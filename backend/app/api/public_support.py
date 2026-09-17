"""Public support surfaces — contact form submissions and user reports.

These mirror the reference panel's contact-us and user-report flows but stay
lightweight: messages land in `contact_messages`, user reports in `reports`,
both consumed by the admin support screens.
"""
import time
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel, EmailStr

from app.core.db import get_db
from app.core.rate_limit import check_rate_limit
from app.core.audit import log as audit_log
from app.core.security import get_current_user
from app.core.serialize import parse_id

router = APIRouter(tags=["public", "support"])


class ContactIn(BaseModel):
    name: str
    email: EmailStr
    subject: str
    message: str


@router.post("/contact", status_code=201)
async def submit_contact(body: ContactIn, request: Request):
    await check_rate_limit(request, "general", key=request.client.host if request.client else "unknown")
    db = get_db()
    now = time.time()
    doc = {"name": body.name, "email": str(body.email).lower(), "subject": body.subject, "message": body.message,
           "status": "new", "created_at": now, "updated_at": now}
    res = await db.contact_messages.insert_one(doc)
    await audit_log(action="support.contact", target_type="message", target_id=res.inserted_id,
                    detail={"email": doc["email"], "subject": body.subject},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"message": "Received — our team will get back to you soon", "id": str(res.inserted_id)}


class UserReportIn(BaseModel):
    reason: str
    description: str = ""


@router.post("/users/{user_id}/report", status_code=201)
async def report_user(user_id: str, body: UserReportIn, request: Request, user=Depends(get_current_user)):
    await check_rate_limit(request, "general", key=user["sub"])
    if not body.reason.strip():
        raise HTTPException(status_code=422, detail={"code": "reason_required", "message": "A reason is required"})
    db = get_db()
    oid = parse_id(user_id, field="user_id")
    target = await db.users.find_one({"_id": oid})
    if not target:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "User not found"})
    if str(oid) == str(user["sub"]):
        raise HTTPException(status_code=422, detail={"code": "self_report", "message": "You cannot report yourself"})
    now = time.time()
    doc = {"reporter_id": ObjectId(user["sub"]), "target_type": "user", "target_id": oid,
           "reason": body.reason, "description": body.description, "status": "pending",
           "created_at": now, "updated_at": now}
    res = await db.reports.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="support.report_user",
                    target_type="report", target_id=res.inserted_id, detail={"reason": body.reason},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"report": {"id": str(res.inserted_id), "status": "pending"}}