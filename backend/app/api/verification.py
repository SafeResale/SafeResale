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

@router.post("/{listing_id}/run-quality")
async def run_quality(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    images = await db.listing_images.find({"listing_id": ObjectId(listing_id)}).to_list(100)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_quality", target_type="listing", target_id=ObjectId(listing_id), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"quality_results": images, "simulated": False}

@router.post("/{listing_id}/run-vision")
async def run_vision(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    provider = get_provider()
    images = await db.listing_images.find({"listing_id": ObjectId(listing_id)}).to_list(100)
    image_inputs = [{"image": img.get("stored_key",""), "image_id": str(img["_id"])} for img in images]
    detections = provider.detect_defects(image_inputs) if image_inputs else []
    condition = provider.classify_condition(image_inputs) if hasattr(provider, "classify_condition") else {"class": "good", "probabilities": {"good": 0.9}}
    await db.detections.delete_many({"listing_id": ObjectId(listing_id)})
    for d in detections:
        for det in d.get("detections", []):
            await db.detections.insert_one({"listing_id": ObjectId(listing_id), "image_id": d.get("image"), **det, "model_version": "stub-1.0" if provider.simulated() else "yolo11n-4", "simulated": provider.simulated(), "created_at": time.time()})
    await db.condition_predictions.delete_many({"listing_id": ObjectId(listing_id)})
    await db.condition_predictions.insert_one({"listing_id": ObjectId(listing_id), **condition, "created_at": time.time()})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_vision", target_type="listing", target_id=ObjectId(listing_id), detail={"detections": len(detections)}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"simulated": provider.simulated(), "detections": detections, "condition": condition, "model_versions": {"detector": "stub-1.0" if provider.simulated() else "yolo11n-4", "classifier": "stub-1.0"}}

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
        doc = {"listing_id": ObjectId(listing_id), "device": body.get("device",{}), "category": body.get("category","mobile"),
               "tests": body.get("tests", []), "skipped": body.get("skipped", False),
               "score": score, "basis": scored.get("basis"), "missing_penalty": scored.get("missing_penalty"),
               "metrics": scored.get("metrics"), "report_version": scored.get("report_version"),
               "created_at": time.time()}
    except Exception as e:
        # fallback: simple failed count
        tests = body.get("tests", []) if isinstance(body, dict) else []
        score = min(100, len([t for t in tests if not t.get("passed")])*22) if tests else 0
        doc = {"listing_id": ObjectId(listing_id), "device": body.get("device",{}), "tests": body.get("tests", []), "skipped": body.get("skipped", False), "score": score, "created_at": time.time(), "error": str(e)}
    await db.diagnostics.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.run_diagnostics", target_type="listing", target_id=ObjectId(listing_id), detail={"score": score, "skipped": doc.get("skipped")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"diagnostics_report": doc, "diagnostic_score": score, "scored": doc}

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
    db = get_db()
    dets = await db.detections.find({"listing_id": ObjectId(listing_id)}).to_list(100)
    cond = await db.condition_predictions.find_one({"listing_id": ObjectId(listing_id)}, sort=[("created_at", -1)])
    diag = await db.diagnostics.find_one({"listing_id": ObjectId(listing_id)}, sort=[("created_at", -1)])
    listing = await db.listings.find_one({"_id": ObjectId(listing_id)})
    images = await db.listing_images.find({"listing_id": ObjectId(listing_id)}).to_list(100)
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
    risk_doc = {**risk, "listing_id": ObjectId(listing_id), "trigger": "auto", "badge": badge, "created_at": time.time()}
    res = await db.risk_scores.insert_one(risk_doc)
    decision_doc = {**decision, "listing_id": ObjectId(listing_id), "risk_score_id": res.inserted_id, "badge": badge, "created_at": time.time()}
    await db.decisions.insert_one(decision_doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="verification.compute_risk", target_type="listing", target_id=ObjectId(listing_id), detail={"risk": risk["adjusted_score"], "decision": decision["status"]}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {**risk, "decision": decision, "badge": badge}

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
