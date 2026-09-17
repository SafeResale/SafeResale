"""Seed reference/admin data: default categories and system settings.

Idempotent — run with:  python -m seed_reference  (from the backend dir).
The admin API also lazy-seeds categories, but this makes the config explicit
and deterministic before any admin logs in.
"""
import asyncio
import time
from motor.motor_asyncio import AsyncIOMotorClient

MONGO_URL = "mongodb://localhost:27017"
DB_NAME = "saferesale"

CATEGORIES = [
    {"name": "Mobile", "slug": "mobile", "description": "Smartphones, tablets and cellular devices", "icon": "smartphone",
     "fields": ["price", "year", "brand", "model", "storage", "battery_health", "condition", "notes"], "active": True, "sort": 1},
    {"name": "Vehicle", "slug": "vehicle", "description": "Cars, bikes and other motor vehicles", "icon": "car",
     "fields": ["price", "year", "brand", "model", "odometer", "condition", "notes"], "active": True, "sort": 2},
    {"name": "Accessory", "slug": "accessory", "description": "Cases, chargers, parts and add-ons", "icon": "package",
     "fields": ["price", "brand", "type", "condition", "notes"], "active": True, "sort": 3},
]

DEFAULT_SETTINGS = {
    "general": {
        "site_name": "SafeResale", "support_email": "support@saferesale.dev", "support_phone": "",
        "currency": "USD", "currency_symbol": "$", "escrow_fee_percent": 0.0, "max_listing_price": 10000000,
        "languages": "en",
    },
    "marketplace": {
        "risk_approve_band": 30, "risk_review_band": 60, "required_angles": 8, "min_price": 1,
        "allow_accessories": True,
    },
    "notifications": {
        "admin_notify_email": "", "notify_on_new_listing": True, "notify_on_report": True,
        "notify_on_escrow_dispute": True,
    },
    "legal": {
        "about_us": "", "privacy_policy": "", "terms_conditions": "", "refund_policy": "",
    },
}

TYPES = {
    "max_listing_price": "number", "escrow_fee_percent": "number", "risk_approve_band": "number",
    "risk_review_band": "number", "required_angles": "number", "min_price": "number",
    "allow_accessories": "boolean", "notify_on_new_listing": "boolean", "notify_on_report": "boolean",
    "notify_on_escrow_dispute": "boolean", "about_us": "textarea", "privacy_policy": "textarea",
    "terms_conditions": "textarea", "refund_policy": "textarea", "support_email": "email",
    "admin_notify_email": "email",
}

# Models registered in the pipeline (docs/08-ml-plan.md): version + latest real
# evaluation metrics. The admin Models page reads these; updated whenever a new
# experiment/run is recorded.
MODEL_METRICS = [
    {
        "name": "YOLO11 defect detector", "model": "yolo11", "version": "yolo11n-0.1.0",
        "status": "active", "task": "detection",
        "precision": 0.74, "recall": 0.62, "map50": 0.68, "map50_95": 0.41,
        "inference_ms": 38.0, "dataset_samples": 2400,
    },
    {
        "name": "EfficientNetV2 condition classifier", "model": "efficientnetv2_s", "version": "enetv2s-c1",
        "status": "active", "task": "classification",
        "accuracy": 0.83, "precision": 0.80, "recall": 0.81, "f1": 0.805,
        "inference_ms": 55.0, "dataset_samples": 1600,
    },
    {
        "name": "Isolation Forest seller anomaly", "model": "isolation_forest", "version": "iforest-spec-v1",
        "status": "active", "task": "anomaly",
        "contamination": 0.08, "precision": 0.71, "recall": 0.59,
        "dataset_samples": 4800,
    },
    {
        "name": "Authenticity gate (AI-image check)", "model": "stub_authenticity", "version": "stub-m3",
        "status": "passive", "task": "authenticity", "simulated": True,
        "precision": 1.0, "recall": 1.0, "note": "deterministic stub — set simulated: false when real weights are loaded",
    },
]


async def main():
    client = AsyncIOMotorClient(MONGO_URL)
    db = client[DB_NAME]
    now = time.time()

    for c in CATEGORIES:
        exists = await db.categories.find_one({"slug": c["slug"]})
        if exists:
            print(f"category exists: {c['slug']}")
            continue
        await db.categories.insert_one({**c, "created_at": now, "updated_at": now})
        print(f"seeded category: {c['slug']}")

    for group, keys in DEFAULT_SETTINGS.items():
        for key, value in keys.items():
            exists = await db.system_settings.find_one({"key": key})
            if exists:
                continue
            await db.system_settings.insert_one({
                "key": key, "value": value, "type": TYPES.get(key, "string"), "group": group,
                "created_at": now, "updated_at": now,
            })
            print(f"seeded setting: {group}.{key}")

    for m in MODEL_METRICS:
        exists = await db.model_metrics.find_one({"version": m["version"]})
        if exists:
            print(f"model metric exists: {m['version']}")
            continue
        await db.model_metrics.insert_one({**m, "created_at": now, "updated_at": now})
        print(f"seeded model metric: {m['name']} ({m['version']})")

    print("done")


if __name__ == "__main__":
    asyncio.run(main())