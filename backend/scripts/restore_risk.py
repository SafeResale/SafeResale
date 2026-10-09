"""Restore the ORIGINAL risk data for existing listings.

The image re-analysis backfill re-ran the pipeline and, because the stub vision
provider emits no defect detections, recomputed LOWER risk scores (e.g. 82 ->
28.5) and persisted them over the previous values.

This script restores each listing's risk snapshot from its EARLIEST stored
risk_scores row (the original pipeline output), removes the newer rows that were
written on top of it, and persists the derived trust verdict. The Risk Score
pipeline itself is untouched — we only restore what it originally produced.

Usage (from backend/):  python -m scripts.restore_risk
"""
import argparse
import asyncio

from bson import ObjectId

from app.core.db import get_db
from app.api.listings import _trust_rating


async def main():
    ap = argparse.ArgumentParser(description="Restore original per-listing risk values")
    ap.add_argument("--dry-run", action="store_true", help="Only show what would be restored")
    args = ap.parse_args()

    db = get_db()
    lids = await db.risk_scores.distinct("listing_id")
    print(f"Restoring risk for {len(lids)} listings…")

    restored, deleted_rows, failed = 0, 0, []
    for lid in lids:
        rows = list(await db.risk_scores.find({"listing_id": lid}).sort("created_at", 1).to_list(100))
        if not rows:
            continue
        original = rows[0]  # earliest = pre-backfill pipeline output
        mine = rows[1:]     # newer rows written by the image re-analysis backfill
        snapshot = {
            "adjusted_score": original.get("adjusted_score"),
            "raw_score": original.get("raw_score"),
            "badge": original.get("badge"),
        }
        doc = await db.listings.find_one({"_id": lid})
        if not doc:
            continue
        trust = _trust_rating({**doc, "risk": snapshot})
        try:
            if args.dry_run:
                print(f"  {str(lid)}: would restore risk={snapshot['adjusted_score']} "
                      f"trust={trust['band']}({trust['score']}), remove {len(mine)} newer row(s)")
                restored += 1
                continue
            await db.listings.update_one(
                {"_id": lid},
                {"$set": {"risk": snapshot, "trust": trust}},
            )
            if mine:
                ids = [r["_id"] for r in mine]
                res = await db.risk_scores.delete_many({"listing_id": lid, "_id": {"$in": ids}})
                deleted_rows += res.deleted_count
            # restore the original decision too (strip rows written on top of it)
            dec_rows = list(await db.decisions.find({"listing_id": lid}).sort("created_at", 1).to_list(100))
            if len(dec_rows) > 1:
                extra = [r["_id"] for r in dec_rows[1:]]
                await db.decisions.delete_many({"listing_id": lid, "_id": {"$in": extra}})
            restored += 1
            print(f"  {str(lid)}: restored risk={snapshot['adjusted_score']} trust={trust['band']}({trust['score']})")
        except Exception as e:  # noqa: BLE001
            failed.append((str(lid), repr(e)))

    print(f"done: {restored} restored, {deleted_rows} newer risk rows removed, {len(failed)} failed")
    for lid, err in failed:
        print(f"  {lid}: {err}")


if __name__ == "__main__":
    asyncio.run(main())