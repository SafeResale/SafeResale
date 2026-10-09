"""End-to-end pipeline test for a single listing: re-run image analysis,
recompute risk + decision, then summarize what came out. Mirrors the app flow
(POST run-vision then compute-risk). The empty-detection guard keeps existing
detections intact, so restored original risk values are not clobbered.

Usage (from backend/):  python -m scripts.test_listing <listing_id>
"""
import argparse
import asyncio

from bson import ObjectId

from app.core.db import get_db
from app.api.verification import analyze_images_for_listing, score_listing
from app.services.image_quality import refresh_server_quality


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("listing_id")
    args = ap.parse_args()

    db = get_db()
    lid = args.listing_id
    vision = await analyze_images_for_listing(lid)
    m6 = await refresh_server_quality(lid)
    payload, listing = await score_listing(lid)

    images = await db.listing_images.find({"listing_id": ObjectId(lid)}).to_list(100)
    shas = [i.get("sha256") for i in images if i.get("sha256")]
    shifts = await db.listings.find_one({"_id": ObjectId(lid)})
    trust = (shifts or {}).get("trust") or {}

    print(f"listing: {lid}")
    print(f"vision:    provider simulated={vision['simulated']} detections={len(vision['detections'])}")
    print(f"images:    {len(images)} captured, {len(set(shas))} unique content hashes, angles={sorted({i.get('angle') for i in images})}")
    print(f"m6:        {m6}")
    print(f"decision:  {payload['decision']['status']} ({payload['decision'].get('reason_code')})")
    for s in payload["decision"].get("hard_stops", []):
        print(f"   stop: {s['code']} — {s['message']}")
    print(f"risk:      adjusted={payload['adjusted_score']} raw={payload.get('raw_score')}")
    print(f"trust:     score={trust.get('score')} band={trust.get('band')} label={trust.get('label')}")
    print(f"diagnostic_score: {(shifts or {}).get('diagnostic_score')}")


if __name__ == "__main__":
    asyncio.run(main())