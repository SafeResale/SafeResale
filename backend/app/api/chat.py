"""Listing-tied 1-1 polling chat (seller↔buyer) — docs/13 PRD §6.4.

Collections:
  chat_threads: {listing_id, seller_id, buyer_id, participants, thread_with, last_message, last_message_at, updated_at, created_at, unread_counts}
    unique index on (listing_id, thread_with) == (listing_id, buyer_id)
  chat_messages: {thread_id, listing_id, sender_id, message, offer_price, created_at, type, read}
    index on (thread_id, created_at)
  chat_blocks: {blocker_id, blocked_id, created_at}
"""
import time
from typing import Optional

from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException, Request, Query
from pydantic import BaseModel
from pymongo.errors import DuplicateKeyError

from app.core.db import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/chat", tags=["chat"])


def _oid(s: str) -> ObjectId:
    try:
        return ObjectId(s)
    except (InvalidId, Exception):
        raise HTTPException(status_code=422, detail={"code": "bad_id", "message": f"Invalid id: {s}"})


async def _ensure_indexes(db):
    try:
        await db.chat_threads.create_index([("listing_id", 1), ("thread_with", 1)], unique=True, background=True)
    except Exception:
        pass
    try:
        await db.chat_messages.create_index([("thread_id", 1), ("created_at", 1)], background=True)
    except Exception:
        pass
    try:
        await db.chat_blocks.create_index([("blocker_id", 1), ("blocked_id", 1)], unique=True, background=True)
    except Exception:
        pass


class SendIn(BaseModel):
    message: str
    offer_price: Optional[float] = None
    recipient_id: Optional[str] = None  # optional: seller specifies buyer


def _is_blocked_sync(blocks, a: ObjectId, b: ObjectId) -> bool:
    # check caller has fetched blocks? helper for later
    return False


# ── POST /chat/listings/{listing_id}/send ───────────────────────────

@router.post("/listings/{listing_id}/send", status_code=201)
async def send_message(listing_id: str, body: SendIn, request: Request, user=Depends(get_current_user)):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "general", key=user["sub"])
    db = get_db()
    await _ensure_indexes(db)

    msg = (body.message or "").strip()
    if not msg:
        raise HTTPException(status_code=422, detail={"code": "empty_message", "message": "Message cannot be empty"})
    if len(msg) > 2000:
        raise HTTPException(status_code=422, detail={"code": "too_long", "message": "Message too long (max 2000)"})
    if body.offer_price is not None and (body.offer_price <= 0 or body.offer_price > 10_000_000):
        raise HTTPException(status_code=422, detail={"code": "bad_offer", "message": "Invalid offer price"})

    lid = _oid(listing_id)
    listing = await db.listings.find_one({"_id": lid})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})

    seller_id = listing.get("seller_id")
    # seller_id may be stored as ObjectId or string
    if isinstance(seller_id, str):
        try:
            seller_id = ObjectId(seller_id)
        except Exception:
            raise HTTPException(status_code=500, detail={"code": "bad_seller", "message": "Listing seller invalid"})
    if seller_id is None:
        raise HTTPException(status_code=500, detail={"code": "no_seller", "message": "Listing has no seller"})

    sender_id = _oid(user["sub"])

    # Determine buyer_id / other_id
    if str(seller_id) == user["sub"]:
        # sender is seller
        if body.recipient_id:
            buyer_id = _oid(body.recipient_id)
            if str(buyer_id) == user["sub"]:
                raise HTTPException(status_code=400, detail={"code": "self_chat", "message": "Cannot chat with yourself"})
        else:
            # try to infer buyer from existing threads for this listing
            threads = await db.chat_threads.find({"listing_id": lid, "seller_id": seller_id}).to_list(100)
            if not threads:
                raise HTTPException(status_code=400, detail={"code": "no_thread", "message": "No existing conversation for this listing — buyer must message first, or specify recipient_id"})
            if len(threads) > 1:
                raise HTTPException(status_code=400, detail={"code": "ambiguous_thread", "message": "Multiple buyers for this listing — specify recipient_id"})
            buyer_id = threads[0]["buyer_id"]
            if isinstance(buyer_id, str):
                buyer_id = ObjectId(buyer_id)
    else:
        buyer_id = sender_id

    if str(buyer_id) == str(seller_id):
        raise HTTPException(status_code=400, detail={"code": "self_chat", "message": "Cannot chat with yourself"})

    other_id = seller_id if str(buyer_id) == user["sub"] else buyer_id

    # Block check: if either has blocked the other, forbid
    blocked = await db.chat_blocks.find_one({
        "$or": [
            {"blocker_id": other_id, "blocked_id": sender_id},
            {"blocker_id": sender_id, "blocked_id": other_id},
        ]
    })
    if blocked:
        raise HTTPException(status_code=403, detail={"code": "blocked", "message": "You cannot message this user (blocked)"})

    now = time.time()
    # Find or create thread
    filt = {"listing_id": lid, "buyer_id": buyer_id}
    thread = await db.chat_threads.find_one(filt)
    is_new = thread is None
    if thread is None:
        doc = {
            "listing_id": lid,
            "seller_id": seller_id,
            "buyer_id": buyer_id,
            "participants": [seller_id, buyer_id],
            "thread_with": str(buyer_id),
            "last_message": msg,
            "last_message_at": now,
            "updated_at": now,
            "created_at": now,
            "unread_counts": {str(other_id): 1, str(sender_id): 0},
        }
        try:
            res = await db.chat_threads.insert_one(doc)
            doc["_id"] = res.inserted_id
            thread = doc
        except DuplicateKeyError:
            thread = await db.chat_threads.find_one(filt)
            if thread is None:
                raise HTTPException(status_code=500, detail={"code": "thread_error", "message": "Could not create thread"})
            is_new = False

    thread_id = thread["_id"]

    # Insert message
    mdoc = {
        "thread_id": thread_id,
        "listing_id": lid,
        "sender_id": sender_id,
        "message": msg,
        "offer_price": body.offer_price,
        "created_at": now,
        "type": "offer" if body.offer_price is not None else "text",
        "read": False,
    }
    res2 = await db.chat_messages.insert_one(mdoc)
    mdoc["_id"] = res2.inserted_id

    # Update thread last_message / unread
    # For a newly created thread unread was already set to 1, so only bump if not new
    if is_new:
        await db.chat_threads.update_one(
            {"_id": thread_id},
            {"$set": {"last_message": msg, "last_message_at": now, "updated_at": now}},
        )
    else:
        await db.chat_threads.update_one(
            {"_id": thread_id},
            {
                "$set": {"last_message": msg, "last_message_at": now, "updated_at": now},
                "$inc": {f"unread_counts.{str(other_id)}": 1},
            },
        )
    # Ensure sender unread stays 0 (inc not applied to sender)
    # If thread was pre-existing, unread for sender should be untouched; we set it to 0 only if it didn't exist before

    return {
        "message": {
            "id": str(mdoc["_id"]),
            "thread_id": str(thread_id),
            "listing_id": listing_id,
            "sender_id": str(sender_id),
            "message": msg,
            "offer_price": body.offer_price,
            "created_at": now,
            "type": mdoc["type"],
        },
        "thread_id": str(thread_id),
    }


# ── GET /chat (inbox) ───────────────────────────────────────────────

@router.get("")
async def list_threads(request: Request, user=Depends(get_current_user)):
    db = get_db()
    await _ensure_indexes(db)
    uid = _oid(user["sub"])
    threads = await db.chat_threads.find({"participants": uid}).sort("updated_at", -1).to_list(100)
    out = []
    for t in threads:
        lid = t["listing_id"]
        listing = await db.listings.find_one({"_id": lid})
        if listing is None:
            continue
        # other user
        buyer_id = t.get("buyer_id")
        seller_id = t.get("seller_id")
        if isinstance(buyer_id, str):
            try:
                buyer_id = ObjectId(buyer_id)
            except Exception:
                buyer_id = None
        if isinstance(seller_id, str):
            try:
                seller_id = ObjectId(seller_id)
            except Exception:
                seller_id = None
        other_id = seller_id if str(buyer_id) == user["sub"] else buyer_id
        if other_id is None:
            other = {"id": "", "name": "Unknown", "email": ""}
        else:
            u = await db.users.find_one({"_id": other_id})
            if u:
                other = {"id": str(u["_id"]), "name": u.get("name") or "User", "email": u.get("email") or ""}
            else:
                other = {"id": str(other_id), "name": "User", "email": ""}
        unread = 0
        try:
            uc = t.get("unread_counts") or {}
            unread = int(uc.get(user["sub"], 0) or 0)
        except Exception:
            unread = 0
        # listing brief
        lb = {
            "id": str(listing["_id"]),
            "title": listing.get("title") or "",
            "price": listing.get("price") or 0,
            "category": listing.get("category") or "",
            "status": listing.get("status") or "",
        }
        # try to fetch cover thumbnail key (first image)
        is_selling = str(seller_id) == user["sub"] if seller_id else False
        out.append({
            "thread_id": str(t["_id"]),
            "listing_id": str(t["listing_id"]),
            "listing": lb,
            "other_user": other,
            "last_message": t.get("last_message") or "",
            "last_message_at": t.get("last_message_at") or t.get("updated_at") or 0,
            "updated_at": t.get("updated_at") or 0,
            "unread": unread,
            "role": "selling" if is_selling else "buying",
        })
    return {"threads": out, "total": len(out)}


# ── GET /chat/listings/{listing_id}/messages ────────────────────────

@router.get("/listings/{listing_id}/messages")
async def get_messages(
    listing_id: str,
    request: Request,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    with_user: Optional[str] = Query(None, alias="with"),
    buyer_id: Optional[str] = Query(None),
    user=Depends(get_current_user),
):
    db = get_db()
    await _ensure_indexes(db)
    lid = _oid(listing_id)
    uid = _oid(user["sub"])

    # Resolve thread
    # with_user / buyer_id hints for seller viewing a specific buyer thread
    hint = with_user or buyer_id
    # also support recipient_id via query param "recipient_id"
    qp = request.query_params.get("recipient_id")
    if not hint and qp:
        hint = qp

    thread = None
    if hint:
        try:
            hint_oid = ObjectId(hint)
        except Exception:
            raise HTTPException(status_code=422, detail={"code": "bad_hint", "message": "Invalid with user id"})
        thread = await db.chat_threads.find_one({"listing_id": lid, "buyer_id": hint_oid, "participants": uid})
        if thread is None:
            thread = await db.chat_threads.find_one({"listing_id": lid, "participants": {"$all": [uid, hint_oid]}})
    else:
        # find threads where user participates for this listing
        candidates = await db.chat_threads.find({"listing_id": lid, "participants": uid}).sort("updated_at", -1).to_list(100)
        if not candidates:
            return {"messages": [], "total": 0, "page": page, "page_size": page_size, "thread_id": None}
        if len(candidates) == 1:
            thread = candidates[0]
        else:
            # seller with multiple buyers and no hint — return most recent thread's messages
            # (client should specify with=buyer_id for precise thread)
            thread = candidates[0]

    if thread is None:
        return {"messages": [], "total": 0, "page": page, "page_size": page_size, "thread_id": None}

    thread_id = thread["_id"]
    total = await db.chat_messages.count_documents({"thread_id": thread_id})
    skip = (page - 1) * page_size
    msgs = await db.chat_messages.find({"thread_id": thread_id}).sort("created_at", 1).skip(skip).limit(page_size).to_list(page_size)

    # Mark read: messages sent by other user become read, and unread count reset
    await db.chat_messages.update_many({"thread_id": thread_id, "sender_id": {"$ne": uid}, "read": False}, {"$set": {"read": True}})
    await db.chat_threads.update_one({"_id": thread_id}, {"$set": {f"unread_counts.{user['sub']}": 0}})

    out = []
    for m in msgs:
        out.append({
            "id": str(m["_id"]),
            "thread_id": str(m["thread_id"]),
            "listing_id": str(m["listing_id"]),
            "sender_id": str(m["sender_id"]),
            "message": m.get("message") or "",
            "offer_price": m.get("offer_price"),
            "created_at": m.get("created_at") or 0,
            "type": m.get("type") or "text",
            "read": m.get("read", False),
        })
    return {"messages": out, "total": total, "page": page, "page_size": page_size, "thread_id": str(thread_id)}


# ── Block / Unblock ─────────────────────────────────────────────────

@router.post("/block/{user_id}", status_code=200)
@router.post("/block-user/{user_id}", status_code=200)
async def block_user(user_id: str, request: Request, user=Depends(get_current_user)):
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "general", key=user["sub"])
    db = get_db()
    await _ensure_indexes(db)
    if user_id == user["sub"]:
        raise HTTPException(status_code=400, detail={"code": "self_block", "message": "Cannot block yourself"})
    target = _oid(user_id)
    exists = await db.users.find_one({"_id": target})
    if not exists:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "User not found"})
    blocker = _oid(user["sub"])
    await db.chat_blocks.update_one(
        {"blocker_id": blocker, "blocked_id": target},
        {"$set": {"blocker_id": blocker, "blocked_id": target, "created_at": time.time()}},
        upsert=True,
    )
    return {"blocked": True, "user_id": user_id}


@router.post("/unblock/{user_id}", status_code=200)
@router.post("/unblock-user/{user_id}", status_code=200)
@router.delete("/block/{user_id}", status_code=200)
@router.delete("/block-user/{user_id}", status_code=200)
async def unblock_user(user_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    await _ensure_indexes(db)
    target = _oid(user_id)
    blocker = _oid(user["sub"])
    await db.chat_blocks.delete_one({"blocker_id": blocker, "blocked_id": target})
    return {"blocked": False, "user_id": user_id}


@router.get("/blocked")
async def list_blocked(user=Depends(get_current_user)):
    db = get_db()
    await _ensure_indexes(db)
    blocker = _oid(user["sub"])
    rows = await db.chat_blocks.find({"blocker_id": blocker}).to_list(100)
    out = []
    for r in rows:
        uid = r["blocked_id"]
        u = await db.users.find_one({"_id": uid})
        out.append({
            "user_id": str(uid),
            "name": (u.get("name") if u else None) or "User",
            "email": (u.get("email") if u else None) or "",
            "created_at": r.get("created_at") or 0,
        })
    return {"blocked": out, "total": len(out)}

