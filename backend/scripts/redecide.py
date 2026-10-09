"""Recompute only the DECISION for listings blocked by the old duplicate-image
check (HARD_DUPLICATE_IMAGES), which false-positived because it read a hash
field that is never populated. The decision now uses each image's real sha256
content hash, so distinct angles no longer collide.

This ONLY rewrites decision rows that reference HARD_DUPLICATE_IMAGES. It does
NOT touch risk_scores, detections, images, or listing status — the restored
original risk values stay byte-for-byte intact.

Usage (from backend/):  python -m scripts.redecide [--all]
"""
import argparse
import asyncio

from bson import ObjectId

from app.core.db import get_db
from app.services.decision import decide_with_stops, badge_for

TRIGGER = "HARD_DUPLICATE_IMAGES"


async def main():
    ap = argparse.ArgumentParser(description="Recompute decisions blocked by the old duplicate-image check")
    ap.add_argument("--all", action="store_true", help="Process every listing (not only HARD_DUPLICATE_IMAGES)")
    args = ap.parse_args()

    db = get_db()
    lids = await db.risk_scores.distinct("listing_id")
    print(f"Scanning {len(lids)} listings…")

    fixed = skipped = failed = 0
    for lid in lids:
        dec = await db.decisions.find_one({"listing_id": lid}, sort=[("created_at", -1)])
        if not dec:
            continue
        reasons = [r.get("code") for r in dec.get("reasons", [])] + [dec.get("reason_code")]
        if not args.all and TRIGGER not in reasons:
            continue
        risk = await db.risk_scores.find_one({"listing_id": lid}, sort=[("created_at", 1)])
        listing = await db.listings.find_one({"_id": lid})
        if not risk or not listing:
            skipped += 1
            continue
        images = await db.listing_images.find({"listing_id": lid}).to_list(100)
        dets = await db.detections.find({"listing_id": lid}).to_list(100)
        flat = []
        for d in dets:
            flat.extend(d.get("detections", [])) if "detections" in d else flat.append(d)
        new = decide_with_stops(risk, listing, images, flat)
        new["badge"] = badge_for(new["status"], listing.get("status", "verifying"))
        update = {
            "status": new["status"],
            "reason_code": new["reason_code"],
            "reasons": new["reasons"],
            "hard_stops": new.get("hard_stops", []),
            "badge": new["badge"],
            "fixed_at": __import__("time").time(),
        }
        await db.decisions.update_one({"_id": dec["_id"]}, {"$set": update})
        fixed += 1
        print(f"  {str(lid)}: {dec.get('reason_code')} -> {new['reason_code']} ({new['status']})")
        for s in new.get("hard_stops", []):
            print(f"      stop: {s['code']} — {s['message']}")

    print(f"done: {fixed} decisions recomputed, {skipped} skipped, {failed} failed")


if __name__ == "__main__":
    asyncio.run(main())