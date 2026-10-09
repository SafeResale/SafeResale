"""Admin catalog management — categories CRUD.

The category set follows the PRD product scope (docs/08-ml-plan.md §3.1): the
verification trust engine is category-agnostic and keys off `slug`, so slugs
are immutable-after-use (deletion guarded when listings reference a category).
This router manages the canonical catalog defined in `app/core/catalog.py`:
rename, re-describe, tune the seller-form schema fields, enable/disable, reorder.
"""
import time
import re
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId
from pydantic import BaseModel

from app.core.db import get_db
from app.core.security import require_role
from app.core.audit import log as audit_log
from app.core.serialize import parse_id, s as _s
from app.core.catalog import CATEGORIES as DEFAULTS

router = APIRouter(prefix="/admin/categories", tags=["admin", "catalog"])


class CategoryIn(BaseModel):
    name: str
    slug: str | None = None
    description: str | None = None
    icon: str | None = None
    fields: list[str] = []
    active: bool = True
    sort: int = 0


def _slugify(raw: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", raw.strip().lower()).strip("-")


async def _ensure_defaults(db):
    """Seed the canonical catalog categories on first use (idempotent)."""
    if await db.categories.count_documents({}):
        return
    now = time.time()
    for c in DEFAULTS:
        await db.categories.insert_one({**c, "created_at": now, "updated_at": now})


@router.get("")
async def list_categories(user=Depends(require_role("admin"))):
    db = get_db()
    await _ensure_defaults(db)
    items = await db.categories.find().sort("sort", 1).to_list(200)
    out = []
    for c in items:
        d = _s(c)
        d["listing_count"] = await db.listings.count_documents({"category": d["slug"]})
        out.append(d)
    return {"items": out, "defaults_count": len(DEFAULTS)}


@router.post("", status_code=201)
async def create_category(body: CategoryIn, request: Request, user=Depends(require_role("admin"))):
    db = get_db()
    await _ensure_defaults(db)
    slug = _slugify(body.slug or body.name)
    if not slug:
        raise HTTPException(status_code=422, detail={"code": "bad_slug", "message": "Name must produce a valid slug"})
    if await db.categories.find_one({"slug": slug}):
        raise HTTPException(status_code=409, detail={"code": "slug_exists", "message": f"Category slug '{slug}' already exists"})
    now = time.time()
    doc = {"name": body.name, "slug": slug, "description": body.description, "icon": body.icon,
           "fields": body.fields, "active": body.active, "sort": body.sort, "created_at": now, "updated_at": now}
    res = await db.categories.insert_one(doc)
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.category.create", target_type="category", target_id=res.inserted_id,
                    detail={"slug": slug, "name": body.name}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = res.inserted_id
    return {"category": _s(doc)}


@router.patch("/{category_id}")
async def update_category(category_id: str, body: dict, request: Request, user=Depends(require_role("admin"))):
    db = get_db()
    oid = parse_id(category_id, field="category_id")
    existing = await db.categories.find_one({"_id": oid})
    if not existing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Category not found"})
    allowed = {"name", "description", "icon", "fields", "active", "sort"}
    patch = {k: v for k, v in body.items() if k in allowed}
    if "name" in patch and existing.get("slug") in (c["slug"] for c in DEFAULTS):
        # keep canonical slugs stable so the trust engine keeps working
        patch.pop("name", None)
        patch["description"] = body.get("description", existing.get("description"))
    if "fields" in patch and not isinstance(patch["fields"], list):
        raise HTTPException(status_code=422, detail={"code": "bad_fields", "message": "fields must be a list of strings"})
    if not patch:
        raise HTTPException(status_code=400, detail={"code": "no_fields", "message": "No updatable fields"})
    patch["updated_at"] = time.time()
    await db.categories.update_one({"_id": oid}, {"$set": patch})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.category.update", target_type="category", target_id=oid,
                    detail=patch, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc = await db.categories.find_one({"_id": oid})
    return {"category": _s(doc)}


@router.delete("/{category_id}")
async def delete_category(category_id: str, request: Request, user=Depends(require_role("admin"))):
    db = get_db()
    oid = parse_id(category_id, field="category_id")
    existing = await db.categories.find_one({"_id": oid})
    if not existing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Category not found"})
    used = await db.listings.count_documents({"category": existing["slug"]})
    if used:
        raise HTTPException(status_code=409, detail={"code": "category_in_use", "message": f"{used} listing(s) use '{existing['slug']}'; deactivate instead of deleting"})
    await db.categories.delete_one({"_id": oid})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role="admin", action="admin.category.delete", target_type="category", target_id=oid,
                    detail={"slug": existing.get("slug")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"ok": True}