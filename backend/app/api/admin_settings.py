"""Admin settings & system status.

Settings are a flat KV store in `system_settings` with typed defaults held
herein (grouped for the UI). Admin GET merges defaults with stored overrides;
admin PUT persists only known keys. System status aggregates backend health,
model metrics and collection counts for the admin System screen.
"""
import time
from typing import Any
from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId

from app.core.db import get_db
from app.core.security import require_role
from app.core.audit import log as audit_log
from app.core.serialize import s as _s

router = APIRouter(prefix="/admin", tags=["admin", "settings"])

DEFAULT_SETTINGS: dict[str, dict[str, Any]] = {
    "general": {
        "site_name": {"value": "SafeResale", "type": "string", "description": "Platform display name"},
        "support_email": {"value": "support@saferesale.dev", "type": "email", "description": "Public support inbox; surfaced on contact pages"},
        "support_phone": {"value": "", "type": "string", "description": "Public support phone (optional)"},
        "currency": {"value": "INR", "type": "string", "description": "ISO currency code for listings and escrow"},
        "currency_symbol": {"value": "₹", "type": "string", "description": "Symbol shown next to amounts"},
        "escrow_fee_percent": {"value": 0.0, "type": "number", "description": "Platform fee % applied when escrow is released (0 disables)"},
        "max_listing_price": {"value": 10000000, "type": "number", "description": "Upper bound enforced on listing price"},
        "languages": {"value": "en", "type": "string", "description": "Supported locales, comma separated"},
    },
    "marketplace": {
        "risk_approve_band": {"value": 30, "type": "number", "description": "Adjusted risk <= threshold auto-approves"},
        "risk_review_band": {"value": 60, "type": "number", "description": "Risk above threshold enters moderation"},
        "required_angles": {"value": 8, "type": "number", "description": "Photo angles expected per listing"},
        "min_price": {"value": 1, "type": "number", "description": "Minimum listing price"},
        "allow_accessories": {"value": True, "type": "boolean", "description": "Enable accessory category listings"},
    },
    "notifications": {
        "admin_notify_email": {"value": "", "type": "email", "description": "Email to alert on new reports/messages"},
        "notify_on_new_listing": {"value": True, "type": "boolean", "description": "Notify admins when a listing is submitted"},
        "notify_on_report": {"value": True, "type": "boolean", "description": "Notify admins when content is reported"},
        "notify_on_escrow_dispute": {"value": True, "type": "boolean", "description": "Notify admins when an escrow enters review"},
    },
    "legal": {
        "about_us": {"value": "", "type": "textarea", "description": "About SafeResale (markdown)"},
        "privacy_policy": {"value": "", "type": "textarea", "description": "Privacy policy (markdown)"},
        "terms_conditions": {"value": "", "type": "textarea", "description": "Terms & conditions (markdown)"},
        "refund_policy": {"value": "", "type": "textarea", "description": "Refund / escrow policy (markdown)"},
    },
}

GROUPS = list(DEFAULT_SETTINGS.keys())


@router.get("/settings")
async def get_settings(group: str | None = None, user=Depends(require_role("admin"))):
    db = get_db()
    stored = {}
    async for s in db.system_settings.find({}):
        stored[s["key"]] = s["value"]
    groups_out: dict = {}
    for g, keys in DEFAULT_SETTINGS.items():
        if group and g != group:
            continue
        entries = {}
        for k, meta in keys.items():
            entries[k] = {"key": k, "value": stored.get(k, meta["value"]), "type": meta["type"], "description": meta["description"], "group": g, "overridden": k in stored}
        groups_out[g] = entries
    return {"groups": groups_out, "group_names": GROUPS}


@router.put("/settings")
async def put_settings(body: dict, request: Request, user=Depends(require_role("admin"))):
    db = get_db()
    known = {k: meta for g in DEFAULT_SETTINGS.values() for k, meta in g.items()}
    unknown = [k for k in body if k not in known]
    if unknown:
        raise HTTPException(status_code=422, detail={"code": "unknown_keys", "message": f"Unknown setting keys: {unknown}"})
    actor = ObjectId(user["sub"])
    now = time.time()
    changed = dict(body)
    for key, value in body.items():
        want_type = known[key]["type"]
        stored = await db.system_settings.find_one({"key": key})
        prev = stored["value"] if stored else known[key]["value"]
        if prev == value:
            changed.pop(key, None)
            continue
        if not stored:
            await db.system_settings.insert_one({"key": key, "value": value, "type": want_type, "group": next(g for g, ks in DEFAULT_SETTINGS.items() if key in ks), "created_at": now, "updated_at": now, "updated_by": actor})
        else:
            await db.system_settings.update_one({"key": key}, {"$set": {"value": value, "updated_at": now, "updated_by": actor}})
    if changed:
        await audit_log(actor_id=actor, actor_role="admin", action="admin.settings.update", target_type="settings",
                        detail={"keys": list(changed.keys())}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"saved": list(changed.keys()), "message": f"{len(changed)} setting(s) updated"}


@router.get("/system")
async def system_status(user=Depends(require_role("admin"))):
    """Aggregated system overview: backend health, storage, models, DB counts."""
    from app.core.db import ping_db
    from app.core.storage import ping_storage
    from app.services.vision_provider import get_provider
    db = get_db()

    health = {"status": "ok", "db": "ok" if await ping_db() else "down"}
    try:
        provider = get_provider()
        health["vision_provider"] = provider.name() if hasattr(provider, "name") else ("stub" if provider.simulated() else "live")
        health["vision_simulated"] = provider.simulated()
    except Exception:
        health["vision_provider"] = "unavailable"
        health["vision_simulated"] = None
    try:
        health["storage"] = "ok" if await ping_storage() else "degraded"
    except Exception:
        health["storage"] = "unknown"

    counts = {}
    for coll in ("users", "listings", "listing_images", "detections", "decisions", "risk_scores",
                 "escrows", "audit_logs", "reports", "contact_messages", "notifications", "blogs", "faqs", "tips"):
        counts[coll] = await db[coll].count_documents({})

    models = []
    async for m in db.model_metrics.find().sort("created_at", -1).limit(20):
        models.append(_s(m))

    from app.core.config import settings as cfg
    return {
        "health": health,
        "storage_driver": getattr(cfg, "storage_driver", "local"),
        "db_name": cfg.db_name,
        "version": "1.0.0",
        "counts": counts,
        "models": models,
    }