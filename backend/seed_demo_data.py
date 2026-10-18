import time
import pymongo
from bson import ObjectId

client = pymongo.MongoClient("mongodb://localhost:27017")
db = client["saferesale"]

inspector = db.users.find_one({"role": "inspector"})
buyer = db.users.find_one({"email": "buyer@example.com"}) or db.users.find_one({"role": "seller"})
seller = db.users.find_one({"role": "admin"})
listing = db.listings.find_one({"title": "Test iPhone 2"}) or db.listings.find_one({})

now = time.time()

# 1. Update first inspection as in_progress
insp1_id = ObjectId("6acd11da1a4cdee2c623153d")
db.inspections.update_one(
    {"_id": insp1_id},
    {"$set": {
        "inspector_id": inspector["_id"] if inspector else None,
        "status": "in_progress",
        "timeline": [
            {"status": "requested", "label": "Requested", "timestamp": now - 7200},
            {"status": "assigned", "label": "Inspector Assigned", "description": "Certified Inspector Rahul accepted job", "timestamp": now - 5400},
            {"status": "travelling", "label": "En Route 🛵", "description": "Inspector is on the way to Indiranagar", "timestamp": now - 3600},
            {"status": "arrived", "label": "Arrived 📍", "description": "Inspector arrived at seller location", "timestamp": now - 1800},
            {"status": "in_progress", "label": "Testing in Progress ⚙️", "description": "Hardware bench & AI diagnostic underway", "timestamp": now}
        ],
        "findings": {
            "overall_condition": "good",
            "screen_condition": "good",
            "battery_condition": "good",
            "body_condition": "fair",
            "camera_condition": "good",
            "connectivity_ok": True,
            "biometrics_ok": True,
            "defects_found": ["Minor scratch on bezel"],
            "notes": "Device powers on, genuine OLED display verified, FaceID responsive, battery capacity 89%.",
            "calculated_score": 84.0
        },
        "score": 84.0,
        "updated_at": now
    }}
)

# 2. Update second inspection as requested (pending queue)
insp2_id = ObjectId("6acd06db1a4cdee2c623152f")
db.inspections.update_one(
    {"_id": insp2_id},
    {"$set": {
        "status": "requested",
        "preferred_date": "Tomorrow morning",
        "note": "Please check car engine sound and original RC book.",
        "updated_at": now
    }}
)

# 3. Create a completed inspection demo
db.inspections.update_one(
    {"listing_id": ObjectId("6aa82435b78c3844797f7992")},
    {"$set": {
        "listing_id": ObjectId("6aa82435b78c3844797f7992"),
        "buyer_id": buyer["_id"] if buyer else None,
        "seller_id": seller["_id"] if seller else None,
        "inspector_id": inspector["_id"] if inspector else None,
        "status": "completed",
        "order": 3,
        "created_at": now - 86400,
        "updated_at": now - 3600,
        "score": 92.5,
        "timeline": [
            {"status": "requested", "label": "Requested", "timestamp": now - 86400},
            {"status": "assigned", "label": "Inspector Assigned", "timestamp": now - 80000},
            {"status": "travelling", "label": "En Route", "timestamp": now - 50000},
            {"status": "arrived", "label": "Arrived", "timestamp": now - 20000},
            {"status": "in_progress", "label": "Testing", "timestamp": now - 10000},
            {"status": "completed", "label": "Verified & Certified ✅", "description": "AI Certificate generated with score 92.5", "timestamp": now - 3600}
        ],
        "findings": {
            "overall_condition": "excellent",
            "screen_condition": "excellent",
            "battery_condition": "good",
            "body_condition": "excellent",
            "camera_condition": "excellent",
            "connectivity_ok": True,
            "biometrics_ok": True,
            "defects_found": [],
            "notes": "Pristine physical condition, 100% genuine components, clean battery cycles.",
            "calculated_score": 92.5
        }
    }},
    upsert=True
)

print("Demo inspection records successfully prepared.")
