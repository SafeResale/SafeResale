from fastapi import APIRouter, Depends
from app.core.security import require_role
from app.core.db import get_db

router = APIRouter(prefix="/admin", tags=["admin"])

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
        items.append({"decision": {**d, "_id": str(d["_id"]), "listing_id": str(d["listing_id"])}, "listing": {**listing, "_id": str(listing["_id"]), "seller_id": str(listing["seller_id"])} if listing else None, "risk": risk})
    return {"items": items, "total": total, "page": page, "page_size": page_size}

@router.get("/listings/{listing_id}")
async def admin_listing(listing_id: str, user=Depends(require_role("admin"))):
    from bson import ObjectId
    db = get_db()
    lid = ObjectId(listing_id)
    listing = await db.listings.find_one({"_id": lid})
    images = await db.listing_images.find({"listing_id": lid}).to_list(20)
    dets = await db.detections.find({"listing_id": lid}).to_list(100)
    cond = await db.condition_predictions.find({"listing_id": lid}).to_list(5)
    diag = await db.diagnostics.find({"listing_id": lid}).to_list(5)
    risks = await db.risk_scores.find({"listing_id": lid}).sort("created_at", -1).to_list(5)
    decisions = await db.decisions.find({"listing_id": lid}).sort("created_at", -1).to_list(5)
    audits = await db.audit_logs.find({"target_id": lid}).sort("created_at", -1).limit(20).to_list(20)
    for c in [listing]:
        if c and "_id" in c: c["_id"] = str(c["_id"])
        if c and "seller_id" in c: c["seller_id"] = str(c["seller_id"])
    return {"listing": listing, "evidence": {"images": images, "detections": dets, "condition": cond, "diagnostics": diag, "risk_history": risks, "decisions": decisions, "audit_trail": audits}}

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
