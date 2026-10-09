"""Admin support & engagement — content reports, contact messages, announcements.

Adapted from the reference panel's Report Reasons / User Reports / Contact Us /
Notification modules. SafeResale stores reports and messages in Mongo and keeps
a lightweight announcement feed (audience-scoped records) ready for a future FCM
delivery coupon; no external push is performed by this slice.
"""
import time
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel

from app.core.db import get_db
from app.core.security import require_role
from app.core.audit import log as audit_log
from app.core.serialize import parse_id, s as _s

router = APIRouter(prefix="/admin", tags=["admin", "support"])

REPORT_STATUSES = ("pending", "resolved", "dismissed")
REPORT_TYPES = ("listing", "user")
MESSAGE_STATUSES = ("new", "read", "resolved", "archived")
NOTIFICATION_AUDIENCES = ("all", "sellers", "buyers", "admins", "inspectors")


async def _report_target_brief(db, target_type: str, target_id):
    try:
        oid = ObjectId(target_id)
    except Exception:
        return None
    coll = db.listings if target_type == "listing" else db.users
    doc = await coll.find_one({"_id": oid})
    if not doc:
        return None
    if target_type == "listing":
        return {"id": str(oid), "title": doc.get("title"), "status": doc.get("status"), "category": doc.get("category")}
    return {"id": str(oid), "name": doc.get("name"), "email": doc.get("email"), "status": doc.get("status"), "role": doc.get("role")}


async def _reporter_brief(db, reporter_id):
    if not reporter_id:
        return None
    try:
        u = await db.users.find_one({"_id": ObjectId(reporter_id)})
    except Exception:
        return None
    return {"id": str(reporter_id), "name": u.get("name") if u else None, "email": u.get("email") if u else None}


# --------------------------------------------------------------------------- reports

@router.get("/reports")
async def list_reports(status: str | None = None, target_type: str | None = None,
                       q: str | None = None, page: int = 1, page_size: int = 20,
                       user=Depends(require_role("admin"))):
    if status and status not in REPORT_STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"status must be one of {list(REPORT_STATUSES)}"})
    if target_type and target_type not in REPORT_TYPES:
        raise HTTPException(status_code=422, detail={"code": "bad_type", "message": f"target_type must be listing|user"})
    db = get_db()
    query: dict = {}
    if status:
        query["status"] = status
    if target_type:
        query["target_type"] = target_type
    if q:
        import re
        query["$or"] = [{"reason": {"$regex": re.escape(q), "$options": "i"}}, {"description": {"$regex": re.escape(q), "$options": "i"}}]
    total = await db.reports.count_documents(query)
    docs = await db.reports.find(query).sort("created_at", -1).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    items = []
    for d in docs:
        item = _s(d)
        item["target"] = await _report_target_brief(db, d.get("target_type"), d.get("target_id"))
        item["reporter"] = await _reporter_brief(db, d.get("reporter_id"))
        items.append(item)
    return {"items": items, "total": total, "page": page, "page_size": page_size, "statuses": list(REPORT_STATUSES), "types": list(REPORT_TYPES)}


@router.get("/reports/{report_id}")
async def report_detail(report_id: str, user=Depends(require_role("admin"))):
    db = get_db()
    d = await db.reports.find_one({"_id": parse_id(report_id, field="report_id")})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Report not found"})
    item = _s(d)
    item["target"] = await _report_target_brief(db, d.get("target_type"), d.get("target_id"))
    item["reporter"] = await _reporter_brief(db, d.get("reporter_id"))
    return {"report": item}


class ReportResolution(BaseModel):
    note: str = ""


async def _resolve_report(report_id: str, status: str, body: ReportResolution, request: Request, user) -> dict:
    db = get_db()
    d = await db.reports.find_one({"_id": parse_id(report_id, field="report_id")})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Report not found"})
    now = time.time()
    await db.reports.update_one({"_id": d["_id"]}, {"$set": {"status": status, "resolution_note": body.note, "resolved_at": now,
                                                             "resolved_by": ObjectId(user["sub"]), "updated_at": now}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action=f"admin.report.{status}", target_type="report", target_id=d["_id"],
                    detail={"note": body.note}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    r = await db.reports.find_one({"_id": d["_id"]})
    return {"report": _s(r)}


@router.post("/reports/{report_id}/resolve")
async def resolve(report_id: str, body: ReportResolution, request: Request, user=Depends(require_role("admin"))):
    return await _resolve_report(report_id, "resolved", body, request, user)


@router.post("/reports/{report_id}/dismiss")
async def dismiss(report_id: str, body: ReportResolution, request: Request, user=Depends(require_role("admin"))):
    return await _resolve_report(report_id, "dismissed", body, request, user)


# --------------------------------------------------------------------------- messages

@router.get("/messages")
async def list_messages(status: str | None = None, q: str | None = None, page: int = 1, page_size: int = 20,
                        user=Depends(require_role("admin"))):
    if status and status not in MESSAGE_STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"status must be one of {list(MESSAGE_STATUSES)}"})
    db = get_db()
    query: dict = {}
    if status:
        query["status"] = status
    if q:
        import re
        query["$or"] = [{"name": {"$regex": re.escape(q), "$options": "i"}}, {"email": {"$regex": re.escape(q), "$options": "i"}}, {"subject": {"$regex": re.escape(q), "$options": "i"}}]
    total = await db.contact_messages.count_documents(query)
    docs = await db.contact_messages.find(query).sort("created_at", -1).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    return {"items": [_s(d) for d in docs], "total": total, "page": page, "page_size": page_size, "statuses": list(MESSAGE_STATUSES)}


@router.get("/messages/{message_id}")
async def message_detail(message_id: str, user=Depends(require_role("admin"))):
    db = get_db()
    d = await db.contact_messages.find_one({"_id": parse_id(message_id, field="message_id")})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Message not found"})
    if d.get("status") == "new":
        await db.contact_messages.update_one({"_id": d["_id"]}, {"$set": {"status": "read", "updated_at": time.time()}})
        d["status"] = "read"
    return {"message": _s(d)}


class MessageUpdate(BaseModel):
    status: str


@router.patch("/messages/{message_id}")
async def update_message(message_id: str, body: MessageUpdate, request: Request, user=Depends(require_role("admin"))):
    if body.status not in MESSAGE_STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"status must be one of {list(MESSAGE_STATUSES)}"})
    db = get_db()
    oid = parse_id(message_id, field="message_id")
    d = await db.contact_messages.find_one({"_id": oid})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Message not found"})
    now = time.time()
    await db.contact_messages.update_one({"_id": oid}, {"$set": {"status": body.status, "updated_at": now}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.message.update", target_type="message", target_id=oid,
                    detail={"status": body.status}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"message": _s({**d, "status": body.status, "updated_at": now})}


@router.delete("/messages/{message_id}")
async def delete_message(message_id: str, request: Request, user=Depends(require_role("admin"))):
    db = get_db()
    oid = parse_id(message_id, field="message_id")
    d = await db.contact_messages.find_one({"_id": oid})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Message not found"})
    await db.contact_messages.delete_one({"_id": oid})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.message.delete", target_type="message", target_id=oid,
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"ok": True}


# --------------------------------------------------------------------------- notifications

class NotificationIn(BaseModel):
    title: str
    body: str = ""
    audience: str = "all"


@router.get("/notifications")
async def list_notifications(page: int = 1, page_size: int = 20, user=Depends(require_role("admin"))):
    db = get_db()
    total = await db.notifications.count_documents({})
    docs = await db.notifications.find().sort("created_at", -1).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    items = []
    for d in docs:
        item = _s(d)
        if item.get("sent_by"):
            item["author"] = await _reporter_brief(db, item["sent_by"])
        items.append(item)
    return {"items": items, "total": total, "page": page, "page_size": page_size, "audiences": list(NOTIFICATION_AUDIENCES)}


@router.post("/notifications", status_code=201)
async def create_notification(body: NotificationIn, request: Request, user=Depends(require_role("admin"))):
    if body.audience not in NOTIFICATION_AUDIENCES:
        raise HTTPException(status_code=422, detail={"code": "bad_audience", "message": f"audience must be one of {list(NOTIFICATION_AUDIENCES)}"})
    db = get_db()
    role_q = {"all": {}, "sellers": {"role": "seller"}, "buyers": {"role": "buyer"}, "admins": {"role": "admin"}, "inspectors": {"role": "inspector"}}
    recipient_count = await db.users.count_documents(role_q[body.audience])
    now = time.time()
    doc = {"title": body.title, "body": body.body, "audience": body.audience, "status": "sent",
           "sent_by": ObjectId(user["sub"]), "sent_at": now, "recipient_count": recipient_count,
           "delivery": "in_app", "created_at": now, "updated_at": now}
    res = await db.notifications.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.notification.create", target_type="notification", target_id=res.inserted_id,
                    detail={"audience": body.audience, "recipient_count": recipient_count},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = res.inserted_id
    return {"notification": _s(doc)}


@router.get("/notifications/{notification_id}")
async def notification_detail(notification_id: str, user=Depends(require_role("admin"))):
    db = get_db()
    d = await db.notifications.find_one({"_id": parse_id(notification_id, field="notification_id")})
    if not d:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Notification not found"})
    return {"notification": _s(d)}