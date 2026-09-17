"""Shared (de)serialization helpers for API payloads.

- s(): deep-convert ObjectIds to strings (Mongo docs -> JSON-safe responses)
- strip_password(): drop password_hash so users never leak hashes
- parse_id(): strict ObjectId parse with a 422 error for the API layer
"""
from bson import ObjectId
from fastapi import HTTPException


def s(value):
    """Deep-stringify BSON ObjectIds in dicts/lists (returns scalars unchanged)."""
    if isinstance(value, ObjectId):
        return str(value)
    if isinstance(value, dict):
        return {k: s(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [s(v) for v in value]
    return value


def strip_password(doc: dict) -> dict:
    """Return a user document without the password hash (safe for responses)."""
    out = dict(doc)
    out.pop("password_hash", None)
    return out


def public_user(doc: dict) -> dict:
    """Serialize a user document for the admin/users API."""
    return s(strip_password(doc))


def parse_id(raw: str, *, field: str = "id") -> ObjectId:
    """Parse a string as ObjectId or raise 422 in the FastAPI error shape."""
    try:
        return ObjectId(raw)
    except Exception:
        raise HTTPException(status_code=422, detail={"code": "bad_id", "message": f"Invalid {field}"})