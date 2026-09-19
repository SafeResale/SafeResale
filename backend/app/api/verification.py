from fastapi import APIRouter, Depends, Request, HTTPException
from bson import ObjectId
from app.core.security import get_current_user
from app.core.db import get_db
from app.services.vision_provider import get_provider
from app.services import risk as risk_svc
from app.services.decision import decide_with_stops, badge_for
from app.services.anomaly import seller_signals, price_anomaly
from app.core.audit import log as audit_log
import time

router = APIRouter(prefix="/listings", tags=["verification"])

DIAG_STATUSES = ("passed", "failed", "permission_required", "unsupported", "unavailable", "skipped")


def _normalize_diag_tests(tests) -> list:
    """Shape producer tests to the diagnostics data model (05-data-model.md 2.8):
    {id, status, passed, value, unit, simulated, measured_at, meta}.
    The stored `passed` flag is what the risk engine scores on."""
    out = []
    for t in tests or []:
        if not isinstance(t, dict):
            continue
        status = str(t.get("status", "skipped"))
        if status not in DIAG_STATUSES:
            status = "skipped"
        out.append({
            "id": str(t.get("id", "")),
            "status": status,
            "passed": status == "passed",
            "value": t.get("value"),
            "unit": t.get("unit"),
            "simulated": bool(t.get("simulated", False)),
            "measured_at": t.get("measured_at"),
            "meta": t.get("meta"),
        })
    return out


def _oid_str(doc):
    if isinstance(doc, dict):
        return {k: _oid_str(v) for k, v in doc.items()}
    if isinstance(doc, list):
        return [_oid_str(v) for v in doc]
    try:
        from bson import ObjectId
        if isinstance(doc, ObjectId):
            return str(doc)
    except Exception:
        pass
    return doc

@router.post("/{listing_id}/run-quality")
async def run_quality(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    images = await db.listing_images.find({"listing_id": ObjectId(listing_id)}).to_list(100)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_quality", target_type="listing", target_id=ObjectId(listing_id), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"quality_results": images, "simulated": False}

@router.post("/{listing_id}/run-vision")
async def run_vision(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    out = await analyze_images_for_listing(listing_id)
    audit_d = {"detections": len(out["detections"])}
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_vision", target_type="listing", target_id=ObjectId(listing_id), detail=audit_d, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return out


async def analyze_authenticity_for_listing(listing_id: str) -> list:
    """M3 — AI-generated-image detection over the listing's stored photos.
    Provider is swapped via AUTHENTICITY_PROVIDER (stub default, real ViT
    classifier when configured), mirroring the vision provider pattern. The
    real classifier runs in the WSL ML venv when it cannot import in-process."""
    db = get_db()
    oid = ObjectId(listing_id)
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    if not images:
        return []
    import os
    from pathlib import Path
    sim = os.environ.get("AUTHENTICITY_PROVIDER", "stub").lower() != "real"
    if sim:
        try:
            import sys
            sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m3_authenticity"))
            from provider import provider_for  # type: ignore
            provider = provider_for(simulated=True)
            from app.core.storage import storage as _storage
            paths = [str(Path(_storage.base) / img.get("stored_key", "")) for img in images if img.get("stored_key")]
            results = []
            try:
                for r in provider.verify(paths):
                    doc = r.to_dict()
                    doc["image"] = doc.get("image", "").replace("\\", "/").rsplit("/", 1)[-1]
                    results.append(doc)
            finally:
                provider.close()
            await db.authenticity.delete_many({"listing_id": oid})
            for doc in results:
                await db.authenticity.insert_one({"listing_id": oid, "created_at": time.time(), **doc})
            return results
        except Exception:
            return []
    # real provider: in-process when the backend can import transformers,
    # otherwise delegated to the WSL ML venv (models trained there).
    from app.core.storage import storage as _storage
    from app.services import wsl
    items = [{"image": img.get("stored_key", ""), "image_id": str(img["_id"])} for img in images if img.get("stored_key")]
    try:
        import sys
        sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m3_authenticity"))
        from provider import provider_for  # type: ignore
        provider = provider_for(simulated=False)
        paths = [str(Path(_storage.base) / im["image"]) for im in items]
        results = []
        try:
            for r in provider.verify(paths):
                doc = r.to_dict()
                doc["image"] = doc.get("image", "").replace("\\", "/").rsplit("/", 1)[-1]
                results.append(doc)
        finally:
            provider.close()
    except Exception:
        try:
            payload_items = [{"image": wsl.to_mnt((Path(_storage.base) / im["image"]).resolve()), "image_id": im["image_id"]} for im in items]
            rep = wsl.run_bridge("authenticity.py", {"images": payload_items}, timeout=600)
            results = []
            for doc in rep.get("results", []):
                d = {k: v for k, v in doc.items() if k != "orig_image"}
                if "model_version" not in d:
                    d["model_version"] = rep.get("model_version")
                if "simulated" not in d:
                    d["simulated"] = rep.get("simulated")
                results.append(d)
        except Exception:
            return []
    await db.authenticity.delete_many({"listing_id": oid})
    for doc in results:
        await db.authenticity.insert_one({"listing_id": oid, "created_at": time.time(), **doc})
    return results


async def analyze_images_for_listing(listing_id: str) -> dict:
    """Re-run the production image-analysis pipeline for one listing:
    load stored product images, run defect detection + condition classification,
    and persist fresh detections / condition predictions. Used by POST
    run-vision and by the system backfill for existing products.

    Guard: if the provider returns zero actual defect detections (e.g. a stub
    provider), existing detection data is preserved instead of being wiped and
    replaced by nothing — this keeps previously computed risk values intact.
    """
    db = get_db()
    oid = ObjectId(listing_id)
    provider = get_provider()
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    image_inputs = [{"image": img.get("stored_key", ""), "image_id": str(img["_id"])} for img in images]
    detections = provider.detect_defects(image_inputs) if image_inputs else []
    condition = provider.classify_condition(image_inputs) if hasattr(provider, "classify_condition") else {"class": "good", "probabilities": {"good": 0.9}}
    mv = getattr(provider, "model_versions", lambda: {})() or {}
    detector_v = mv.get("detector") or ("stub-1.0" if provider.simulated() else "yolo11n-4")
    flat = [det for d in detections for det in d.get("detections", [])]
    if flat:
        await db.detections.delete_many({"listing_id": oid})
        for d in detections:
            for det in d.get("detections", []):
                await db.detections.insert_one({"listing_id": oid, "image_id": d.get("image"), **det, "model_version": detector_v, "simulated": provider.simulated(), "created_at": time.time()})
    # condition classification is independent of defect detections; persist it
    # whenever the provider returned a prediction (no detection clobbering).
    if condition is not None:
        await db.condition_predictions.delete_many({"listing_id": oid})
        await db.condition_predictions.insert_one({"listing_id": oid, **condition, "created_at": time.time()})
    authenticity = await analyze_authenticity_for_listing(listing_id)
    return {"simulated": provider.simulated(), "detections": detections, "condition": condition, "authenticity": authenticity, "model_versions": {"detector": detector_v, "classifier": mv.get("classifier") or detector_v, "authenticity": "stub-v1" if not authenticity else (authenticity[0].get("model_version") or "n/a")}}

@router.post("/{listing_id}/run-diagnostics")
async def run_diagnostics(listing_id: str, request: Request, user=Depends(get_current_user)):
    body = await request.json() if await request.body() else {}
    db = get_db()
    # use canonical scoring service (device-diagnostics-contract.md + ml/device_diagnostics)
    try:
        import sys
        from pathlib import Path
        sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "device_diagnostics"))
        from services.diagnostics import compute_diagnostics  # type: ignore
        scored = compute_diagnostics(body if isinstance(body, dict) else {})
        score = int(scored.get("score", 0))
        # enrich with scored metadata
        tests = _normalize_diag_tests(body.get("tests", []) if isinstance(body, dict) else [])
        doc = {"listing_id": ObjectId(listing_id), "device": body.get("device",{}), "category": body.get("category","mobile"),
               "tests": tests, "skipped": body.get("skipped", False),
               "score": score, "basis": scored.get("basis"), "missing_penalty": scored.get("missing_penalty"),
               "metrics": scored.get("metrics"), "report_version": scored.get("report_version"),
               "created_at": time.time()}
    except Exception as e:
        # fallback: simple failed count over normalized, testable tests
        tests = _normalize_diag_tests(body.get("tests", []) if isinstance(body, dict) else [])
        testable = [t for t in tests if t["status"] not in ("unsupported", "unavailable")]
        score = min(100, len([t for t in testable if not t["passed"]])*22) if testable else 0
        doc = {"listing_id": ObjectId(listing_id), "device": body.get("device",{}), "tests": tests, "skipped": body.get("skipped", False), "score": score, "created_at": time.time(), "error": str(e)}
    await db.diagnostics.insert_one(doc)
    # Persist the canonical quality score on the listing so the trust badge can
    # read it directly; only the ml-service result (no "error") is a quality
    # score, the fallback path counts failures and is excluded.
    if not doc.get("error") and score is not None:
        await db.listings.update_one({"_id": ObjectId(listing_id)}, {"$set": {"diagnostic_score": score}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_diagnostics", target_type="listing", target_id=ObjectId(listing_id), detail={"score": score, "skipped": doc.get("skipped")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    out = _oid_str(doc)
    return {"diagnostics_report": out, "diagnostic_score": score, "scored": out}

@router.get("/{listing_id}/latest-scores")
async def latest_scores(listing_id: str, user=Depends(get_current_user)):
    """Latest diagnostics + risk + decision + image coverage for a listing (Score screen)."""
    db = get_db()
    try:
        oid = ObjectId(listing_id)
    except Exception:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    listing = await db.listings.find_one({"_id": oid})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    if listing.get("status") not in ("published", "approved") and str(listing.get("seller_id")) != user["sub"] and user.get("role") not in ("admin", "inspector"):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not authorized"})
    diag = await db.diagnostics.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    risk = await db.risk_scores.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    decision = await db.decisions.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    cond = await db.condition_predictions.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    angles = sorted({img.get("angle") for img in images if img.get("angle")})
    from app.services.ml_report import models_report
    out = {
        "listing_id": listing_id,
        "diagnostics": _oid_str(diag),
        "diagnostic_score": (diag or {}).get("score"),
        "risk": _oid_str(risk),
        "decision": _oid_str(decision),
        "condition": _oid_str(cond),
        "images": {"count": len(images), "angles": angles, "required": 8},
        "ml_models": await models_report(listing_id),
    }
    return out

@router.post("/{listing_id}/run-anomaly")
async def run_anomaly(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    listing = await db.listings.find_one({"_id": ObjectId(listing_id)})
    signals = await seller_signals(listing["seller_id"], listing) if listing else []
    pa = await price_anomaly(listing) if listing else {}
    if listing:
        await db.seller_behavior_features.insert_one({"seller_id": listing["seller_id"], "listing_id": ObjectId(listing_id), "features": {}, "top_signals": signals, "created_at": time.time()})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_anomaly", target_type="listing", target_id=ObjectId(listing_id), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"signals": signals, "price_anomaly": pa, "anomaly_score": min(100, sum(s.get("severity",0) for s in signals)//max(1,len(signals)) if signals else 0), "explanation": "; ".join(s["explanation"] for s in signals) if signals else "no signals"}

@router.post("/{listing_id}/compute-risk")
async def compute_risk(listing_id: str, request: Request, user=Depends(get_current_user)):
    payload, _ = await score_listing(listing_id)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.compute_risk", target_type="listing", target_id=ObjectId(listing_id), detail={"risk": payload["adjusted_score"], "decision": payload["decision"]["status"]}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return payload


async def score_listing(listing_id: str):
    """The scoring pipeline (no auth/audit): recompute risk + decision, persist
    them, and write a trust snapshot onto the listing doc so the marketplace
    badge reads the truth from the listing itself. Used by POST compute-risk
    and by the system backfill for pre-existing products."""
    db = get_db()
    try:
        oid = ObjectId(listing_id)
    except Exception:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    dets = await db.detections.find({"listing_id": oid}).to_list(100)
    cond = await db.condition_predictions.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    diag = await db.diagnostics.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    listing = await db.listings.find_one({"_id": oid})
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    # R-RISK-04: confidence reduced when image quality poor
    failed_quality = sum(1 for img in images if not img.get("quality", {}).get("passed", True) or not img.get("server_quality", {}).get("passed", True))
    quality_penalty = failed_quality * 3
    signals = await seller_signals(listing["seller_id"], listing) if listing else []
    # flatten detections
    flat_dets = []
    for doc in dets:
        # doc may be raw detection doc with class/confidence
        if "class" in doc:
            flat_dets.append(doc)
        else:
            flat_dets.extend(doc.get("detections", []))
    physical = risk_svc.physical_risk(flat_dets, cond)
    # apply quality penalty to confidence
    if failed_quality:
        physical["confidence"] = max(0.3, physical["confidence"] - quality_penalty * 0.02)
    diagnostic = risk_svc.diagnostic_risk(diag)
    behavioral = risk_svc.behavioral_risk(signals, await price_anomaly(listing) if listing else None)
    risk = risk_svc.compute_risk(physical, diagnostic, behavioral)
    decision = decide_with_stops(risk, listing or {}, images, flat_dets)
    # badge
    listing_status = listing.get("status", "verifying") if listing else "verifying"
    badge = badge_for(decision["status"], listing_status)
    risk_doc = {**risk, "listing_id": oid, "trigger": "auto", "badge": badge, "created_at": time.time()}
    res = await db.risk_scores.insert_one(risk_doc)
    decision_doc = {**decision, "listing_id": oid, "risk_score_id": res.inserted_id, "badge": badge, "created_at": time.time()}
    await db.decisions.insert_one(decision_doc)
    if listing:
        # Trust snapshot on the listing doc: the marketplace badge derives from it.
        # diagnostic_score is the canonical ml-service quality number when present;
        # the risk fallback path is excluded ("error").
        upsert: dict = {
            "risk": {
                "adjusted_score": risk["adjusted_score"],
                "raw_score": risk.get("raw_score"),
                "badge": badge,
            }
        }
        if diag and "error" not in diag and diag.get("score") is not None:
            upsert["diagnostic_score"] = int(diag["score"])
        # Persist the trust verdict too so the stored product carries it.
        from app.api.listings import _trust_rating
        snapshot = dict(listing)
        snapshot["risk"] = upsert["risk"]
        if "diagnostic_score" in upsert:
            snapshot["diagnostic_score"] = upsert["diagnostic_score"]
        upsert["trust"] = _trust_rating(snapshot)
        await db.listings.update_one({"_id": oid}, {"$set": upsert})
    return {**risk, "decision": decision, "badge": badge}, listing

@router.post("/{listing_id}/decision")
async def decision(listing_id: str, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.decisions.find_one({"listing_id": ObjectId(listing_id)}, sort=[("created_at", -1)])
    return {"decision": doc or {"status": "review"}}

@router.post("/{listing_id}/rescore")
async def rescore(listing_id: str, request: Request, user=Depends(get_current_user)):
    # admin/inspector only
    if user.get("role") not in ("admin","inspector"):
        raise HTTPException(status_code=403, detail={"code":"forbidden","message":"Only admin/inspector can rescore"})
    return await compute_risk(listing_id, request, user)

# Inspection workflow (R-ADMIN-05)
@router.post("/{listing_id}/inspection")
async def submit_inspection(listing_id: str, request: Request, user=Depends(get_current_user)):
    if user.get("role") not in ("inspector","admin"):
        raise HTTPException(status_code=403, detail={"code":"forbidden","message":"Inspector only"})
    body = await request.json()
    db = get_db()
    doc = {"listing_id": ObjectId(listing_id), "inspector_id": ObjectId(user["sub"]), "result": body.get("result","pass"), "evidence_keys": body.get("evidence_keys", []), "note": body.get("note",""), "created_at": time.time()}
    res = await db.inspection_reports.insert_one(doc)
    # trigger rescore
    await compute_risk(listing_id, request, user)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="inspection.submit", target_type="listing", target_id=ObjectId(listing_id), detail={"result": doc["result"]}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"inspection": {**doc, "_id": str(res.inserted_id)}}
