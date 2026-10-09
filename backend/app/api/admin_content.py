"""Admin content management — blogs, FAQs and tips.

Adapted from the reference panel's Blog / FAQ / Tip modules. Each resource is a
small Mongo collection with CRUD + a published/draft status, list pagination and
search. Implemented through a tiny router factory so adding a content type later
is a two-liner.
"""
import time
import re
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel, Field

from app.core.db import get_db
from app.core.security import require_role
from app.core.audit import log as audit_log
from app.core.serialize import parse_id, s as _s

CONTENT_STATUSES = ("published", "draft")


def _slugify(raw: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", raw.strip().lower()).strip("-")


class BlogIn(BaseModel):
    title: str
    slug: str | None = None
    excerpt: str | None = None
    content: str = ""
    cover_url: str | None = None
    status: str = "draft"
    published_at: float | None = None


class FaqIn(BaseModel):
    question: str
    answer: str = ""
    category: str | None = None
    sort: int = 0
    status: str = "published"


class TipIn(BaseModel):
    title: str
    description: str = ""
    category: str | None = None
    sort: int = 0
    status: str = "published"


_ATTRS = {
    "blogs": ("title", "slug", "excerpt", "content", "cover_url", "status", "published_at"),
    "faqs": ("question", "answer", "category", "sort", "status"),
    "tips": ("title", "description", "category", "sort", "status"),
}
_SEARCH = {
    "blogs": ["title", "excerpt"],
    "faqs": ["question", "answer"],
    "tips": ["title", "description"],
}
_SORT_DEFAULT = {
    "blogs": "published_at",
    "faqs": "sort",
    "tips": "sort",
}


def _validate_status(model):
    if model and getattr(model, "status", None):
        if model.status not in CONTENT_STATUSES:
            raise HTTPException(status_code=422, detail={"code": "bad_status", "message": "status must be published|draft"})


async def _slug_available(db, collection: str, slug: str, ignore_id=None) -> bool:
    q = {"slug": slug}
    if ignore_id:
        q["_id"] = {"$ne": ignore_id}
    return not await db[collection].find_one(q)


def _make_router(path: str, collection: str, schemas: type, attr: str) -> APIRouter:
    r = APIRouter(prefix=path, tags=["admin", collection])

    @r.get("")
    async def list_items(status: str | None = None, q: str | None = None, page: int = 1, page_size: int = 50,
                         user=Depends(require_role("admin"))):
        db = get_db()
        query: dict = {}
        if status:
            if status not in CONTENT_STATUSES:
                raise HTTPException(status_code=422, detail={"code": "bad_status", "message": "status must be published|draft"})
            query["status"] = status
        if q:
            query["$or"] = [{f: {"$regex": re.escape(q), "$options": "i"}} for f in _SEARCH[collection]]
        total = await db[collection].count_documents(query)
        items = await db[collection].find(query).sort("sort" if collection in ("faqs", "tips") else "created_at", -1).skip((page - 1) * page_size).limit(page_size).to_list(page_size)
        return {"items": [_s(i) for i in items], "total": total, "page": page, "page_size": page_size, "statuses": list(CONTENT_STATUSES)}

    @r.get("/{item_id}")
    async def get_item(item_id: str, user=Depends(require_role("admin"))):
        db = get_db()
        doc = await db[collection].find_one({"_id": parse_id(item_id, field="item_id")})
        if not doc:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Not found"})
        return {attr: _s(doc)}

    @r.post("", status_code=201)
    async def create_item(body: schemas, request: Request, user=Depends(require_role("admin"))):
        _validate_status(body)
        db = get_db()
        data = body.model_dump(exclude_none=True)
        if "slug" in _ATTRS[collection]:
            slug = _slugify(data.get("slug") or data.get("title", ""))
            data["slug"] = slug
            if not slug or not await _slug_available(db, collection, slug):
                raise HTTPException(status_code=409, detail={"code": "slug_exists", "message": "Slug already in use"})
        if "published_at" in _ATTRS[collection] and not data.get("published_at"):
            data["published_at"] = time.time()
        now = time.time()
        data.update({"created_at": now, "updated_at": now})
        res = await db[collection].insert_one(data)
        await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action=f"admin.{collection}.create", target_type=collection, target_id=res.inserted_id,
                        ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
        data["_id"] = res.inserted_id
        return {attr: _s(data)}

    @r.patch("/{item_id}")
    async def update_item(item_id: str, body: dict, request: Request, user=Depends(require_role("admin"))):
        db = get_db()
        oid = parse_id(item_id, field="item_id")
        existing = await db[collection].find_one({"_id": oid})
        if not existing:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Not found"})
        allowed = set(_ATTRS[collection])
        patch = {k: v for k, v in body.items() if k in allowed}
        if not patch:
            raise HTTPException(status_code=400, detail={"code": "no_fields", "message": "No updatable fields"})
        if patch.get("status") and patch["status"] not in CONTENT_STATUSES:
            raise HTTPException(status_code=422, detail={"code": "bad_status", "message": "status must be published|draft"})
        if "slug" in patch:
            slug = _slugify(patch["slug"])
            patch["slug"] = slug
            if not slug or not await _slug_available(db, collection, slug, ignore_id=oid):
                raise HTTPException(status_code=409, detail={"code": "slug_exists", "message": "Slug already in use"})
        patch["updated_at"] = time.time()
        await db[collection].update_one({"_id": oid}, {"$set": patch})
        await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action=f"admin.{collection}.update", target_type=collection, target_id=oid,
                        ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
        doc = await db[collection].find_one({"_id": oid})
        return {attr: _s(doc)}

    @r.delete("/{item_id}")
    async def delete_item(item_id: str, request: Request, user=Depends(require_role("admin"))):
        db = get_db()
        oid = parse_id(item_id, field="item_id")
        doc = await db[collection].find_one({"_id": oid})
        if not doc:
            raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Not found"})
        await db[collection].delete_one({"_id": oid})
        await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action=f"admin.{collection}.delete", target_type=collection, target_id=oid,
                        ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
        return {"ok": True}

    return r


router = APIRouter(tags=["admin", "content"])
router.include_router(_make_router("/admin/blogs", "blogs", BlogIn, "blog"))
router.include_router(_make_router("/admin/faqs", "faqs", FaqIn, "faq"))
router.include_router(_make_router("/admin/tips", "tips", TipIn, "tip"))