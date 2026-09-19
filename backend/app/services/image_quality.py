"""M6 server-side image quality: OpenCV check with WSL fallback.

The backend on Windows cannot import cv2 (models live in WSL), so each call
prefers the in-process analyzer and delegates to the WSL ML venv otherwise.
"""
from __future__ import annotations

import sys
from pathlib import Path

from bson import ObjectId

from app.core.storage import storage


def server_quality_for_image(image_path: str, stored_key: str = "") -> dict:
    """One M6 quality report. Raises on hard failures (caller decides)."""
    try:
        import cv2  # noqa: F401
    except ImportError:
        return _analyze_via_wsl(image_path, stored_key)
    return _analyze_in_process(image_path)


def _analyze_in_process(image_path: str) -> dict:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m6_image_quality" / "scripts"))
    from common import analyze_image_quality  # type: ignore
    return analyze_image_quality(image_path)


def _analyze_via_wsl(image_path: str, stored_key: str) -> dict:
    from app.services import wsl
    rep = wsl.run_bridge(
        "quality.py",
        {"images": [{"image": wsl.to_mnt(Path(image_path).resolve()), "image_id": stored_key}]},
        timeout=90,
    )
    if not rep:
        raise RuntimeError("WSL quality check returned no results")
    return rep[0]["server_quality"]


async def refresh_server_quality(listing_id: str) -> dict:
    """(Re)run M6 on every stored photo and update the image docs.

    Used to repair listings uploaded while the server had no cv2 available.
    """
    from app.core.db import get_db
    db = get_db()
    oid = ObjectId(listing_id)
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    updated = 0
    for img in images:
        key = img.get("stored_key")
        if not key:
            continue
        p = storage.base / key
        if not p.exists():
            continue
        q = server_quality_for_image(str(p), key)
        merged = {**(img.get("client_quality") or {}), "server": q, "passed": q.get("passed", True)}
        await db.listing_images.update_one(
            {"_id": img["_id"]},
            {"$set": {"server_quality": q, "quality": merged}},
        )
        updated += 1
    return {"listing_id": listing_id, "updated": updated}