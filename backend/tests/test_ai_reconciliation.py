"""Hermetic tests for item identification, AI verification, and reconciliation."""
import pytest
from app.services.item_identification import identify_item
from app.services.ai_reconciliation import reconcile_assessment


def test_item_identification_no_hallucination():
    # Only generic image / title, no verified telemetry
    listing = {"title": "Selling black phone", "category": "mobile"}
    res = identify_item(listing, images=[{"image": "photo1.jpg"}])

    # Must NOT hallucinate a specific model name (e.g. iPhone 15 Pro)
    assert res["verification_status"] == "unverified"
    assert res["brand"] is None
    assert res["model"] is None
    assert res["needs_clarification"] is True
    assert "cannot be reliably determined" in " ".join(res["unknown_info"])


def test_item_identification_distinguishes_visually_similar():
    # User claims iPhone 13; visually ambiguous with iPhone 14
    listing = {"title": "iPhone 13 128GB", "category": "mobile", "brand": "Apple", "model": "iPhone 13"}
    res = identify_item(listing)

    assert res["verification_status"] == "seller_declared"
    assert res["similar_models_warning"] is not None
    assert "iPhone 13" in res["similar_models_warning"]
    assert "iPhone 14" in res["similar_models_warning"]


def test_item_identification_confirmed_by_diagnostics():
    # Paired device supplies hardware diagnostic info
    listing = {"title": "My Pixel", "category": "mobile"}
    diagnostic = {
        "category": "mobile",
        "device": {"model": "Pixel 7 Pro", "manufacturer": "Google", "os_version": "Android 14", "sdk": 34},
    }
    res = identify_item(listing, diagnostic=diagnostic)

    assert res["verification_status"] == "confirmed_by_diagnostics"
    assert res["brand"] == "Google"
    assert res["model"] == "Pixel 7 Pro"
    assert res["confidence"] >= 0.95
    assert any("Pixel 7 Pro" in f for f in res["confirmed_facts"])


def test_reconciliation_distinguishes_visual_vs_hardware_touch():
    # Visual analysis sees a crack, but hardware touch digitizer is 100% passed
    listing = {"title": "iPhone 13", "category": "mobile", "brand": "Apple", "model": "iPhone 13"}
    detections = [
        {"class": "crack", "confidence": 0.85, "image": "front.jpg"}
    ]
    diagnostic = {
        "score": 88,
        "device": {"model": "iPhone14,5", "manufacturer": "Apple"},
        "tests": [
            {"id": "touch", "name": "Touch screen", "status": "passed"},
            {"id": "display", "name": "Display panel", "status": "passed"},
        ],
    }

    report = reconcile_assessment(listing, detections=detections, diagnostic=diagnostic)

    # Distinguishes surface crack from internal hardware digitizer functionality
    assert len(report["visual_condition"]["verified_defects"]) == 1
    assert report["device_health"]["diagnostics_performed"] is True
    assert "Visual surface damage" in report["device_health"]["display_reconciliation"]
    assert "digitizer and touch display hardware are functionally intact" in report["device_health"]["display_reconciliation"]


def test_reconciliation_explicit_when_untested():
    # Device was NOT diagnosed by Diagnosis app
    listing = {"title": "Laptop", "category": "laptop", "brand": "Dell", "model": "XPS 13"}

    report = reconcile_assessment(listing, diagnostic=None)

    # Must explicitly state device has not been tested, NEVER fabricate score
    assert report["device_health"]["diagnostics_performed"] is False
    assert report["device_health"]["score"] is None
    assert "Device has not been tested" in report["device_health"]["status_message"]
    assert "Hardware functionality" in " ".join(report["unverified_claims"])


def test_reconciliation_detects_contradiction_seller_vs_visual():
    listing = {"title": "Pristine Phone", "category": "mobile", "seller_condition": "brand_new"}
    detections = [
        {"class": "dent", "confidence": 0.78, "image": "corner.jpg"},
        {"class": "scratch", "confidence": 0.72, "image": "back.jpg"},
    ]

    report = reconcile_assessment(listing, detections=detections)

    assert len(report["discrepancies"]) > 0
    assert any("Seller claimed 'brand_new'" in disc for disc in report["discrepancies"])
    assert report["needs_clarification"] is True


@pytest.mark.asyncio
async def test_assessment_endpoint(monkeypatch):
    from fastapi.testclient import TestClient
    from app.main import app
    from bson import ObjectId
    from app.core.security import create_access_token

    class MockCol:
        def __init__(self, data=None):
            self.docs = data or []
        async def find_one(self, query=None, sort=None):
            return self.docs[-1] if self.docs else None
        def find(self, query=None):
            class _Cur:
                def __init__(self, d): self.d = d
                async def to_list(self, n=None): return list(self.d)
            return _Cur(self.docs)

    uid = str(ObjectId())
    lid = str(ObjectId())
    mock_listing = {"_id": ObjectId(lid), "seller_id": ObjectId(uid), "title": "Test Device", "category": "mobile", "status": "draft"}
    mock_db = type("DB", (), {
        "listings": MockCol([mock_listing]),
        "diagnostics": MockCol([]),
        "condition_predictions": MockCol([]),
        "listing_images": MockCol([]),
        "detections": MockCol([]),
        "authenticity": MockCol([]),
    })()

    monkeypatch.setattr("app.core.db.get_db", lambda: mock_db)
    monkeypatch.setattr("app.api.verification.get_db", lambda: mock_db)

    token = create_access_token(uid, "user")
    tc = TestClient(app)
    res = tc.get(f"/listings/{lid}/assessment", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 200
    data = res.json()
    assert "assessment" in data
    assert data["assessment"]["device_health"]["diagnostics_performed"] is False
    assert "Device has not been tested" in data["assessment"]["device_health"]["status_message"]


def test_device_mismatch_samsung_f15_vs_a35():
    # Listing specifies "Samsung F15" in title
    listing = {"title": "Samsung F15", "category": "mobile", "price": 100000}
    # Diagnosis telemetry returned from Samsung A35 (SM-A356E)
    diagnostic = {
        "category": "mobile",
        "device": {"model": "samsung SM-A356E", "os_version": "Android 16", "sdk": 36},
        "score": 44,
    }

    ident = identify_item(listing, diagnostic=diagnostic)
    assert ident["device_mismatch"] is True
    assert ident["device_mismatch_detail"] is not None
    assert "Samsung F15" in ident["device_mismatch_detail"]["declared_device"]
    assert "Galaxy A35" in ident["device_mismatch_detail"]["diagnosed_device"]
    assert "SM-A356E" in ident["device_mismatch_detail"]["diagnosed_device"]

    report = reconcile_assessment(listing, diagnostic=diagnostic)
    assert report["device_mismatch"] is True
    assert any("Device mismatch" in disc for disc in report["discrepancies"])

    from app.services.decision import decide_with_stops
    req_images = [{"angle": a} for a in ["front", "back", "left", "right", "top", "bottom"]]
    decision = decide_with_stops({"adjusted_score": 15}, listing, req_images, [], diagnostic=diagnostic)
    assert decision["status"] == "review"
    assert any("Device mismatch" in r for r in decision["review_reasons"])
    assert any("Samsung F15" in r and "A35" in r for r in decision["review_reasons"])
    assert any("SafeResale Diagnosis app on the actual" in tip for tip in decision["actionable_tips"])


