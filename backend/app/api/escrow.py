from fastapi import APIRouter, Depends, HTTPException
import time
from bson import ObjectId

from app.core.security import require_role
from app.core.db import get_db

router = APIRouter(prefix="/admin/escrows", tags=["admin", "escrow"])

ESCROW_STATUSES = ("pending", "held", "in_review", "released", "refunded")

TRANSITIONS = {
    "pending": ("held", "refunded"),
    "held": ("in_review", "released", "refunded"),
    "in_review": ("released", "refunded"),
}


def _must_be(s: str) -> None:
    if s not in ESCROW_STATUSES:
        raise HTTPException(status_code=422, detail={"code": "bad_status", "message": f"Unknown status '{s}'"})


def _transition_from(current: str, action: str) -> str:
    nxt = {
        "hold": "held",
        "review": "in_review",
        "release": "released",
        "refund": "refunded",
    }.get(action)
    if nxt is None:
        raise HTTPException(status_code=422, detail={"code": "bad_action", "message": f"Unknown action '{action}'"})
    if nxt not in TRANSITIONS.get(current, ()):
        raise HTTPException(
            status_code=409,
            detail={"code": "invalid_transition", "message": f"Cannot '{action}' an escrow in '{current}'"},
        )
    return nxt


async def _get_escrow(db, escrow_id: str) -> dict:
    try:
        oid = ObjectId(escrow_id)
    except Exception:
        raise HTTPException(status_code=422, detail={"code": "bad_id", "message": "Invalid escrow id"})
    doc = await db.escrows.find_one({"_id": oid})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Escrow not found"})
    return doc


def _public(doc: dict) -> dict:
    out = {**doc, "_id": str(doc["_id"])}
    for k in ("listing_id", "buyer_id", "seller_id"):
        if doc.get(k):
            out[k] = str(doc[k])
    return out


@router.get("")
async def list_escrows(
    status: str | None = None,
    page: int = 1,
    page_size: int = 20,
    user=Depends(require_role("admin")),
):
    if status:
        _must_be(status)
    db = get_db()
    q: dict = {"status": status} if status else {}
    total = await db.escrows.count_documents(q)
    docs = (
        await db.escrows.find(q)
        .sort("created_at", -1)
        .skip((page - 1) * page_size)
        .limit(page_size)
        .to_list(page_size)
    )
    return {"items": [_public(d) for d in docs], "total": total, "page": page, "page_size": page_size}


@router.get("/kpis")
async def escrow_kpis(user=Depends(require_role("admin"))):
    db = get_db()
    held, review = 0.0, 0.0
    async for d in db.escrows.find({"status": {"$in": ["held", "in_review"]}}):
        if d["status"] == "held":
            held += d.get("amount", 0)
        else:
            review += d.get("amount", 0)
    counts = {}
    for s in ESCROW_STATUSES:
        counts[s] = await db.escrows.count_documents({"status": s})
    return {"held": round(held, 2), "in_review": round(review, 2), "in_hold_total": round(held + review, 2), "counts": counts}


@router.get("/{escrow_id}")
async def get_escrow(escrow_id: str, user=Depends(require_role("admin"))):
    db = get_db()
    doc = await _get_escrow(db, escrow_id)
    hops = await db.escrow_transitions.find({"escrow_id": doc["_id"]}).sort("created_at", 1).to_list(100)
    for h in hops:
        h["_id"] = str(h["_id"])
        h["escrow_id"] = str(h["escrow_id"])
        if h.get("actor_id"):
            h["actor_id"] = str(h["actor_id"])
    return {"escrow": _public(doc), "transitions": hops}


async def _transition(escrow_id: str, action: str, user) -> dict:
    db = get_db()
    doc = await _get_escrow(db, escrow_id)
    nxt = _transition_from(doc["status"], action)
    now = time.time()
    await db.escrows.update_one(
        {"_id": doc["_id"]},
        {"$set": {"status": nxt, "updated_at": now,
                  "resolved_at" if nxt in ("released", "refunded") else "updated_at": now}},
    )
    await db.escrow_transitions.insert_one(
        {"escrow_id": doc["_id"], "from_status": doc["status"], "to_status": nxt, "action": action,
         "actor_id": ObjectId(user["sub"]), "actor_role": "admin", "created_at": now}
    )
    await db.audit_logs.insert_one(
        {"actor_id": ObjectId(user["sub"]), "actor_role": "admin", "action": f"admin.escrow.{action}",
         "target_type": "escrow", "target_id": doc["_id"],
         "detail": {"escrow_id": str(doc["_id"]), "from_status": doc["status"], "to_status": nxt},
         "created_at": now}
    )
    doc["status"] = nxt
    doc["updated_at"] = now
    return _public(doc)


@router.post("/{escrow_id}/hold")
async def hold(escrow_id: str, user=Depends(require_role("admin"))):
    return {"escrow": await _transition(escrow_id, "hold", user)}


@router.post("/{escrow_id}/review")
async def review(escrow_id: str, user=Depends(require_role("admin"))):
    return {"escrow": await _transition(escrow_id, "review", user)}


@router.post("/{escrow_id}/release")
async def release(escrow_id: str, user=Depends(require_role("admin"))):
    return {"escrow": await _transition(escrow_id, "release", user)}


@router.post("/{escrow_id}/refund")
async def refund(escrow_id: str, user=Depends(require_role("admin"))):
    return {"escrow": await _transition(escrow_id, "refund", user)}
