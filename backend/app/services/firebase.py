"""Firebase ID-token verification using the canonical firebase-admin SDK.

The service reads the Firebase service-account JSON (gitignored path from
FIREBASE_SERVICE_ACCOUNT_FILE) and calls firebase_admin.auth.verify_id_token,
which handles JWKS caching, RS256 verification, and expiry checks for us. No
service-account JSON is ever committed; tests inject a stub verifier.

Because firebase-admin is blocking, wrappers run in a worker thread
(anyio.to_thread / asyncio.to_thread) so the event loop stays free.
"""
from __future__ import annotations

import os
from typing import Any, Callable, Awaitable

from app.core.config import settings

# Injectables for hermetic tests (no live Firebase network calls).
Verifier = Callable[[str], Awaitable[dict]]
_default_verifier: Verifier | None = None


def _load_app():
    """Return a configured firebase_admin App (singleton)."""
    import firebase_admin
    from firebase_admin import credentials as fb_creds

    app = firebase_admin.get_app() if firebase_admin._apps else None
    if app is not None:
        return app
    path = settings.firebase_service_account_file
    if not path or not os.path.isfile(path):
        raise RuntimeError(
            "FIREBASE_SERVICE_ACCOUNT_FILE is not set or does not exist; "
            "place a Firebase service-account JSON there (gitignored)."
        )
    cred = fb_creds.Certificate(path)
    return firebase_admin.initialize_app(cred)


def set_verifier(fn: Verifier | None) -> None:
    """Override the default verifier (tests use this to stay hermetic)."""
    global _default_verifier
    _default_verifier = fn


async def verify_id_token(id_token: str, project_id: str | None = None) -> dict:
    """Verify a Firebase ID token and return its claims.

    Uses the injected stub when set (tests), otherwise the firebase-admin SDK
    running in a worker thread. Raises ValueError on invalid/expired tokens.
    """
    if _default_verifier is not None:
        return await _default_verifier(id_token)
    import asyncio
    from firebase_admin import auth as fb_auth

    _load_app()
    try:
        claims = await asyncio.to_thread(fb_auth.verify_id_token, id_token)
    except Exception as e:  # noqa: BLE001 - SDK raises ValueError subclasses
        raise ValueError("Invalid Firebase ID token: " + str(e))
    claims.setdefault("provider", "firebase")
    return claims