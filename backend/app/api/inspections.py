"""Physical inspection workflow.

A buyer requests an inspection on a published listing. An admin assigns an
inspector, who completes the on-site / drop-off inspection. Statuses:
requested -> assigned -> in_progress -> completed (or cancelled/rejected).
"""
import time
from fastapi import APIRouter, HTTPException, Depends, Request
from pydantic import BaseModel
from bson import ObjectId

from app.core.db import get_db
from app.core.security import get_current_user
from app.core.audit import log as audit_log

router = APIRouter(prefix="/inspections", tags=["inspections"])

VALID_STATUSES = {"requested", "assigned", "in_progress", "completed", "cancelled", "rejected"}
ADMIN_ACTIONABLE = {"assigned", "in_progress", "completed", "cancelled", "rejected"}


class InspectionIn(BaseModel):
    listing_id: str
    preferred_date: str | None = None
    note: str | None = None


class InspectionPatch(BaseModel):
    status: str | None = None
    inspector_id: str | None = None
    inspectors: str | None = None
    note: str | None = None
    inspection_date: str | None = None
    findings: dict | None = None


@router.post("", status_code=201)
async def create_inspection(body: InspectionIn, request: Request, user=Depends(get_current_user)):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "general", key=user["sub"])
    db = get_db()
    listing = await db.listings.find_one({"_id": ObjectId(body.listing_id)})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    if listing.get("status") not in ("published", "approved"):
        raise HTTPException(status_code=409, detail={"code": "not_available", "message": "Listing is not available for inspection"})
    if str(listing.get("seller_id")) == user["sub"]:
        raise HTTPException(status_code=400, detail={"code": "own_listing", "message": "You cannot inspect your own listing"})
    active = await db.inspections.find_one({
        "listing_id": ObjectId(body.listing_id),
        "buyer_id": ObjectId(user["sub"]),
        "status": {"$in": ["requested", "assigned", "in_progress"]},
    })
    if active:
        raise HTTPException(status_code=409, detail={"code": "already_requested", "message": "An active inspection request already exists for this listing"})
    ordering = await db.inspections.count_documents({"listing_id": ObjectId(body.listing_id)}) + 1
    doc = {
        "listing_id": ObjectId(body.listing_id),
        "buyer_id": ObjectId(user["sub"]),
        "seller_id": listing.get("seller_id"),
        "status": "requested",
        "preferred_date": body.preferred_date,
        "note": body.note,
        "order": ordering,
        "created_at": time.time(),
        "updated_at": time.time(),
    }
    res = await db.inspections.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="inspection.request",
                    target_type="inspection", target_id=res.inserted_id,
                    detail={"listing_id": body.listing_id}, ip=request.client.host if request.client else None,
                    request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = str(res.inserted_id)
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"])
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    return {"inspection": doc}


@router.get("")
async def list_inspections(user=Depends(get_current_user)):
    db = get_db()
    as_buyer = await db.inspections.find({"buyer_id": ObjectId(user["sub"])}).sort("created_at", -1).to_list(100)
    for it in as_buyer:
        it["role"] = "buyer"
    if user.get("role") in ("admin", "inspector"):
        as_inspector = await db.inspections.find({"status": {"$in": ["requested", "assigned", "in_progress"]}}).sort("created_at", -1).to_list(100)
    else:
        as_seller = await db.inspections.find({"seller_id": ObjectId(user["sub"])}).sort("created_at", -1).to_list(100)
        as_inspector = []
        for it in as_seller:
            it["role"] = "seller"
    for it in as_inspector:
        it["role"] = it.get("role", "inspector")
    items = as_buyer + as_inspector
    for it in items:
        it["_id"] = str(it["_id"])
        it["listing_id"] = str(it["listing_id"])
        it["buyer_id"] = str(it["buyer_id"])
        it["seller_id"] = str(it["seller_id"]) if it.get("seller_id") else None
    return {"items": items, "total": len(items)}


@router.get("/{inspection_id}")
async def get_inspection(inspection_id: str, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})
    ok = str(doc.get("buyer_id")) == user["sub"] or str(doc.get("seller_id")) == user["sub"] or user.get("role") in ("admin", "inspector")
    if not ok:
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not authorized"})
    doc["_id"] = str(doc["_id"])
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"])
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    if doc.get("inspector_id"):
        doc["inspector_id"] = str(doc["inspector_id"])
    return {"inspection": doc}


@router.patch("/{inspection_id}")
async def patch_inspection(inspection_id: str, body: InspectionPatch, request: Request, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})
    if user.get("role") not in ("admin", "inspector"):
        # buyers/sellers may only cancel their own pending request
        owner = str(doc.get("buyer_id")) == user["sub"]
        if not owner or body.status not in ("cancelled",):
            raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Only admins or inspectors can update inspections"})
    patch = {}
    if body.status is not None:
        if body.status not in VALID_STATUSES:
            raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"Status must be one of {sorted(VALID_STATUSES)}"})
        if body.status in ADMIN_ACTIONABLE and user.get("role") not in ("admin", "inspector"):
            raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Only admins or inspectors can set this status"})
        patch["status"] = body.status
    if body.inspector_id:
        inspector = await db.users.find_one({"_id": ObjectId(body.inspector_id)})
        if not inspector:
            raise HTTPException(status_code=400, detail={"code": "no_inspector", "message": "Inspector user not found"})
        patch["inspector_id"] = ObjectId(body.inspector_id)
        patch["status"] = doc.get("status", "requested") if doc.get("status") != "requested" else "assigned"
    if body.inspectors:
        patch["inspector_name"] = body.inspectors
    if body.note is not None:
        patch["note"] = body.note
    if body.inspection_date:
        patch["inspection_date"] = body.inspection_date
    if body.findings is not None:
        patch["findings"] = body.findings

    # Update the user's role to inspector when an admin assigns them
    if patch.get("inspector_id"):
        await db.users.update_one(
            {"_id": ObjectId(patch["inspector_id"]), "role": {"$ne": "inspector"}},
            {"$set": {"role": "inspector", "updated_at": time.time()}},
        )

    if not patch:
        raise HTTPException(status_code=400, detail={"code": "no_fields", "message": "No updatable fields"})
    await db.inspections.update_one({"_id": doc["_id"]}, {"$set": {**patch, "updated_at": time.time()}})
    doc.update(patch)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="inspection.update",
                    target_type="inspection", target_id=doc["_id"], detail=patch,
                    ip=request.client.host if request.client else None,
                    request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = str(doc["_id"])
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"])
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    if doc.get("inspector_id"):
        doc["inspector_id"] = str(doc["inspector_id"])
    return {"inspection": doc}