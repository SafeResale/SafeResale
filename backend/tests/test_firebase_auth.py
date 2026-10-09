"""Hermetic tests for POST /auth/firebase (no network, no credentials).

We stub the verifier (app.services.firebase._default_verifier) so tests never
touch Firebase or the service-account JSON. The endpoint contract:
  POST /auth/firebase {id_token} ->
    201/200: {"access_token", "refresh_token", "user": {id,email,name,role}}
    401 invalid token -> {"code": "invalid_firebase_token", ...}
"""
import asyncio

import pytest
from httpx import AsyncClient, ASGITransport

from app.core.config import settings
from app.main import app
from app.services import firebase as fb_svc

# Helper to build fake claims without touching firebase_admin.
def _claims(uid="u_123", email="buyer@example.com", name="Buyer", verified=True):
    return {
        "sub": uid, "firebase_uid": uid, "uid": uid, "user_id": uid,
        "email": email, "name": name,
        "email_verified": verified, "aud": settings.firebase_project_id or "test-project",
        "iss": "https://securetoken.google.com/test-project",
        "exp": 9999999999, "iat": 1000000000,
    }

@pytest.fixture(autouse=True)
def _stub_verifier():
    """Inject a fake async verifier that returns claims for a known token."""
    async def _fake(token: str) -> dict:
        if token == "valid-token":
            return _claims()
        raise ValueError("Invalid Firebase ID token: stub reject")
    prev = fb_svc._default_verifier
    fb_svc.set_verifier(_fake)
    yield
    fb_svc.set_verifier(prev)

async def _post(payload: dict):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        return await ac.post("/auth/firebase", json=payload)

@pytest.mark.asyncio
async def test_firebase_login_success():
    r = await _post({"id_token": "valid-token"})
    assert r.status_code == 200
    data = r.json()
    assert "access_token" in data and "refresh_token" in data
    assert data["user"]["email"] == "buyer@example.com"
    assert data["user"]["role"] in ("seller", "admin")

@pytest.mark.asyncio
async def test_firebase_login_invalid_token():
    r = await _post({"id_token": "bad-token"})
    assert r.status_code == 401
    body = r.json()
    assert body["detail"]["code"] == "invalid_firebase_token"

@pytest.mark.asyncio
async def test_firebase_login_missing_id_token():
    r = await _post({})
    assert r.status_code == 422
