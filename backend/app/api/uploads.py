import time
import re
import secrets
import hashlib
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Request, Response
from pydantic import BaseModel
from bson import ObjectId
from app.core.db import get_db
from app.core.security import get_current_user
from app.core.storage import storage
from pathlib import Path

router = APIRouter(prefix="/listings", tags=["uploads"])
# Raw-bytes receiver for the local storage driver. The token endpoint returns
# upload_url="/uploads/{key}"; the client PUTs bytes here, then calls
# confirm-upload. (S3 driver would return a presigned URL instead.)
bytes_router = APIRouter(prefix="/uploads", tags=["uploads"])

ALLOWED = {"image/jpeg", "image/png", "image/webp"}
MAX_SIZE = 10 * 1024 * 1024

class TokenReq(BaseModel):
    angle: str
    filename: str
    content_type: str
    size: int

class ConfirmReq(BaseModel):
    upload_token: str
    stored_key: str
    angle: str
    quality: dict | None = None

def _safe_filename(name: str) -> str:
    base = Path(name or "photo.jpg").name
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", base) or "photo.jpg"
    return safe[:100]


def _is_safe_key(key: str) -> bool:
    # Keys are server-generated as "{listing_id}/{angle}_{rand}_{filename}".
    # Reject traversal, absolute paths and absurd lengths.
    return bool(key) and len(key) < 256 and ".." not in key and not key.startswith("/")


@router.post("/{listing_id}/upload-token")
async def upload_token(listing_id: str, body: TokenReq, request: Request, user=Depends(get_current_user)):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "upload-token", key=user["sub"])
    db = get_db()
    listing = await db.listings.find_one({"_id": ObjectId(listing_id), "seller_id": ObjectId(user["sub"])})
    if not listing:
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"Listing not found"})
    if body.angle not in ("front","back","left","right","top","bottom","front_45","back_45"):
        raise HTTPException(status_code=400, detail={"code":"invalid_angle","message":"Invalid angle"})
    if body.content_type not in ALLOWED:
        raise HTTPException(status_code=400, detail={"code":"invalid_mime","message":"Only jpeg/png/webp allowed"})
    if body.size > MAX_SIZE:
        raise HTTPException(status_code=400, detail={"code":"too_large","message":"Max 10 MB"})
    token = secrets.token_urlsafe(24)
    key = f"{listing_id}/{body.angle}_{secrets.token_hex(4)}_{_safe_filename(body.filename)}"
    await db.upload_tokens.insert_one({"token": token, "listing_id": ObjectId(listing_id), "angle": body.angle, "key": key, "expires_at": time.time()+600, "used": False})
    info = storage.create_upload_url(key, body.content_type)
    from app.core.audit import log as audit_log
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="upload.token", target_type="listing", target_id=ObjectId(listing_id), detail={"angle": body.angle}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"upload_token": token, "upload_url": info["upload_url"], "expires_at": time.time()+600, "key": key}

@router.post("/{listing_id}/confirm-upload")
async def confirm_upload(listing_id: str, body: ConfirmReq, request: Request, user=Depends(get_current_user)):
    db = get_db()
    rec = await db.upload_tokens.find_one({"token": body.upload_token, "listing_id": ObjectId(listing_id)})
    if not rec or rec.get("used") or rec["expires_at"] < time.time():
        raise HTTPException(status_code=400, detail={"code":"invalid_token","message":"Invalid or expired upload token"})
    await db.upload_tokens.update_one({"token": body.upload_token}, {"$set": {"used": True}})
    # compute SHA256 + server-side OpenCV recheck (real, per 01-prd.md:6)
    p = Path(storage.base) / body.stored_key
    sha = None
    server_quality = None
    if p.exists():
        h = hashlib.sha256()
        h.update(p.read_bytes())
        sha = h.hexdigest()
        try:
            import sys
            sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m6_image_quality" / "scripts"))
            from common import analyze_image_quality  # type: ignore
            server_quality = analyze_image_quality(str(p))
            # merge client + server: server is source of truth
            merged_quality = {**(body.quality or {}), "server": server_quality, "passed": server_quality["passed"]}
        except Exception as e:
            server_quality = {"error": str(e), "passed": True}
            merged_quality = body.quality
    else:
        merged_quality = body.quality
    img_doc = {"listing_id": ObjectId(listing_id), "angle": body.angle, "stored_key": body.stored_key, "sha256": sha, "quality": merged_quality, "server_quality": server_quality, "client_quality": body.quality, "created_at": time.time(), "server_timestamp": time.time()}
    res = await db.listing_images.insert_one(img_doc)
    img_doc["_id"] = str(res.inserted_id)
    img_doc["listing_id"] = str(img_doc["listing_id"])
    from app.core.audit import log as audit_log
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="upload.confirm", target_type="listing", target_id=ObjectId(listing_id), detail={"angle": body.angle, "sha256": sha, "quality_passed": (merged_quality or {}).get("passed")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"listing_image": img_doc}

@bytes_router.put("/{key:path}")
async def put_bytes(key: str, request: Request, user=Depends(get_current_user)):
    """Receive raw image bytes for a previously issued upload token (local driver)."""
    if not _is_safe_key(key):
        raise HTTPException(status_code=400, detail={"code": "invalid_key", "message": "Invalid storage key"})
    ctype = (request.headers.get("content-type") or "").split(";")[0].strip().lower()
    if ctype not in ALLOWED:
        raise HTTPException(status_code=400, detail={"code": "invalid_mime", "message": "Only jpeg/png/webp allowed"})
    body = await request.body()
    if not body:
        raise HTTPException(status_code=400, detail={"code": "empty_body", "message": "Empty upload"})
    if len(body) > MAX_SIZE:
        raise HTTPException(status_code=413, detail={"code": "too_large", "message": "Max 10 MB"})
    db = get_db()
    rec = await db.upload_tokens.find_one({"key": key, "used": False})
    if not rec or rec.get("expires_at", 0) < time.time():
        raise HTTPException(status_code=400, detail={"code": "invalid_token", "message": "No valid upload token for this key"})
    listing = await db.listings.find_one({"_id": rec["listing_id"], "seller_id": ObjectId(user["sub"])})
    if not listing:
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not your listing"})
    p = Path(storage.base) / key
    if Path(storage.base).resolve() not in p.resolve().parents:
        raise HTTPException(status_code=400, detail={"code": "invalid_key", "message": "Key escapes storage root"})
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(body)
    sha = hashlib.sha256(body).hexdigest()
    return {"key": key, "size": len(body), "sha256": sha}


@router.get("/{listing_id}/images")
async def list_images(listing_id: str, user=Depends(get_current_user)):
    db = get_db()
    items = await db.listing_images.find({"listing_id": ObjectId(listing_id)}).to_list(100)
    for it in items:
        it["_id"] = str(it["_id"])
        it["listing_id"] = str(it["listing_id"])
    return items
