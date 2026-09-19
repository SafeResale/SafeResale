"""Backfill: re-run the production image pipeline + risk pipeline for existing
listings and persist a fresh trust verdict on each product doc.

Trust Badge classification (separate from Risk Score semantics):
  1-30 -> Poor, 31-70 -> Moderate, 71-100 -> Good  (higher = better).

For every existing marketplace product:
  1. load stored product images
  2. re-process them through the existing image-analysis pipeline
     (detect defects + classify condition)
  3. recompute risk with the existing, untouched risk pipeline
  4. persist the risk snapshot + trust verdict on the listing doc
     so database, API and frontend all agree.

Usage (from backend/):  python -m scripts.backfill_trust  [--all]  [--no-images]
"""
import argparse
import asyncio

from app.core.db import get_db
from app.api.verification import analyze_images_for_listing, score_listing


async def main():
    ap = argparse.ArgumentParser(description="Recompute risk + trust for existing listings")
    ap.add_argument("--all", action="store_true", help="Process every listing, not just marketplace ones")
    ap.add_argument("--no-images", action="store_true", help="Skip image re-analysis (risk recompute only)")
    args = ap.parse_args()

    db = get_db()
    if args.all:
        cursor = db.listings.find({}, {"_id": 1, "status": 1})
    else:
        cursor = db.listings.find(
            {
                "$or": [
                    {"status": {"$in": ["published", "approved"]}},
                    {"risk": {"$exists": True}},
                    {"diagnostic_score": {"$exists": True}},
                    {"is_fit_to_show": {"$exists": True}},
                ]
            },
            {"_id": 1, "status": 1},
        )
    ids = [str(doc["_id"]) async for doc in cursor]
    print(f"Re-processing {len(ids)} listings…")

    ok, failed = 0, []
    for i, lid in enumerate(ids, 1):
        try:
            if not args.no_images:
                await analyze_images_for_listing(lid)  # re-check stored product images
            payload, _ = await score_listing(lid)       # risk pipeline (untouched) + trust verdict
            ok += 1
            print(f"  [{i}] {lid}: risk={payload['adjusted_score']} decision={payload['decision']['status']}")
        except Exception as e:  # noqa: BLE001
            failed.append((lid, repr(e)))
        if i % 10 == 0 or i == len(ids):
            print(f"  {i}/{len(ids)}")

    print(f"done: {ok} updated, {len(failed)} failed")
    for lid, err in failed:
        print(f"  {lid}: {err}")


if __name__ == "__main__":
    asyncio.run(main())