import re
from fastapi import APIRouter, Depends
from app.core.security import require_role
from app.core.db import get_db
from app.core.serialize import s as _s

router = APIRouter(prefix="/admin", tags=["admin"])

LISTING_STATUSES = ("draft", "capturing", "submitted", "verifying", "approved", "review", "blocked", "published", "restricted", "inspection_pending")


def _risk_band(score):
    try:
        score = float(score)
    except (TypeError, ValueError):
        return None
    return "low" if score <= 30 else ("medium" if score <= 60 else "high")


async def _latest_risk(db, listing_id):
    return await db.risk_scores.find_one({"listing_id": listing_id}, sort=[("created_at", -1)])


async def _latest_decision(db, listing_id):
    return await db.decisions.find_one({"listing_id": listing_id}, sort=[("created_at", -1)])


async def _enrich(db, listings, *, with_seller=True):
    """Attach latest risk/decision, image count and seller info to listing docs."""
    ids = [l["_id"] for l in listings]
    if not ids:
        return []
    image_counts = {}
    async for g in db.listing_images.aggregate([
        {"$match": {"listing_id": {"$in": ids}}},
        {"$group": {"_id": "$listing_id", "n": {"$sum": 1}}},
    ]):
        image_counts[g["_id"]] = g["n"]

    sellers = {}
    if with_seller:
        seller_ids = {l.get("seller_id") for l in listings if l.get("seller_id")}
        async for u in db.users.find({"_id": {"$in": list(seller_ids)}}, {"name": 1, "email": 1, "status": 1, "verified": 1}):
            sellers[u["_id"]] = u

    out = []
    for l in listings:
        risk = await _latest_risk(db, l["_id"])
        decision = await _latest_decision(db, l["_id"])
        sl = {}
        if with_seller and l.get("seller_id"):
            u = sellers.get(l["seller_id"])
            sl = {"id": str(l["seller_id"]), "name": u.get("name") if u else None,
                  "email": u.get("email") if u else None, "status": u.get("status") if u else None} if u else {"id": str(l["seller_id"])}
        out.append({
            "listing": _s(l),
            "risk": _s(risk) if risk else None,
            "risk_band": _risk_band((risk or {}).get("adjusted_score")),
            "decision": _s(decision) if decision else None,
            "image_count": image_counts.get(l["_id"], 0),
            "seller": sl,
        })
    return out


@router.get("/listings")
async def admin_listings(
    status: str | None = None,
    category: str | None = None,
    risk: str | None = None,
    q: str | None = None,
    seller: str | None = None,
    page: int = 1,
    page_size: int = 20,
    sort_by: str = "created_at",
    sort_dir: str = "desc",
    user=Depends(require_role("admin")),
):
    """Full catalog management list: filters + search + pagination + sort.

    Each item enriches the raw listing with its latest risk score, band,
    latest decision, image count and seller brief.
    """
    from bson import ObjectId
    if status and status not in LISTING_STATUSES:
        from fastapi import HTTPException
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"Unknown status '{status}'"})
    if risk and risk not in ("low", "medium", "high"):
        from fastapi import HTTPException
        raise HTTPException(status_code=422, detail={"code": "bad_risk", "message": "risk must be low|medium|high"})
    db = get_db()
    q_doc: dict = {}
    if status:
        q_doc["status"] = status
    else:
        # drafts are seller-private; admin catalog should not surface them unless explicitly filtered
        q_doc["status"] = {"$ne": "draft"}
    if category:
        q_doc["category"] = category
    if seller:
        try:
            q_doc["seller_id"] = ObjectId(seller)
        except Exception:
            from fastapi import HTTPException
            raise HTTPException(status_code=422, detail={"code": "bad_seller", "message": "Invalid seller id"})
    if q:
        q_doc["$or"] = [{"title": {"$regex": re.escape(q), "$options": "i"}}, {"description": {"$regex": re.escape(q), "$options": "i"}}]
    sort_key = sort_by if sort_by in ("created_at", "updated_at", "price", "status", "title") else "created_at"
    sort_dir_i = 1 if sort_dir == "asc" else -1

    total = await db.listings.count_documents(q_doc)
    listings = await db.listings.find(q_doc).sort(sort_key, sort_dir_i).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    base = await _enrich(db, listings)

    if risk:
        # risk band is derived from the latest risk_score (separate collection), so
        # band filtering happens here rather than in the Mongo query. Total is
        # recomputed over the banded rows (capped to a sane sweep for tooling).
        cap = 10_000
        banded: list = []
        async for l in db.listings.find(q_doc).limit(cap):
            r = await _latest_risk(db, l["_id"])
            b = _risk_band((r or {}).get("adjusted_score"))
            if b == risk:
                banded.append({"listing": l, "risk": r, "risk_band": b})
        banded.sort(key=lambda x: x["listing"].get(sort_key) or 0, reverse=(sort_dir_i == -1))
        total = len(banded)
        page_items = banded[(page - 1) * page_size: page * page_size]
        # enrich the page banded rows (images/seller) and shape like base
        shaped = await _enrich(db, [x["listing"] for x in page_items])
        for it, sh in zip(page_items, shaped):
            sh["risk"] = _s(it["risk"]) if it.get("risk") else sh.get("risk")
            if it.get("risk_band"):
                sh["risk_band"] = it["risk_band"]
            elif not sh.get("risk_band"):
                sh["risk_band"] = risk
        return {"items": shaped, "total": total, "page": page, "page_size": page_size, "statuses": list(LISTING_STATUSES), "risk_filter_capped": total >= cap}

    return {"items": base, "total": total, "page": page, "page_size": page_size, "statuses": list(LISTING_STATUSES)}


@router.get("/dashboard")
async def dashboard(user=Depends(require_role("admin"))):
    """Rich admin dashboard payload: KPIs, status/risk distribution, trend, escrow, recent activity."""
    import time
    db = get_db()
    now = time.time()
    day = 86400

    total = await db.listings.count_documents({})
    status_counts = {}
    async for g in db.listings.aggregate([{"$group": {"_id": "$status", "n": {"$sum": 1}}}]):
        status_counts[g["_id"]] = g["n"] if g["_id"] else 0
    pending = sum(status_counts.get(s, 0) for s in ("review", "verifying", "submitted"))
    high_risk = await db.decisions.count_documents({"status": "blocked"})
    approved = await db.decisions.count_documents({"status": "approved"})
    total_dec = await db.decisions.count_documents({})
    approval_rate = round(approved / total_dec * 100, 1) if total_dec else 0

    # avg risk over latest risk score per listing
    risk_vals = []
    async for r in db.risk_scores.aggregate([
        {"$sort": {"created_at": -1}},
        {"$group": {"_id": "$listing_id", "score": {"$first": "$adjusted_score"}, "badge": {"$first": "$badge"}}},
        {"$limit": 500},
    ]):
        if r.get("score") is not None:
            risk_vals.append(float(r["score"]))
    avg_risk = round(sum(risk_vals) / len(risk_vals), 1) if risk_vals else 0
    risk_bands = {"low": 0, "medium": 0, "high": 0}
    for v in risk_vals:
        risk_bands[_risk_band(v) or "low"] += 1

    # trend: listings created per day, last 14 days
    trend = []
    for i in range(13, -1, -1):
        start = now - (i + 1) * day
        end = now - i * day
        n = await db.listings.count_documents({"created_at": {"$gte": start, "$lt": end}})
        trend.append({"date": int(end), "count": n})

    # users
    user_counts = {"total": 0, "sellers": 0, "buyers": 0, "admins": 0, "inspectors": 0, "suspended": 0}
    user_counts["total"] = await db.users.count_documents({})
    user_counts["suspended"] = await db.users.count_documents({"status": "suspended"})
    async for g in db.users.aggregate([{"$group": {"_id": "$role", "n": {"$sum": 1}}}]):
        role = g["_id"] or "buyer"
        if role == "seller":
            user_counts["sellers"] = g["n"]
        elif role == "buyer":
            user_counts["buyers"] = g["n"]
        elif role == "admin":
            user_counts["admins"] = g["n"]
        elif role == "inspector":
            user_counts["inspectors"] = g["n"]

    # escrow
    escrow_held = escrow_review = 0.0
    escrow_counts = {}
    async for e in db.escrows.find({}):
        st = e.get("status")
        escrow_counts[st] = escrow_counts.get(st, 0) + 1
        if st == "held":
            escrow_held += e.get("amount", 0)
        elif st == "in_review":
            escrow_review += e.get("amount", 0)

    reports_pending = await db.reports.count_documents({"status": "pending"}) if "reports" in await db.list_collection_names() else 0
    messages_new = await db.contact_messages.count_documents({"status": "new"}) if "contact_messages" in await db.list_collection_names() else 0

    new_today = await db.listings.count_documents({"created_at": {"$gte": now - day}})
    new_7d = await db.listings.count_documents({"created_at": {"$gte": now - 7 * day}})

    # recent activity (audit)
    recent_activity = []
    async for a in db.audit_logs.find().sort("created_at", -1).limit(10):
        recent_activity.append(_s(a))

    # recent flagged — deduplicate by listing and only include listings whose latest decision is still review/blocked
    recent_flagged = []
    decs = await db.decisions.find({"status": {"$in": ["review", "blocked"]}}).sort("created_at", -1).limit(20).to_list(20)
    if decs:
        seen = set()
        uniq_listing_ids = []
        for d in decs:
            lid = d["listing_id"]
            if lid in seen:
                continue
            seen.add(lid)
            # only include if latest decision for this listing is still flagged
            latest = await _latest_decision(db, lid)
            if latest and latest.get("status") in ("review", "blocked"):
                uniq_listing_ids.append(lid)
            if len(uniq_listing_ids) >= 5:
                break
        if uniq_listing_ids:
            listings_for_flagged = []
            for lid in uniq_listing_ids:
                doc = await db.listings.find_one({"_id": lid})
                listings_for_flagged.append(doc or {"_id": lid})
            recent_flagged = await _enrich(db, listings_for_flagged, with_seller=True)

    return {
        "kpis": {
            "total_listings": total, "pending_review": pending, "high_risk": high_risk,
            "approval_rate": approval_rate, "avg_risk": avg_risk, "model_confidence": 0.82,
            "new_today": new_today, "new_7d": new_7d,
            "users": user_counts, "escrow_held": round(escrow_held, 2),
            "escrow_in_review": round(escrow_review, 2), "escrow_counts": escrow_counts,
            "reports_pending": reports_pending, "messages_new": messages_new,
        },
        "status_distribution": status_counts,
        "risk_distribution": risk_bands,
        "risk_samples": len(risk_vals),
        "trend": trend,
        "recent_flagged": recent_flagged,
        "recent_activity": recent_activity,
        "generated_at": now,
    }

@router.get("/kpis")
async def kpis(user=Depends(require_role("admin"))):
    db = get_db()
    total = await db.listings.count_documents({})
    pending = await db.listings.count_documents({"status": {"$in": ["review","verifying","submitted"]}})
    high = await db.decisions.count_documents({"status": "blocked"})
    approved = await db.decisions.count_documents({"status": "approved"})
    total_dec = await db.decisions.count_documents({})
    avg_risk = 0
    async for doc in db.risk_scores.find().sort("created_at", -1).limit(20):
        avg_risk += doc.get("adjusted_score", 0)
    if total_dec:
        avg_risk = round(avg_risk / min(20, total_dec), 1)
    rate = round(approved / total_dec * 100, 1) if total_dec else 0
    return {"total_listings": total, "pending_review": pending, "high_risk": high, "approval_rate": rate, "avg_risk": avg_risk, "model_confidence": 0.82, "diag_failure_rate": 0.15}

@router.get("/listings/flagged")
async def flagged(status: str | None = None, page: int = 1, page_size: int = 20, user=Depends(require_role("admin"))):
    db = get_db()
    # flagged = decisions with review|blocked, most recent first
    q: dict = {}
    if status:
        q["status"] = status
    else:
        q["status"] = {"$in": ["review", "blocked"]}
    total = await db.decisions.count_documents(q)
    decs = await db.decisions.find(q).sort("created_at", -1).skip((page-1)*page_size).limit(page_size).to_list(page_size)
    items = []
    for d in decs:
        listing = await db.listings.find_one({"_id": d["listing_id"]})
        risk = await db.risk_scores.find_one({"_id": d.get("risk_score_id")}) or await db.risk_scores.find_one({"listing_id": d["listing_id"]}, sort=[("created_at", -1)])
        if risk:
            risk["_id"] = str(risk["_id"])
            if "listing_id" in risk: risk["listing_id"] = str(risk["listing_id"])
        if d.get("risk_score_id"): d["risk_score_id"] = str(d["risk_score_id"])
        items.append({"decision": {**d, "_id": str(d["_id"]), "listing_id": str(d["listing_id"])}, "listing": {**listing, "_id": str(listing["_id"]), "seller_id": str(listing["seller_id"])} if listing else None, "risk": risk})
    return {"items": items, "total": total, "page": page, "page_size": page_size}

@router.get("/listings/{listing_id}")
async def admin_listing(listing_id: str, user=Depends(require_role("admin"))):
    from bson import ObjectId
    db = get_db()
    lid = ObjectId(listing_id)
    listing = await db.listings.find_one({"_id": lid})
    seller = None
    if listing and listing.get("seller_id"):
        seller_id = listing["seller_id"]
        u = await db.users.find_one({"_id": seller_id}, {"name": 1, "email": 1, "status": 1, "verified": 1})
        if u:
            seller = {"id": str(u["_id"]), "name": u.get("name"), "email": u.get("email"),
                      "status": u.get("status"), "verified": bool(u.get("verified", False))}
        else:
            seller = {"id": str(seller_id)}
    images = await db.listing_images.find({"listing_id": lid}).to_list(20)
    dets = await db.detections.find({"listing_id": lid}).to_list(100)
    cond = await db.condition_predictions.find({"listing_id": lid}).to_list(5)
    diag = await db.diagnostics.find({"listing_id": lid}).to_list(5)
    risks = await db.risk_scores.find({"listing_id": lid}).sort("created_at", -1).to_list(5)
    decisions = await db.decisions.find({"listing_id": lid}).sort("created_at", -1).to_list(5)
    audits = await db.audit_logs.find({"target_id": lid}).sort("created_at", -1).limit(20).to_list(20)
    return {"listing": _s(listing), "seller": seller, "evidence": {
        "images": _s(images), "detections": _s(dets), "condition": _s(cond),
        "diagnostics": _s(diag), "risk_history": _s(risks),
        "decisions": _s(decisions), "audit_trail": _s(audits),
    }}

@router.post("/listings/{listing_id}/review")
async def review(listing_id: str, body: dict, request=None, user=Depends(require_role("admin"))):
    from bson import ObjectId
    import time
    db = get_db()
    lid = ObjectId(listing_id)
    listing = await db.listings.find_one({"_id": lid})
    if not listing:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"Listing not found"})
    action = body.get("action", "review")
    reason = body.get("reason", "")
    if not reason:
        from fastapi import HTTPException
        raise HTTPException(status_code=400, detail={"code":"reason_required","message":"Reason is required for audit"})
    prev = listing.get("status")
    mapping = {"approve": "approved", "warn": "review", "block": "blocked", "request_inspection": "inspection_pending", "suspend_seller": "restricted"}
    new_status = mapping.get(action, "review")
    await db.listings.update_one({"_id": lid}, {"$set": {"status": new_status, "updated_at": time.time()}})
    await db.admin_reviews.insert_one({"listing_id": lid, "admin_id": ObjectId(user["sub"]), "action": action, "reason": reason, "note": body.get("note"), "prev_status": prev, "new_status": new_status, "created_at": time.time()})
    await db.audit_logs.insert_one({"actor_id": ObjectId(user["sub"]), "actor_role": "admin", "action": f"admin.{action}", "target_type": "listing", "target_id": lid, "detail": {"reason": reason, "prev_status": prev, "new_status": new_status}, "created_at": time.time()})
    # suspend seller side-effect
    if action == "suspend_seller":
        await db.users.update_one({"_id": listing["seller_id"]}, {"$set": {"status": "suspended"}})
    return {"listing": {"id": listing_id, "status": new_status}, "review": body}

@router.get("/audit-logs")
async def audit_logs(page: int = 1, page_size: int = 50, user=Depends(require_role("admin"))):
    db = get_db()
    total = await db.audit_logs.count_documents({})
    items = await db.audit_logs.find().sort("created_at", -1).skip((page-1)*page_size).limit(page_size).to_list(page_size)
    for it in items:
        it["_id"] = str(it["_id"])
        if it.get("actor_id"): it["actor_id"] = str(it["actor_id"])
        if it.get("target_id"): it["target_id"] = str(it["target_id"])
    return {"items": items, "total": total, "page": page}

@router.get("/audit-logs/export")
async def audit_export(user=Depends(require_role("admin"))):
    db = get_db()
    import csv, io
    from fastapi.responses import StreamingResponse
    items = await db.audit_logs.find().sort("created_at", -1).limit(1000).to_list(1000)
    output = io.StringIO()
    w = csv.writer(output)
    w.writerow(["created_at","actor_id","actor_role","action","target_type","target_id","detail"])
    for it in items:
        w.writerow([it.get("created_at"), it.get("actor_id"), it.get("actor_role"), it.get("action"), it.get("target_type"), it.get("target_id"), it.get("detail")])
    output.seek(0)
    return StreamingResponse(iter([output.getvalue()]), media_type="text/csv", headers={"Content-Disposition": "attachment; filename=audit.csv"})

@router.get("/models")
async def models(user=Depends(require_role("admin"))):
    db = get_db()
    items = await db.model_metrics.find().sort("created_at", -1).to_list(20)
    for it in items:
        it["_id"] = str(it["_id"])
    return {"models": items}
