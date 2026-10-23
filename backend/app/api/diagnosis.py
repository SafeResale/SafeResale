"""Device-diagnosis pairing + result ingestion (secure, token-based).

A listing that is a device may be diagnosed by a SEPARATE Diagnosis app running
on the device being sold. The seller's listing device never runs the tests.

States (explicit, validated transitions):
  awaiting_confirmation -> waiting_for_app -> token_generated -> paired ->
  in_progress -> results_received -> verification_completed
  (any non-terminal may go to failed / expired)

Tokens are generated on the backend, stored only as a SHA-256 hash, expire
after TOKEN_TTL_SECONDS, and may be used for pairing at most TOKEN_MAX_USES
times. Results submission re-validates the same token, so a client cannot
claim success without the backend having received + scored the report.
"""
import hashlib
import secrets
import time

from fastapi import APIRouter, Depends, HTTPException, Request
from bson import ObjectId

from app.core.db import get_db
from app.core.security import get_current_user, get_optional_user
from app.core.audit import log as audit_log

router = APIRouter(prefix="/diagnosis", tags=["diagnosis"])

TOKEN_TTL_SECONDS = 15 * 60
TOKEN_MAX_USES = 1
RESULT_MAX_USES = 3

STATES = {
    "awaiting_confirmation", "waiting_for_app", "token_generated",
    "paired", "in_progress", "results_received", "verification_completed",
    "failed", "expired",
}

TRANSITIONS = {
    "awaiting_confirmation": {"waiting_for_app", "failed", "expired"},
    "waiting_for_app": {"token_generated", "failed", "expired"},
    "token_generated": {"paired", "failed", "expired", "token_generated"},
    "paired": {"in_progress", "results_received", "failed", "expired"},
    "in_progress": {"results_received", "failed", "expired"},
    "results_received": {"verification_completed", "failed"},
    "verification_completed": set(),
    "failed": {"token_generated"},
    "expired": {"token_generated"},
}


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _now() -> float:
    return time.time()


def _oid(v):
    try:
        return ObjectId(v)
    except Exception:
        raise HTTPException(status_code=400, detail={"code": "bad_id", "message": "Invalid id"})


def _clean(doc):
    if doc is None:
        return None
    out = dict(doc)
    if "_id" in out:
        out["_id"] = str(out["_id"])
    for k in ("listing_id", "seller_id", "paired_device"):
        if isinstance(out.get(k), ObjectId):
            out[k] = str(out[k])
    return out


async def _load_session(db, session_id: str):
    try:
        oid = ObjectId(session_id)
    except Exception:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Session not found"})
    doc = await db.diagnosis_sessions.find_one({"_id": oid})
    if not doc:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Session not found"})
    return doc


def _check_token(session, raw_token: str, *, for_results: bool = False):
    if not raw_token or not isinstance(raw_token, str):
        raise HTTPException(status_code=401, detail={"code": "missing_token", "message": "Pairing token required"})
    if session.get("token_hash") != _hash(raw_token):
        raise HTTPException(status_code=401, detail={"code": "invalid_token", "message": "Invalid pairing token"})
    if _now() > float(session.get("token_expires_at", 0)):
        raise HTTPException(status_code=401, detail={"code": "expired_token", "message": "Pairing token has expired"})
    if for_results:
        if int(session.get("result_uses_remaining", 0)) <= 0:
            raise HTTPException(status_code=409, detail={"code": "no_result_slot", "message": "No result submission slots left"})
    else:
        if int(session.get("token_uses_remaining", 0)) <= 0:
            raise HTTPException(status_code=401, detail={"code": "token_used", "message": "Pairing token already used"})
    return True


def _transition(session, new_state: str):
    cur = session.get("state", "awaiting_confirmation")
    if new_state not in STATES:
        raise HTTPException(status_code=400, detail={"code": "bad_state", "message": f"Unknown state {new_state}"})
    if new_state != cur and new_state not in TRANSITIONS.get(cur, set()):
        raise HTTPException(status_code=409, detail={"code": "bad_transition", "message": f"Cannot move {cur} -> {new_state}"})
    return new_state


@router.post("/sessions", status_code=201)
async def create_session(body: dict, request: Request, user=Depends(get_current_user)):
    db = get_db()
    listing_id = body.get("listing_id")
    if not listing_id:
        raise HTTPException(status_code=400, detail={"code": "missing_listing", "message": "listing_id required"})
    oid = _oid(listing_id)
    listing = await db.listings.find_one({"_id": oid})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    if str(listing.get("seller_id")) != user["sub"] and user.get("role") not in ("admin", "inspector"):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not your listing"})
    category = body.get("category") or listing.get("category") or "mobile"
    existing = await db.diagnosis_sessions.find_one(
        {"listing_id": oid, "state": {"$in": ["token_generated", "waiting_for_app", "paired", "in_progress"]}},
        sort=[("created_at", -1)])
    if existing and existing.get("state") in ("token_generated", "waiting_for_app"):
        raw_token = secrets.token_urlsafe(16)
        now = _now()
        await db.diagnosis_sessions.update_one(
            {"_id": existing["_id"]},
            {"$set": {
                "state": "token_generated",
                "token_hash": _hash(raw_token),
                "token_expires_at": now + TOKEN_TTL_SECONDS,
                "token_uses_remaining": TOKEN_MAX_USES,
                "result_uses_remaining": RESULT_MAX_USES,
                "error": None,
                "updated_at": now,
            }}
        )
        fresh = await _load_session(db, str(existing["_id"]))
        return {
            "session": _clean(fresh),
            "token": raw_token,
            "expires_at": fresh["token_expires_at"],
            "expires_in_seconds": TOKEN_TTL_SECONDS,
            "instructions": "Open the SafeResale Diagnosis app and enter this token to connect your device.",
        }
    if existing and _now() <= float(existing.get("token_expires_at", 0)):
        return {"session": _clean(existing), "token": None,
                "message": f"An active session in state {existing.get('state')} exists."}

    raw_token = secrets.token_urlsafe(16)
    now = _now()
    doc = {
        "listing_id": oid,
        "seller_id": _oid(user["sub"]),
        "category": category,
        "state": "token_generated",
        "token_hash": _hash(raw_token),
        "token_expires_at": now + TOKEN_TTL_SECONDS,
        "token_uses_remaining": TOKEN_MAX_USES,
        "result_uses_remaining": RESULT_MAX_USES,
        "paired_device": None,
        "paired_at": None,
        "results": None,
        "results_received_at": None,
        "verified_at": None,
        "error": None,
        "device_info": None,
        "created_at": now,
        "updated_at": now,
    }
    res = await db.diagnosis_sessions.insert_one(doc)
    doc["_id"] = res.inserted_id
    await audit_log(action="diagnosis.session_created", target_type="diagnosis_session",
                   target_id=res.inserted_id, actor_id=_oid(user["sub"]), actor_role=user.get("role"),
                   ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"),
                   detail={"listing_id": str(oid), "category": category})
    return {
        "session": _clean(doc),
        "token": raw_token,
        "expires_at": doc["token_expires_at"],
        "expires_in_seconds": TOKEN_TTL_SECONDS,
        "instructions": "Open the SafeResale Diagnosis app and enter this token to connect your device.",
    }


@router.post("/pair")
async def pair(body: dict, request: Request):
    db = get_db()
    raw_token = body.get("token")
    if not raw_token:
        raise HTTPException(status_code=400, detail={"code": "missing_token", "message": "token required"})
    session = await db.diagnosis_sessions.find_one({"token_hash": _hash(raw_token)})
    if not session:
        raise HTTPException(status_code=401, detail={"code": "invalid_token", "message": "Invalid pairing token"})
    _check_token(session, raw_token, for_results=False)
    new_state = _transition(session, "paired")
    device_info = body.get("device") if isinstance(body.get("device"), dict) else None
    now = _now()
    await db.diagnosis_sessions.update_one(
        {"_id": session["_id"]},
        {"$set": {
            "state": new_state,
            "paired_device": body.get("device_id") or body.get("device_fingerprint"),
            "device_info": device_info,
            "paired_at": now,
            "token_uses_remaining": max(0, int(session.get("token_uses_remaining", 0)) - 1),
            "updated_at": now,
        }})
    await db.listings.update_one({"_id": session["listing_id"]},
                                {"$set": {"diagnosis_session_id": str(session["_id"]), "diagnosis_state": new_state,
                                          "updated_at": now}})
    await audit_log(action="diagnosis.paired", target_type="diagnosis_session", target_id=session["_id"],
                   detail={"device": device_info}, ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"))
    fresh = await _load_session(db, str(session["_id"]))
    return {"paired": True, "session": _clean(fresh),
            "listing_id": str(session["listing_id"]), "category": session.get("category")}


@router.post("/sessions/{session_id}/results")
async def submit_results(session_id: str, body: dict, request: Request):
    db = get_db()
    session = await _load_session(db, session_id)
    raw_token = body.get("token")
    _check_token(session, raw_token, for_results=True)
    if int(session.get("result_uses_remaining", 0)) <= 0:
        raise HTTPException(status_code=409, detail={"code": "no_result_slot", "message": "No result submission slots left"})
    new_state = _transition(session, "results_received")
    tests = body.get("tests")
    if not isinstance(tests, list):
        raise HTTPException(status_code=400, detail={"code": "bad_tests", "message": "tests must be a list"})
    device = body.get("device") if isinstance(body.get("device"), dict) else session.get("device_info")
    category = body.get("category") or session.get("category") or "mobile"
    now = _now()

    try:
        import sys
        from pathlib import Path
        sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "device_diagnostics"))
        from services.diagnostics import compute_diagnostics
        scored = compute_diagnostics({"device": device or {}, "category": category, "tests": tests,
                                     "skipped": bool(body.get("skipped", False))})
        score = int(scored.get("score", 0))
        scored_meta = {"basis": scored.get("basis"), "missing_penalty": scored.get("missing_penalty"),
                       "metrics": scored.get("metrics"), "report_version": scored.get("report_version")}
        had_error = False
    except Exception as e:
        score = None
        scored_meta = {"error": str(e)}
        had_error = True

    try:
        from app.api.verification import _normalize_diag_tests
        norm_tests = _normalize_diag_tests(tests)
    except Exception:
        norm_tests = tests

    doc = {
        "listing_id": session["listing_id"],
        "device": device or {},
        "category": category,
        "tests": norm_tests,
        "skipped": bool(body.get("skipped", False)),
        "score": score,
        "basis": scored_meta.get("basis"),
        "missing_penalty": scored_meta.get("missing_penalty"),
        "metrics": scored_meta.get("metrics"),
        "report_version": scored_meta.get("report_version"),
        "error": scored_meta.get("error"),
        "created_at": now,
        "source": "diagnosis_app",
        "session_id": str(session["_id"]),
    }
    res = await db.diagnostics.insert_one(doc)
    if not had_error and score is not None:
        await db.listings.update_one({"_id": session["listing_id"]},
                                    {"$set": {"diagnostic_score": score, "updated_at": now}})

    await db.diagnosis_sessions.update_one(
        {"_id": session["_id"]},
        {"$set": {
            "state": new_state,
            "results": {"diagnostic_id": str(res.inserted_id), "score": score, "had_error": had_error,
                        "tests_count": len(norm_tests)},
            "results_received_at": now,
            "result_uses_remaining": max(0, int(session.get("result_uses_remaining", 0)) - 1),
            "updated_at": now,
            "error": scored_meta.get("error"),
        }})
    await db.listings.update_one({"_id": session["listing_id"]},
                                {"$set": {"diagnosis_state": new_state, "diagnosis_session_id": str(session["_id"]),
                                          "updated_at": now}})
    await audit_log(action="diagnosis.results_received", target_type="diagnosis_session",
                   target_id=session["_id"], detail={"score": score, "tests": len(norm_tests),
                   "had_error": had_error}, ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"))
    fresh = await _load_session(db, str(session["_id"]))
    return {"received": True, "session": _clean(fresh), "diagnostic_id": str(res.inserted_id),
            "diagnostic_score": score, "had_error": had_error}



@router.post("/sessions/{session_id}/progress")
async def report_progress(session_id: str, body: dict, request: Request):
    db = get_db()
    session = await _load_session(db, session_id)
    raw_token = body.get("token")
    _check_token(session, raw_token, for_results=True)
    new_state = _transition(session, "in_progress")
    now = _now()
    await db.diagnosis_sessions.update_one(
        {"_id": session["_id"]},
        {"$set": {"state": new_state, "updated_at": now}}
    )
    await db.listings.update_one(
        {"_id": session["listing_id"]},
        {"$set": {"diagnosis_state": new_state, "updated_at": now}}
    )
    await audit_log(action="diagnosis.in_progress", target_type="diagnosis_session",
                   target_id=session["_id"], ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"))
    fresh = await _load_session(db, session_id)
    return {"status": "in_progress", "session": _clean(fresh)}

@router.post("/sessions/{session_id}/regenerate-token")
async def regenerate_token(session_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    session = await _load_session(db, session_id)
    if str(session.get("seller_id")) != user["sub"] and user.get("role") not in ("admin", "inspector"):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not your session"})
    cur = session.get("state", "token_generated")
    if cur not in ("token_generated", "expired", "failed"):
        raise HTTPException(status_code=409, detail={"code": "bad_state",
                            "message": f"Cannot regenerate token from state {cur}"})
    raw_token = secrets.token_urlsafe(16)
    now = _now()
    await db.diagnosis_sessions.update_one(
        {"_id": session["_id"]},
        {"$set": {
            "state": "token_generated",
            "token_hash": _hash(raw_token),
            "token_expires_at": now + TOKEN_TTL_SECONDS,
            "token_uses_remaining": TOKEN_MAX_USES,
            "result_uses_remaining": RESULT_MAX_USES,
            "error": None,
            "updated_at": now,
        }})
    await audit_log(action="diagnosis.token_regenerated", target_type="diagnosis_session",
                   target_id=session["_id"], ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"))
    fresh = await _load_session(db, session_id)
    return {"session": _clean(fresh), "token": raw_token, "expires_at": now + TOKEN_TTL_SECONDS,
            "expires_in_seconds": TOKEN_TTL_SECONDS,
            "instructions": "Open the SafeResale Diagnosis app and enter this token to connect your device."}


@router.get("/session-by-token")
async def get_session_by_token(token: str, request: Request):
    if not token or not isinstance(token, str):
        raise HTTPException(status_code=400, detail={"code": "missing_token", "message": "Token required"})
    db = get_db()
    session = await db.diagnosis_sessions.find_one({"token_hash": _hash(token)})
    if not session:
        raise HTTPException(status_code=404, detail={"code": "invalid_token", "message": "Invalid pairing token"})
    if _now() > float(session.get("token_expires_at", 0)):
        raise HTTPException(status_code=410, detail={"code": "expired_token", "message": "Pairing token expired"})
    return {
        "session": _clean(session),
        "listing_id": str(session["listing_id"]),
        "category": session.get("category", "laptop")
    }


@router.get("/sessions/{session_id}")
async def get_session(session_id: str, request: Request, token: str | None = None,
                      user=Depends(get_optional_user)):
    db = get_db()
    session = await _load_session(db, session_id)
    is_owner = user is not None and (str(session.get("seller_id")) == user["sub"] or user.get("role") in ("admin", "inspector"))
    is_token_holder = (token is not None and session.get("token_hash") == _hash(token)
                       and _now() <= float(session.get("token_expires_at", 0)))
    if not (is_owner or is_token_holder):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not authorized"})
    out = _clean(session)
    if out.get("state") not in ("verification_completed", "failed", "expired") and _now() > float(session.get("token_expires_at", 0)):
        await db.diagnosis_sessions.update_one({"_id": session["_id"]}, {"$set": {"state": "expired", "updated_at": _now()}})
        out["state"] = "expired"
    return {"session": out}


@router.post("/sessions/{session_id}/verify")
async def verify_session(session_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    session = await _load_session(db, session_id)
    if str(session.get("seller_id")) != user["sub"] and user.get("role") not in ("admin", "inspector"):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not your session"})
    if session.get("state") != "results_received":
        raise HTTPException(status_code=409, detail={"code": "not_ready",
                            "message": "Cannot verify until results are received"})
    new_state = _transition(session, "verification_completed")
    now = _now()
    await db.diagnosis_sessions.update_one({"_id": session["_id"]},
                                          {"$set": {"state": new_state, "verified_at": now, "updated_at": now}})
    await db.listings.update_one({"_id": session["listing_id"]},
                                {"$set": {"diagnosis_state": new_state, "diagnosis_verified": True, "updated_at": now}})
    await audit_log(action="diagnosis.verification_completed", target_type="diagnosis_session",
                   target_id=session["_id"], ip=request.client.host if request.client else None,
                   request_id=request.headers.get("X-Request-ID"))
    fresh = await _load_session(db, session_id)
    return {"verified": True, "session": _clean(fresh)}


@router.get("/listings/{listing_id}/status")
async def listing_status(listing_id: str, user=Depends(get_current_user)):
    db = get_db()
    oid = _oid(listing_id)
    listing = await db.listings.find_one({"_id": oid})
    if not listing:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Listing not found"})
    if str(listing.get("seller_id")) != user["sub"] and user.get("role") not in ("admin", "inspector"):
        raise HTTPException(status_code=403, detail={"code": "forbidden", "message": "Not your listing"})
    sess = await db.diagnosis_sessions.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    return {"listing_id": listing_id, "diagnosis_state": listing.get("diagnosis_state"),
            "diagnosis_session_id": listing.get("diagnosis_session_id"),
            "diagnostic_score": listing.get("diagnostic_score"),
            "session": _clean(sess) if sess else None}