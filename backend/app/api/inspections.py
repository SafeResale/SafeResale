"""Physical inspection workflow.

Flow:
1. Buyer requests an inspection on a listing.
2. Notification sent via FCM & database feed to Seller ("Buyer requested inspection") and to Available Inspectors.
3. Inspector logs into the Inspector Web App, views the queue, and accepts the request (status -> "assigned").
   -> Buyer notified: "Inspector has accepted your inspection request!"
4. Inspector updates status: "travelling" (heading to inspection location).
   -> Buyer notified: "Inspector is on the way to inspect the device!"
5. Inspector updates status: "arrived" or "in_progress" (testing the physical device).
   -> Buyer notified: "Inspector has arrived and is conducting physical hardware testing."
6. Inspector fills out physical findings (battery, display, body, camera, functionality, overall result).
   -> AI Score Engine calculates the inspection score & verdict.
   -> Status -> "completed".
   -> Buyer & Seller notified: "Inspection completed! Trust score updated."
"""
import time
from fastapi import APIRouter, HTTPException, Depends, Request
from pydantic import BaseModel
from bson import ObjectId

from app.core.db import get_db
from app.core.security import get_current_user
from app.core.audit import log as audit_log
from app.core.fcm import send_notification

router = APIRouter(prefix="/inspections", tags=["inspections"])

VALID_STATUSES = {
    "requested",
    "assigned",
    "travelling",
    "arrived",
    "in_progress",
    "completed",
    "cancelled",
    "rejected"
}

ADMIN_OR_INSPECTOR_STATUSES = {
    "assigned",
    "travelling",
    "arrived",
    "in_progress",
    "completed",
    "rejected"
}


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


class InspectionSubmitFindings(BaseModel):
    overall_condition: str  # "excellent" | "good" | "fair" | "poor"
    battery_condition: str | None = "good"
    screen_condition: str | None = "good"
    body_condition: str | None = "good"
    camera_condition: str | None = "good"
    connectivity_ok: bool = True
    biometrics_ok: bool = True
    notes: str | None = None
    defects_found: list[str] = []
    photos: list[str] = []
    calculated_score: float | None = None


def _stringify_ids(payload):
    """Recursively replace BSON ObjectId values with strings.

    Inspection docs are returned as raw dicts (not passed through the shared
    serializer), so any ObjectId left on a document - e.g. an admin id added by
    a newer code path - would otherwise blow up JSON encoding with
    "'ObjectId' object is not iterable".
    """
    if isinstance(payload, list):
        return [_stringify_ids(p) for p in payload]
    if isinstance(payload, dict):
        return {
            k: (str(v) if isinstance(v, ObjectId) else _stringify_ids(v))
            for k, v in payload.items()
        }
    return payload


def compute_inspector_score(findings: dict) -> float:
    """AI Scoring helper that translates inspector physical findings into a 0-100 quality score."""
    base_scores = {
        "excellent": 95.0,
        "good": 82.0,
        "fair": 60.0,
        "poor": 35.0,
    }
    score = base_scores.get(str(findings.get("overall_condition", "good")).lower(), 80.0)

    # Component deductions
    deductions = 0.0
    for comp in ["battery_condition", "screen_condition", "body_condition", "camera_condition"]:
        val = str(findings.get(comp, "good")).lower()
        if val == "poor":
            deductions += 10.0
        elif val == "fair":
            deductions += 5.0

    if not findings.get("connectivity_ok", True):
        deductions += 12.0
    if not findings.get("biometrics_ok", True):
        deductions += 8.0

    defects = findings.get("defects_found") or []
    deductions += min(25.0, len(defects) * 6.0)

    final_score = max(10.0, min(100.0, score - deductions))
    return round(final_score, 1)


@router.post("", status_code=201)
async def create_inspection(body: InspectionIn, request: Request, user=Depends(get_current_user)):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "general", key=user["sub"])
    db = get_db()
    listing = await db.listings.find_one({"_id": ObjectId(body.listing_id)})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    if listing.get("status") not in ("published", "approved", "inspection_pending"):
        raise HTTPException(status_code=409, detail={"code": "not_available", "message": "Listing is not available for inspection"})
    if str(listing.get("seller_id")) == user["sub"]:
        raise HTTPException(status_code=400, detail={"code": "own_listing", "message": "You cannot inspect your own listing"})

    active = await db.inspections.find_one({
        "listing_id": ObjectId(body.listing_id),
        "buyer_id": ObjectId(user["sub"]),
        "status": {"$in": ["requested", "assigned", "travelling", "arrived", "in_progress"]},
    })
    if active:
        raise HTTPException(status_code=409, detail={"code": "already_requested", "message": "An active inspection request already exists for this listing"})

    # An inspection raised by an admin has no buyer_id, so it is invisible to
    # buyers in `GET /inspections` and looks un-bookable in the app. When a buyer
    # books on such a listing, join the existing task instead of creating a
    # second one (which the guard above would reject as a duplicate).
    admin_task = await db.inspections.find_one({
        "listing_id": ObjectId(body.listing_id),
        "buyer_id": None,
        "status": {"$in": ["requested", "assigned", "travelling", "arrived", "in_progress"]},
    })
    if admin_task:
        now_j = time.time()
        set_j = {"buyer_id": ObjectId(user["sub"]), "updated_at": now_j}
        if body.preferred_date:
            set_j["preferred_date"] = body.preferred_date
        if body.note:
            set_j["note"] = body.note
        await db.inspections.update_one(
            {"_id": admin_task["_id"]},
            {"$set": set_j, "$push": {"timeline": {
                "status": admin_task.get("status", "requested"),
                "label": "Inspection Joined by Buyer",
                "description": "Buyer joined the inspection requested by the admin.",
                "timestamp": now_j,
            }}},
        )
        admin_task.update(set_j)
        admin_task["_id"] = str(admin_task["_id"])
        admin_task["listing_id"] = body.listing_id
        admin_task["buyer_id"] = str(admin_task["buyer_id"])
        admin_task["seller_id"] = str(admin_task["seller_id"]) if admin_task.get("seller_id") else None
        if admin_task.get("inspector_id"):
            admin_task["inspector_id"] = str(admin_task["inspector_id"])
        admin_task["requested_by_admin_id"] = str(admin_task.get("requested_by_admin_id"))
        return {"inspection": _stringify_ids(admin_task), "joined_existing": True}

    ordering = await db.inspections.count_documents({"listing_id": ObjectId(body.listing_id)}) + 1
    now = time.time()
    doc = {
        "listing_id": ObjectId(body.listing_id),
        "buyer_id": ObjectId(user["sub"]),
        "seller_id": listing.get("seller_id"),
        "status": "requested",
        "preferred_date": body.preferred_date,
        "note": body.note,
        "order": ordering,
        "created_at": now,
        "updated_at": now,
        "timeline": [
            {
                "status": "requested",
                "label": "Inspection Requested",
                "description": "Buyer requested on-site inspection for second-hand device.",
                "timestamp": now,
            }
        ]
    }
    res = await db.inspections.insert_one(doc)

    # ── FCM Push Notification to Seller & Notification Feed ───────────────────
    if listing.get("seller_id"):
        seller_id = str(listing["seller_id"])
        await send_notification(
            user_id=seller_id,
            title="Inspection Requested",
            body=f"A buyer requested physical inspection for your listing: '{listing.get('title', 'Item')}'.",
            data={
                "type": "inspection_request",
                "inspection_id": str(res.inserted_id),
                "listing_id": body.listing_id,
            },
            notification_type="inspection"
        )

    await audit_log(
        actor_id=ObjectId(user["sub"]),
        actor_role=user.get("role"),
        action="inspection.request",
        target_type="inspection",
        target_id=res.inserted_id,
        detail={"listing_id": body.listing_id},
        ip=request.client.host if request.client else None,
        request_id=request.headers.get("X-Request-ID")
    )

    doc["_id"] = str(res.inserted_id)
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"])
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    return {"inspection": doc}


@router.get("")
async def list_inspections(user=Depends(get_current_user)):
    db = get_db()
    user_role = user.get("role", "buyer")

    # If the JWT says buyer/seller, double-check if the DB user actually has inspector or admin role
    if user_role not in ("admin", "inspector"):
        try:
            db_user = await db.users.find_one({"_id": ObjectId(user["sub"])})
            if db_user:
                db_role = db_user.get("role")
                if db_role in ("admin", "inspector") or "inspector" in db_user.get("roles", []):
                    user_role = db_role or "inspector"
        except Exception:
            pass

    # If inspector or admin, return queue of active and recent requests with enriched listing & users
    if user_role in ("admin", "inspector"):
        raw_items = await db.inspections.find({}).sort("created_at", -1).to_list(150)
    else:
        try:
            uid = ObjectId(user["sub"])
            # A buyer's own requests, plus listings they are the seller of, plus
            # admin-raised inspections (buyer_id is null) on listings they are
            # party to - otherwise those tasks are invisible to the app.
            seller_ids = await db.listings.distinct(
                "seller_id", {"seller_id": uid}
            )
            own_listing_ids = list(seller_ids or [])
            raw_items = await db.inspections.find({
                "$or": [
                    {"buyer_id": uid},
                    {"seller_id": uid},
                    {"listing_id": {"$in": own_listing_ids}, "buyer_id": None},
                ]
            }).sort("created_at", -1).to_list(100)
        except Exception:
            raw_items = []

    # Enrich listings & users
    listing_ids = [it["listing_id"] for it in raw_items if it.get("listing_id")]
    user_ids = list(set(
        [it["buyer_id"] for it in raw_items if it.get("buyer_id")] +
        [it["seller_id"] for it in raw_items if it.get("seller_id")] +
        [it["inspector_id"] for it in raw_items if it.get("inspector_id")]
    ))

    listings_map = {}
    if listing_ids:
        found_listings = await db.listings.find({"_id": {"$in": listing_ids}}).to_list(len(listing_ids))
        for l in found_listings:
            listings_map[str(l["_id"])] = {
                "id": str(l["_id"]),
                "title": l.get("title", "Device"),
                "category": l.get("category", "mobile"),
                "price": l.get("price", 0),
                "brand": l.get("brand"),
                "model": l.get("model"),
                "address": l.get("address"),
                "latitude": l.get("latitude"),
                "longitude": l.get("longitude"),
            }

    users_map = {}
    if user_ids:
        found_users = await db.users.find({"_id": {"$in": user_ids}}).to_list(len(user_ids))
        for u in found_users:
            users_map[str(u["_id"])] = {
                "id": str(u["_id"]),
                "name": u.get("name", "User"),
                "phone": u.get("phone"),
                "email": u.get("email"),
            }

    items = []
    for it in raw_items:
        lid_str = str(it["listing_id"])
        bid_str = str(it["buyer_id"]) if it.get("buyer_id") else None
        sid_str = str(it["seller_id"]) if it.get("seller_id") else None
        iid_str = str(it["inspector_id"]) if it.get("inspector_id") else None

        it["_id"] = str(it["_id"])
        it["listing_id"] = lid_str
        it["buyer_id"] = bid_str
        it["seller_id"] = sid_str
        if iid_str:
            it["inspector_id"] = iid_str

        it["listing"] = listings_map.get(lid_str)
        it["buyer"] = users_map.get(bid_str)
        it["seller"] = users_map.get(sid_str)
        it["inspector"] = users_map.get(iid_str)
        # Distinguish "raised by an admin during moderation" from "a buyer asked
        # for this" so the inspector panel can label the requester honestly
        # instead of falling back to "Anonymous Buyer".
        it["requested_by_admin"] = bool(it.get("requested_by_admin_id"))
        items.append(it)

    return {"items": _stringify_ids(items), "total": len(items)}


@router.get("/for-listing/{listing_id}")
async def inspection_for_listing(listing_id: str, user=Depends(get_current_user)):
    """Buyer-safe inspection state for a single listing.

    `GET /inspections` is scoped to the caller's own requests (buyer_id or
    seller_id), so listing-level inspections raised by an admin - which have no
    buyer_id - are invisible to buyers. The app needs to show "inspection
    requested" on the listing itself, so this endpoint returns the newest
    inspection for the listing with no requester identity.
    """
    db = get_db()
    try:
        oid = ObjectId(listing_id)
    except Exception:
        raise HTTPException(status_code=400, detail={"code": "bad_id", "message": "Invalid listing ID"})

    doc = await db.inspections.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    if not doc:
        return {"inspection": None}

    findings = doc.get("findings") or {}
    return {
        "inspection": {
            "id": str(doc["_id"]),
            "listing_id": listing_id,
            "status": doc.get("status", "requested"),
            "inspector_name": doc.get("inspector_name"),
            "score": doc.get("score", findings.get("score")),
            "verdict": findings.get("verdict"),
            "note": doc.get("note"),
            "created_at": doc.get("created_at"),
            "updated_at": doc.get("updated_at"),
            "completed_at": doc.get("completed_at"),
            "timeline": [
                {"status": t.get("status"), "label": t.get("label"), "timestamp": t.get("timestamp")}
                for t in (doc.get("timeline") or [])
            ],
            "requested_by_admin": bool(doc.get("requested_by_admin_id")),
        }
    }


@router.get("/{inspection_id}")
async def get_inspection(inspection_id: str, user=Depends(get_current_user)):
    db = get_db()
    try:
        oid = ObjectId(inspection_id)
    except Exception:
        raise HTTPException(status_code=400, detail={"code": "bad_id", "message": "Invalid inspection ID"})

    doc = await db.inspections.find_one({"_id": oid})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})

    ok = (
        str(doc.get("buyer_id")) == user["sub"]
        or str(doc.get("seller_id")) == user["sub"]
        or user.get("role") in ("admin", "inspector")
    )
    if not ok:
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not authorized"})

    lid = doc.get("listing_id")
    if lid:
        listing = await db.listings.find_one({"_id": lid})
        if listing:
            doc["listing"] = {
                "id": str(listing["_id"]),
                "title": listing.get("title"),
                "price": listing.get("price"),
                "category": listing.get("category"),
                "brand": listing.get("brand"),
                "model": listing.get("model"),
                "address": listing.get("address"),
                "latitude": listing.get("latitude"),
                "longitude": listing.get("longitude"),
                "status": listing.get("status"),
            }

    if doc.get("buyer_id"):
        b = await db.users.find_one({"_id": doc["buyer_id"]})
        if b:
            doc["buyer"] = {"id": str(b["_id"]), "name": b.get("name"), "phone": b.get("phone")}

    if doc.get("seller_id"):
        s = await db.users.find_one({"_id": doc["seller_id"]})
        if s:
            doc["seller"] = {"id": str(s["_id"]), "name": s.get("name"), "phone": s.get("phone")}

    if doc.get("inspector_id"):
        insp = await db.users.find_one({"_id": doc["inspector_id"]})
        if insp:
            doc["inspector"] = {"id": str(insp["_id"]), "name": insp.get("name"), "phone": insp.get("phone")}

    doc["_id"] = str(doc["_id"])
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"]) if doc.get("buyer_id") else None
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    if doc.get("inspector_id"):
        doc["inspector_id"] = str(doc["inspector_id"])

    return {"inspection": _stringify_ids(doc)}


@router.post("/{inspection_id}/accept")
async def accept_inspection(inspection_id: str, request: Request, user=Depends(get_current_user)):
    """Inspector accepts an inspection task from the inspector dashboard."""
    db = get_db()
    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})

    now = time.time()
    inspector_id = user["sub"]
    inspector_user = await db.users.find_one({"_id": ObjectId(inspector_id)})
    inspector_name = inspector_user.get("name", "Certified Inspector") if inspector_user else "Inspector"

    timeline_entry = {
        "status": "assigned",
        "label": "Inspector Assigned",
        "description": f"Inspector {inspector_name} has accepted your request.",
        "timestamp": now,
    }

    patch = {
        "status": "assigned",
        "inspector_id": ObjectId(inspector_id),
        "inspector_name": inspector_name,
        "updated_at": now,
    }

    await db.inspections.update_one(
        {"_id": doc["_id"]},
        {
            "$set": patch,
            "$push": {"timeline": timeline_entry}
        }
    )

    # Make sure user has inspector role
    if inspector_user and inspector_user.get("role") != "inspector":
        await db.users.update_one({"_id": ObjectId(inspector_id)}, {"$set": {"role": "inspector"}})

    # FCM Notification to Buyer (absent for admin-created inspection tasks)
    if doc.get("buyer_id"):
        buyer_id = str(doc["buyer_id"])
        await send_notification(
            user_id=buyer_id,
            title="Inspector Assigned! 🔍",
            body=f"{inspector_name} has accepted your inspection request and will visit the device location.",
            data={
                "type": "inspection_update",
                "inspection_id": inspection_id,
                "status": "assigned",
            },
            notification_type="inspection"
        )

    return {"ok": True, "status": "assigned", "inspector_name": inspector_name}


@router.post("/{inspection_id}/progress")
async def update_inspection_progress(inspection_id: str, request: Request, user=Depends(get_current_user)):
    """Delivery-app style status updates: 'travelling' | 'arrived' | 'in_progress'."""
    db = get_db()
    body = await request.json()
    new_status = body.get("status")

    if new_status not in ("travelling", "arrived", "in_progress"):
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": "Status must be travelling, arrived, or in_progress"})

    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})

    now = time.time()
    status_details = {
        "travelling": {
            "label": "Inspector on the Way 🛵",
            "desc": "The certified inspector is travelling to the inspection location.",
            "notif_body": "Your inspector is travelling to inspect the second-hand device."
        },
        "arrived": {
            "label": "Inspector Arrived 📍",
            "desc": "Inspector has reached the seller's location and met for physical inspection.",
            "notif_body": "Inspector has arrived at the location to inspect the device."
        },
        "in_progress": {
            "label": "Inspection in Progress ⚙️",
            "desc": "Conducting 18-point hardware, body, screen, and battery health checks.",
            "notif_body": "Hardware & body inspection is currently underway."
        }
    }

    info = status_details[new_status]
    timeline_entry = {
        "status": new_status,
        "label": info["label"],
        "description": info["desc"],
        "timestamp": now,
    }

    await db.inspections.update_one(
        {"_id": doc["_id"]},
        {
            "$set": {"status": new_status, "updated_at": now},
            "$push": {"timeline": timeline_entry}
        }
    )

# FCM Notification to Buyer (absent for admin-created inspection tasks)
    if doc.get("buyer_id"):
        buyer_id = str(doc["buyer_id"])
        await send_notification(
            user_id=buyer_id,
            title=f"Inspection Update: {info['label']}",
            body=info["notif_body"],
            data={
                "type": "inspection_update",
                "inspection_id": inspection_id,
                "status": new_status,
            },
            notification_type="inspection"
        )

    if doc.get("seller_id"):
        await send_notification(
            user_id=str(doc["seller_id"]),
            title=f"Inspector Status: {info['label']}",
            body=info["notif_body"],
            data={
                "type": "inspection_update",
                "inspection_id": inspection_id,
                "status": new_status,
            },
            notification_type="inspection"
        )

    return {"ok": True, "status": new_status}


@router.post("/{inspection_id}/submit-report")
async def submit_inspection_report(inspection_id: str, body: InspectionSubmitFindings, request: Request, user=Depends(get_current_user)):
    """Inspector submits complete on-site findings. AI engine calculates trust score."""
    db = get_db()
    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})

    now = time.time()
    findings_dict = body.dict()

    # Calculate AI Score based on inspector evaluation
    ai_score = body.calculated_score or compute_inspector_score(findings_dict)
    findings_dict["score"] = ai_score

    # Determine verdict label
    if ai_score >= 80:
        verdict = "Excellent Condition"
        result = "pass"
    elif ai_score >= 60:
        verdict = "Good / Moderate Condition"
        result = "pass"
    else:
        verdict = "Defects / High Wear"
        result = "fail"

    findings_dict["verdict"] = verdict
    findings_dict["result"] = result

    timeline_entry = {
        "status": "completed",
        "label": f"Inspection Completed (Score: {int(ai_score)}/100) ✅",
        "description": f"Verified with {verdict}. Detailed inspection report ready.",
        "timestamp": now,
    }

    # Update inspection record
    await db.inspections.update_one(
        {"_id": doc["_id"]},
        {
            "$set": {
                "status": "completed",
                "findings": findings_dict,
                "score": ai_score,
                "completed_at": now,
                "updated_at": now,
            },
            "$push": {"timeline": timeline_entry}
        }
    )

    # Update Listing's diagnostic & trust score
    listing_id = doc.get("listing_id")
    if listing_id:
        from app.api.listings import _trust_rating
        listing = await db.listings.find_one({"_id": listing_id})
        if listing:
            update_data = {
                "diagnostic_score": int(ai_score),
                "is_fit_to_show": True,
                "submission_score": ai_score,
                "submission_condition_summary": verdict,
                "submission_drawbacks": body.defects_found,
                "submission_at": now,
            }
            # Recompute trust rating
            l_copy = dict(listing)
            l_copy.update(update_data)
            update_data["trust"] = _trust_rating(l_copy)
            await db.listings.update_one({"_id": listing_id}, {"$set": update_data})

    # FCM Notification to Buyer
    buyer_id = str(doc.get("buyer_id"))
    await send_notification(
        user_id=buyer_id,
        title="Inspection Report Ready! 🎯",
        body=f"Your device check scored {int(ai_score)}/100 ({verdict}). You can safely proceed with purchase.",
        data={
            "type": "inspection_completed",
            "inspection_id": inspection_id,
            "score": str(int(ai_score)),
            "verdict": verdict,
        },
        notification_type="inspection"
    )

    # FCM Notification to Seller
    if doc.get("seller_id"):
        await send_notification(
            user_id=str(doc["seller_id"]),
            title="Inspection Report Completed! 🏷️",
            body=f"Inspector completed verification with score {int(ai_score)}/100.",
            data={
                "type": "inspection_completed",
                "inspection_id": inspection_id,
                "score": str(int(ai_score)),
            },
            notification_type="inspection"
        )

    return {
        "ok": True,
        "status": "completed",
        "score": ai_score,
        "verdict": verdict,
        "result": result,
    }


@router.patch("/{inspection_id}")
async def patch_inspection(inspection_id: str, body: InspectionPatch, request: Request, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.inspections.find_one({"_id": ObjectId(inspection_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Inspection not found"})

    if user.get("role") not in ("admin", "inspector"):
        owner = str(doc.get("buyer_id")) == user["sub"]
        if not owner or body.status not in ("cancelled",):
            raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Only admins or inspectors can update inspections"})

    patch = {}
    now = time.time()
    if body.status is not None:
        if body.status not in VALID_STATUSES:
            raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"Status must be one of {sorted(VALID_STATUSES)}"})
        if body.status in ADMIN_OR_INSPECTOR_STATUSES and user.get("role") not in ("admin", "inspector"):
            raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Only admins or inspectors can set this status"})
        patch["status"] = body.status
        # Add timeline entry
        timeline_entry = {
            "status": body.status,
            "label": f"Status changed to {body.status}",
            "description": f"Updated by {user.get('role', 'user')}.",
            "timestamp": now,
        }
        await db.inspections.update_one({"_id": doc["_id"]}, {"$push": {"timeline": timeline_entry}})

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

    if not patch:
        raise HTTPException(status_code=400, detail={"code": "no_fields", "message": "No updatable fields"})

    await db.inspections.update_one({"_id": doc["_id"]}, {"$set": {**patch, "updated_at": now}})
    doc.update(patch)

    doc["_id"] = str(doc["_id"])
    doc["listing_id"] = str(doc["listing_id"])
    doc["buyer_id"] = str(doc["buyer_id"]) if doc.get("buyer_id") else None
    doc["seller_id"] = str(doc["seller_id"]) if doc.get("seller_id") else None
    if doc.get("inspector_id"):
        doc["inspector_id"] = str(doc["inspector_id"])

    return {"inspection": doc}