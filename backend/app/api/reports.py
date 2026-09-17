from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel

from app.core.security import get_current_user
from app.core.rate_limit import check_rate_limit
from app.core.audit import log as audit_log
from app.core.db import get_db

router = APIRouter(prefix="/listings", tags=["reports"])


class ListingReportIn(BaseModel):
    reason: str
    description: str = ""


@router.post("/{listing_id}/report", status_code=201)
async def report_listing(listing_id: str, body: ListingReportIn, request: Request, user=Depends(get_current_user)):
    await check_rate_limit(request, "general", key=user["sub"])
    if not body.reason.strip():
        raise HTTPException(status_code=422, detail={"code": "reason_required", "message": "A reason is required"})
    db = get_db()
    try:
        oid = ObjectId(listing_id)
    except Exception:
        raise HTTPException(status_code=422, detail={"code": "bad_id", "message": "Invalid listing id"})
    target = await db.listings.find_one({"_id": oid})
    if not target:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    import time
    now = time.time()
    doc = {"reporter_id": ObjectId(user["sub"]), "target_type": "listing", "target_id": oid,
           "reason": body.reason, "description": body.description, "status": "pending",
           "created_at": now, "updated_at": now}
    res = await db.reports.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="support.report_listing",
                    target_type="report", target_id=res.inserted_id, detail={"reason": body.reason},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"report": {"id": str(res.inserted_id), "status": "pending"}}

@router.get("/{listing_id}/report")
async def report(listing_id: str):
    return {"badge": "verified", "verified_at": None, "visual_summary": {}, "diagnostic_summary": {}, "seller_trust_tier": "new", "risk_band": "low", "breakdown": {}, "reasons": [], "annotated_images": []}
