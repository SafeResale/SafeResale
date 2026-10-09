"""Admin user management — list, detail, create, role/status changes, password reset.

Adapted from the reference panel's Customers + Staff modules to SafeResale's
single-role RBAC (role field on the user, roles pulled from JWT `role` claim).
All writes are audit-logged; administrators cannot demote/suspend themselves.
"""
import time
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel, EmailStr

from app.core.db import get_db
from app.core.security import require_role, hash_password
from app.core.audit import log as audit_log
from app.core.serialize import parse_id, public_user, strip_password, s as _s

router = APIRouter(prefix="/admin/users", tags=["admin", "users"])

ROLES = ("seller", "buyer", "admin", "inspector")
STATUSES = ("active", "suspended")


class UserCreate(BaseModel):
    name: str
    email: EmailStr
    password: str
    role: str = "seller"
    phone: str | None = None


class StatusChange(BaseModel):
    status: str
    reason: str = ""


class RoleChange(BaseModel):
    role: str


class PasswordReset(BaseModel):
    new_password: str


@router.get("")
async def list_users(
    role: str | None = None,
    status: str | None = None,
    verified: bool | None = None,
    q: str | None = None,
    page: int = 1,
    page_size: int = 20,
    sort_by: str = "created_at",
    sort_dir: str = "desc",
    user=Depends(require_role("admin")),
):
    import re
    if role and role not in ROLES:
        raise HTTPException(status_code=422, detail={"code": "bad_role", "message": f"role must be one of {list(ROLES)}"})
    if status and status not in STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"status must be one of {list(STATUSES)}"})
    db = get_db()
    q_doc: dict = {}
    if role:
        q_doc["$or"] = [{"role": role}, {"roles": role}]
    if status:
        q_doc["status"] = status
    if verified is not None:
        q_doc["verified"] = verified
    if q:
        q_doc["$or"] = [{"name": {"$regex": re.escape(q), "$options": "i"}}, {"email": {"$regex": re.escape(q), "$options": "i"}}]
    sort_key = sort_by if sort_by in ("created_at", "name", "email", "updated_at") else "created_at"
    sort_dir_i = 1 if sort_dir == "asc" else -1
    total = await db.users.count_documents(q_doc)
    docs = await db.users.find(q_doc).sort(sort_key, sort_dir_i).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    items = []
    for d in docs:
        item = public_user(d)
        item["listing_count"] = await db.listings.count_documents({"seller_id": ObjectId(item["_id"])})
        items.append(item)
    return {"items": items, "total": total, "page": page, "page_size": page_size, "roles": list(ROLES), "statuses": list(STATUSES)}


async def _user_or_404(db, user_id: str) -> dict:
    oid = parse_id(user_id, field="user_id")
    doc = await db.users.find_one({"_id": oid})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "User not found"})
    return doc


@router.get("/{user_id}")
async def user_detail(user_id: str, user=Depends(require_role("admin"))):
    db = get_db()
    doc = await _user_or_404(db, user_id)
    oid = doc["_id"]
    listings = []
    async for l in db.listings.find({"seller_id": oid}).sort("created_at", -1).limit(50):
        listings.append(_s(l))
    by_status = {}
    async for g in db.listings.aggregate([{"$match": {"seller_id": oid}}, {"$group": {"_id": "$status", "n": {"$sum": 1}}}]):
        by_status[g["_id"]] = g["n"]
    escrows_as_buyer = await db.escrows.count_documents({"buyer_id": oid})
    escrows_as_seller = await db.escrows.count_documents({"seller_id": oid})
    reports_against = await db.reports.count_documents({"target_type": "user", "target_id": oid})
    latest_signals = await db.seller_behavior_features.find_one({"seller_id": oid}, sort=[("created_at", -1)])
    refresh_count = await db.refresh_tokens.count_documents({"user_id": oid})
    return {
        "user": public_user(doc),
        "stats": {
            "listings_by_status": by_status,
            "listing_count": sum(by_status.values()),
            "escrows_as_buyer": escrows_as_buyer,
            "escrows_as_seller": escrows_as_seller,
            "reports_against": reports_against,
            "active_sessions": refresh_count,
        },
        "listings": listings,
        "behavior": {"top_signals": _s(latest_signals.get("top_signals", [])) if latest_signals else [], "features": _s(latest_signals.get("features", {})) if latest_signals else {}},
    }


@router.post("", status_code=201)
async def create_user(body: UserCreate, request: Request, user=Depends(require_role("admin"))):
    if body.role not in ROLES:
        raise HTTPException(status_code=422, detail={"code": "bad_role", "message": f"role must be one of {list(ROLES)}"})
    if len(body.password) < 8:
        raise HTTPException(status_code=422, detail={"code": "weak_password", "message": "Password must be at least 8 characters"})
    db = get_db()
    email = str(body.email).lower()
    if await db.users.find_one({"email": email}):
        raise HTTPException(status_code=409, detail={"code": "email_exists", "message": "Email already registered"})
    now = time.time()
    doc = {
        "name": body.name, "email": email, "phone": body.phone,
        "password_hash": hash_password(body.password), "role": body.role, "roles": [body.role],
        "verified": True, "status": "active", "created_at": now, "updated_at": now,
    }
    res = await db.users.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.user.create", target_type="user", target_id=res.inserted_id,
                    detail={"email": email, "role": body.role}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = res.inserted_id
    return {"user": public_user(doc)}


@router.patch("/{user_id}/status")
async def change_status(user_id: str, body: StatusChange, request: Request, user=Depends(require_role("admin"))):
    if body.status not in STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"status must be one of {list(STATUSES)}"})
    if user_id == str(user["sub"]):
        raise HTTPException(status_code=409, detail={"code": "self_action", "message": "You cannot suspend your own account"})
    db = get_db()
    doc = await _user_or_404(db, user_id)
    prev = doc.get("status", "active")
    now = time.time()
    await db.users.update_one({"_id": doc["_id"]}, {"$set": {"status": body.status, "updated_at": now}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.user.status", target_type="user", target_id=doc["_id"],
                    detail={"prev_status": prev, "new_status": body.status, "reason": body.reason},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["status"] = body.status
    return {"user": public_user(doc), "changed": {"prev": prev, "now": body.status}}


@router.patch("/{user_id}/role")
async def change_role(user_id: str, body: RoleChange, request: Request, user=Depends(require_role("admin"))):
    if body.role not in ROLES:
        raise HTTPException(status_code=422, detail={"code": "bad_role", "message": f"role must be one of {list(ROLES)}"})
    if user_id == str(user["sub"]):
        raise HTTPException(status_code=409, detail={"code": "self_action", "message": "You cannot change your own role"})
    db = get_db()
    doc = await _user_or_404(db, user_id)
    prev = doc.get("role", "seller")
    now = time.time()
    await db.users.update_one({"_id": doc["_id"]}, {"$set": {"role": body.role, "roles": [body.role], "updated_at": now}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.user.role", target_type="user", target_id=doc["_id"],
                    detail={"prev_role": prev, "new_role": body.role},
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["role"] = body.role
    return {"user": public_user(doc), "changed": {"prev": prev, "now": body.role}}


@router.post("/{user_id}/reset-password")
async def reset_password(user_id: str, body: PasswordReset, request: Request, user=Depends(require_role("admin"))):
    if len(body.new_password) < 8:
        raise HTTPException(status_code=422, detail={"code": "weak_password", "message": "Password must be at least 8 characters"})
    db = get_db()
    doc = await _user_or_404(db, user_id)
    now = time.time()
    await db.users.update_one({"_id": doc["_id"]}, {"$set": {"password_hash": hash_password(body.new_password), "updated_at": now}})
    await db.refresh_tokens.delete_many({"user_id": doc["_id"]})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.user.reset_password", target_type="user", target_id=doc["_id"],
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"ok": True, "message": "Password reset. Existing sessions were revoked."}


@router.delete("/{user_id}")
async def delete_user(user_id: str, request: Request, user=Depends(require_role("admin"))):
    if user_id == str(user["sub"]):
        raise HTTPException(status_code=409, detail={"code": "self_action", "message": "You cannot delete your own account"})
    db = get_db()
    doc = await _user_or_404(db, user_id)
    oid = doc["_id"]
    listings = await db.listings.count_documents({"seller_id": oid})
    if listings:
        raise HTTPException(status_code=409, detail={"code": "user_has_listings", "message": f"User owns {listings} listing(s); suspend instead of deleting"})
    await db.users.delete_one({"_id": oid})
    await db.refresh_tokens.delete_many({"user_id": oid})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.user.delete", target_type="user", target_id=oid,
                    detail={"email": doc.get("email")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"ok": True}