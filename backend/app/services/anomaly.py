"""M4 price anomaly + M5 seller anomaly (stubs that satisfy contract, real IF later)."""
from collections import defaultdict
from app.core.db import get_db

# M4: category/brand/model median
async def price_anomaly(listing: dict) -> dict:
    db = get_db()
    key = f"{listing.get('category','')}:{listing.get('brand','')}:{listing.get('model','')}".lower()
    stat = await db.price_stats.find_one({"key": key})
    if not stat or not stat.get("median_price"):
        return {"deviation_ratio": 0, "median": None, "key": key}
    median = stat["median_price"]
    price = float(listing.get("price", 0))
    ratio = abs(price - median) / median if median else 0
    return {"deviation_ratio": round(ratio, 3), "median": median, "key": key, "price": price}

async def seller_signals(seller_id, listing: dict | None = None) -> list[dict]:
    db = get_db()
    from bson import ObjectId
    # listing frequency last 7d
    import time
    week = time.time() - 7*24*3600
    cnt = await db.listings.count_documents({"seller_id": ObjectId(seller_id), "created_at": {"$gte": week}})
    signals = []
    if cnt > 10:
        signals.append({"signal": "high_listing_frequency", "value": cnt, "severity": 60, "explanation": f"{cnt} listings in 7 days"})
    # price deviation
    if listing:
        pa = await price_anomaly(listing)
        if pa["deviation_ratio"] > 0.5:
            signals.append({"signal": "price_anomaly", "value": pa["deviation_ratio"], "severity": 55, "explanation": f"Price deviates {pa['deviation_ratio']*100:.0f}% from median {pa['median']}"})
    # duplicate images stub — real M7 later
    return signals
