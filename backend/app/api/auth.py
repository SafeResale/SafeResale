import secrets
import time
from fastapi import APIRouter, HTTPException, Depends, Request
from pydantic import BaseModel, EmailStr
from app.core.db import get_db
from app.core.security import hash_password, verify_password, create_access_token, create_refresh_token, get_current_user
from app.core.config import settings
from app.core.rate_limit import check_rate_limit
from app.core.audit import log as audit_log
from bson import ObjectId

router = APIRouter(prefix="/auth", tags=["auth"])

class RegisterIn(BaseModel):
    name: str
    email: EmailStr
    password: str
    phone: str | None = None

class VerifyIn(BaseModel):
    email: EmailStr
    code: str

class LoginIn(BaseModel):
    email: EmailStr
    password: str

class RefreshIn(BaseModel):
    refresh_token: str

class FirebaseIn(BaseModel):
    id_token: str

class ChangePasswordIn(BaseModel):
    current_password: str
    new_password: str

class ResetRequestIn(BaseModel):
    email: EmailStr

class ResetIn(BaseModel):
    email: EmailStr
    code: str
    new_password: str

class PhoneIn(BaseModel):
    phone: str

class PhoneVerifyIn(BaseModel):
    phone: str
    code: str
    name: str | None = None

class GoogleIdTokenIn(BaseModel):
    id_token: str

def _code() -> str:
    return f"{secrets.randbelow(900000)+100000}"

@router.post("/register", status_code=201)
async def register(body: RegisterIn, request: Request):
    await check_rate_limit(request, "auth", key=request.client.host if request.client else "unknown")
    db = get_db()
    if await db.users.find_one({"email": body.email}):
        raise HTTPException(status_code=409, detail={"code": "email_exists", "message": "Email already registered"})
    user = {"name": body.name, "email": body.email, "password_hash": hash_password(body.password), "phone": body.phone, "role": "seller", "verified": False, "created_at": time.time(), "updated_at": time.time()}
    res = await db.users.insert_one(user)
    code = _code()
    await db.verification_codes.insert_one({"email": body.email, "code": code, "created_at": time.time()})
    await audit_log(action="auth.register", target_type="user", target_id=res.inserted_id, detail={"email": body.email}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    print(f"[dev-mailer] verify code for {body.email}: {code}")
    return {"user": {"id": str(res.inserted_id), "email": body.email, "name": body.name}, "verify_required": True, **({"dev_code": code} if settings.dev_verify_enabled else {})}

@router.post("/verify")
async def verify(body: VerifyIn, request: Request):
    await check_rate_limit(request, "auth", key=body.email)
    db = get_db()
    rec = await db.verification_codes.find_one({"email": body.email, "code": body.code})
    if not rec:
        raise HTTPException(status_code=400, detail={"code": "invalid_code", "message": "Invalid verification code"})
    await db.users.update_one({"email": body.email}, {"$set": {"verified": True, "updated_at": time.time()}})
    await db.verification_codes.delete_many({"email": body.email})
    await audit_log(action="auth.verify", target_type="user", detail={"email": body.email}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"verified": True}

@router.post("/login")
async def login(body: LoginIn, request: Request):
    await check_rate_limit(request, "auth", key=body.email)
    db = get_db()
    user = await db.users.find_one({"email": body.email})
    if not user or not verify_password(body.password, user["password_hash"]):
        await audit_log(action="auth.login_fail", target_type="user", detail={"email": body.email}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
        raise HTTPException(status_code=401, detail={"code": "invalid_credentials", "message": "Invalid email or password"})
    access = create_access_token(str(user["_id"]), user.get("role", "seller"))
    raw_refresh = create_refresh_token()
    from hashlib import sha256
    h = sha256(raw_refresh.encode()).hexdigest()
    await db.refresh_tokens.insert_one({"user_id": user["_id"], "token_hash": h, "created_at": time.time()})
    await audit_log(action="auth.login_success", target_type="user", target_id=user["_id"], actor_id=user["_id"], actor_role=user.get("role"), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"access_token": access, "refresh_token": raw_refresh, "user": {"id": str(user["_id"]), "email": user["email"], "name": user["name"], "role": user.get("role","seller")}}

@router.post("/refresh")
async def refresh(body: RefreshIn, request: Request):
    await check_rate_limit(request, "auth", key=request.client.host if request.client else "unknown")
    from hashlib import sha256
    db = get_db()
    h = sha256(body.refresh_token.encode()).hexdigest()
    rec = await db.refresh_tokens.find_one({"token_hash": h})
    if not rec:
        raise HTTPException(status_code=401, detail={"code": "invalid_refresh", "message": "Invalid refresh token"})
    await db.refresh_tokens.delete_one({"token_hash": h})
    user = await db.users.find_one({"_id": rec["user_id"]})
    if not user:
        raise HTTPException(status_code=401, detail={"code": "user_not_found", "message": "User not found"})
    access = create_access_token(str(user["_id"]), user.get("role","seller"))
    new_raw = create_refresh_token()
    h2 = sha256(new_raw.encode()).hexdigest()
    await db.refresh_tokens.insert_one({"user_id": user["_id"], "token_hash": h2, "created_at": time.time()})
    return {"access_token": access, "refresh_token": new_raw}

@router.post("/logout", status_code=204)
async def logout(body: RefreshIn, request: Request):
    from hashlib import sha256
    db = get_db()
    h = sha256(body.refresh_token.encode()).hexdigest()
    await db.refresh_tokens.delete_one({"token_hash": h})
    await audit_log(action="auth.logout", ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return

@router.post("/phone/otp", status_code=202)
async def phone_otp(body: PhoneIn, request: Request):
    """Dev fallback OTP (not used once Firebase phone auth is configured).
    Sends (prints) a 6-digit code for {phone}. Swap this for a real SMS
    gateway or Firebase in production."""
    await check_rate_limit(request, "auth", key=body.phone)
    phone = body.phone.strip().replace(" ", "")
    if not (7 <= len(phone.lstrip("+")) <= 15):
        raise HTTPException(status_code=422, detail={"code": "bad_phone", "message": "Enter a valid phone number"})
    db = get_db()
    await db.verification_codes.delete_many({"phone": phone, "purpose": "phone"})
    code = _code()
    await db.verification_codes.insert_one({"phone": phone, "code": code, "purpose": "phone", "created_at": time.time()})
    await audit_log(action="auth.phone_otp", detail={"phone": phone}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    print(f"[dev-sms] OTP for {phone}: {code}")
    return {"sent": True, **({"dev_code": code} if settings.dev_verify_enabled else {})}

@router.post("/phone/verify")
async def phone_verify(body: PhoneVerifyIn, request: Request):
    await check_rate_limit(request, "auth", key=body.phone)
    phone = body.phone.strip().replace(" ", "")
    db = get_db()
    code = (body.code or "").strip()
    rec = await db.verification_codes.find_one({"phone": phone, "code": code, "purpose": "phone"})
    if not rec:
        raise HTTPException(status_code=400, detail={"code": "invalid_code", "message": "Invalid OTP"})
    now = time.time()
    await db.verification_codes.delete_many({"phone": phone, "purpose": "phone"})
    user = await db.users.find_one({"phone": phone})
    if user is None:
        res = await db.users.insert_one({
            "name": body.name or f"User {phone[-4:]}",
            "email": None, "password_hash": None, "phone": phone,
            "role": "seller", "verified": True, "provider": "phone",
            "created_at": now, "updated_at": now,
        })
        user = {"_id": res.inserted_id, "name": body.name or f"User {phone[-4:]}", "email": None, "role": "seller", "verified": False}
    else:
        # A phone number is an identifier, not proof of identity — sellers are
        # never auto-verified. Admins confirm KYC/document details and flip the
        # `verified` flag in the admin platform.
        await db.users.update_one({"_id": user["_id"]}, {"$set": {"provider": "phone", "updated_at": now}})
    access = create_access_token(str(user["_id"]), user.get("role", "seller"))
    raw_refresh = create_refresh_token()
    from hashlib import sha256
    h = sha256(raw_refresh.encode()).hexdigest()
    await db.refresh_tokens.insert_one({"user_id": user["_id"], "token_hash": h, "created_at": now})
    await audit_log(action="auth.phone_login", target_type="user", target_id=user["_id"], actor_id=user["_id"], actor_role=user.get("role"), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"access_token": access, "refresh_token": raw_refresh, "user": {"id": str(user["_id"]), "email": user["email"], "name": user["name"], "role": user.get("role", "seller")}}

@router.post("/request-reset", status_code=202)
async def request_reset(body: ResetRequestIn, request: Request):
    await check_rate_limit(request, "auth", key=body.email)
    db = get_db()
    user = await db.users.find_one({"email": body.email})
    if not user:
        return {"message": "If the email exists, a code was sent"}
    code = _code()
    await db.verification_codes.insert_one({"email": body.email, "code": code, "purpose": "reset", "created_at": time.time()})
    print(f"[dev-mailer] reset code for {body.email}: {code}")
    return {"message": "If the email exists, a code was sent", **({"dev_code": code} if settings.dev_verify_enabled else {})}

@router.post("/reset-password")
async def reset_password(body: ResetIn, request: Request):
    await check_rate_limit(request, "auth", key=body.email)
    db = get_db()
    rec = await db.verification_codes.find_one({"email": body.email, "code": body.code, "purpose": "reset"})
    if not rec:
        raise HTTPException(status_code=400, detail={"code": "invalid_code", "message": "Invalid code"})
    await db.users.update_one({"email": body.email}, {"$set": {"password_hash": hash_password(body.new_password), "updated_at": time.time()}})
    await db.verification_codes.delete_many({"email": body.email, "purpose": "reset"})
    await audit_log(action="auth.reset", target_type="user", detail={"email": body.email}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"message": "Password updated"}

@router.get("/me")
async def me(user=Depends(get_current_user)):
    db = get_db()
    doc = await db.users.find_one({"_id": ObjectId(user["sub"])})
    if not doc:
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"User not found"})
    return {"user": {"id": str(doc["_id"]), "email": doc["email"], "name": doc["name"], "role": doc.get("role","seller"), "verified": doc.get("verified", False), "phone": doc.get("phone")}}

@router.patch("/me")
async def patch_me(body: dict, user=Depends(get_current_user), request: Request = None):
    db = get_db()
    allowed = {"name", "phone"}
    patch = {k: v for k, v in body.items() if k in allowed}
    if not patch:
        raise HTTPException(status_code=400, detail={"code":"no_fields","message":"No updatable fields"})
    await db.users.update_one({"_id": ObjectId(user["sub"])}, {"$set": {**patch, "updated_at": time.time()}})
    doc = await db.users.find_one({"_id": ObjectId(user["sub"])})
    return {"user": {"id": str(doc["_id"]), "email": doc["email"], "name": doc["name"], "role": doc.get("role","seller")}}
from app.services.firebase import verify_id_token

class FirebaseIn(BaseModel):
    id_token: str

@router.post("/change-password")
async def change_password(body: ChangePasswordIn, request: Request, user=Depends(get_current_user)):
    await check_rate_limit(request, "auth", key=user["sub"])
    if len(body.new_password) < 8:
        raise HTTPException(status_code=422, detail={"code": "weak_password", "message": "New password must be at least 8 characters"})
    db = get_db()
    doc = await db.users.find_one({"_id": ObjectId(user["sub"])})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "User not found"})
    if not doc.get("password_hash"):
        raise HTTPException(status_code=409, detail={"code": "no_password", "message": "Account uses Firebase sign-in; reset via Firebase instead"})
    if not verify_password(body.current_password, doc["password_hash"]):
        raise HTTPException(status_code=401, detail={"code": "invalid_password", "message": "Current password is incorrect"})
    await db.users.update_one({"_id": doc["_id"]}, {"$set": {"password_hash": hash_password(body.new_password), "updated_at": time.time()}})
    await db.refresh_tokens.delete_many({"user_id": doc["_id"]})
    await audit_log(actor_id=user["sub"], actor_role=user.get("role"), action="auth.change_password", target_type="user", target_id=doc["_id"],
                    ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"message": "Password updated. Please sign in again."}


@router.post("/firebase")
async def firebase_login(body: FirebaseIn, request: Request):
    await check_rate_limit(request, "auth", key=request.client.host if request.client else "unknown")
    db = get_db()
    try:
        claims = await verify_id_token(body.id_token)
    except Exception as e:
        raise HTTPException(status_code=401, detail={"code": "invalid_firebase_token", "message": str(e)})
    email = (claims.get("email") or "").lower()
    phone = claims.get("phone_number")
    if not email and not phone:
        raise HTTPException(status_code=400, detail={"code": "missing_identifier", "message": "No email or phone in token"})
    firebase_uid = claims.get("sub") or claims.get("user_id") or claims.get("uid")
    now = time.time()
    user = await db.users.find_one({"firebase_uid": firebase_uid}) if firebase_uid else None
    if user is None and email:
        user = await db.users.find_one({"email": email})
    if user is None and phone:
        user = await db.users.find_one({"phone": phone})
    if user is not None and firebase_uid:
        # Never auto-verify: email/phone ownership isn't proof of seller identity.
        await db.users.update_one({"_id": user["_id"]}, {"$set": {"firebase_uid": firebase_uid, "provider": "firebase", "updated_at": now}})
    if user is None:
        res = await db.users.insert_one({
            "name": claims.get("name") or (email.split("@")[0] if email else f"User {phone[-4:]}"),
            "email": email or None,
            "phone": phone,
            "firebase_uid": firebase_uid,
            "password_hash": None,
            "role": "seller",
            "verified": False,
            "provider": "firebase",
            "created_at": now,
            "updated_at": now,
        })
        user = {"_id": res.inserted_id, "email": email or None, "name": claims.get("name") or (email.split("@")[0] if email else f"User {phone[-4:]}"), "role": "seller", "verified": False}
    access = create_access_token(str(user["_id"]), user.get("role", "seller"))
    raw_refresh = create_refresh_token()
    from hashlib import sha256
    h = sha256(raw_refresh.encode()).hexdigest()
    await db.refresh_tokens.insert_one({"user_id": user["_id"], "token_hash": h, "created_at": now})
    await audit_log(action="auth.firebase_login", target_type="user", target_id=user["_id"], actor_id=user["_id"], actor_role=user.get("role"), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"access_token": access, "refresh_token": raw_refresh, "user": {"id": str(user["_id"]), "email": user["email"], "name": user["name"], "role": user.get("role", "seller")}}
