"""Hermetic tests for secure token-based pairing and device diagnosis workflow."""
import time
import pytest
from bson import ObjectId
from fastapi.testclient import TestClient
from app.main import app
from app.core.security import create_access_token

client = TestClient(app)


def _auth_headers(user_id: str, role: str = "user"):
    token = create_access_token(user_id, role)
    return {"Authorization": f"Bearer {token}"}


class MockCol:
    def __init__(self):
        self.docs = []

    def _match(self, doc, query):
        if not query:
            return True
        for k, v in query.items():
            if k == "$or" and isinstance(v, list):
                if not any(self._match(doc, cond) for cond in v):
                    return False
                continue
            if isinstance(v, dict):
                if "$in" in v:
                    val = doc.get(k)
                    if val not in v["$in"]:
                        return False
                elif "$ne" in v:
                    if doc.get(k) == v["$ne"]:
                        return False
            else:
                if doc.get(k) != v:
                    return False
        return True

    async def find_one(self, query=None, sort=None):
        matched = [d for d in self.docs if self._match(d, query)]
        if not matched:
            return None
        if sort and sort[0][0] == "created_at" and sort[0][1] == -1:
            matched.sort(key=lambda d: d.get("created_at", 0), reverse=True)
        return dict(matched[-1])

    async def insert_one(self, doc):
        d = dict(doc)
        if "_id" not in d:
            d["_id"] = ObjectId()
        self.docs.append(d)
        return type("Res", (), {"inserted_id": d["_id"]})()

    async def update_one(self, query, update):
        target = None
        for d in self.docs:
            if self._match(d, query):
                target = d
                break
        if target and "$set" in update:
            for k, v in update["$set"].items():
                target[k] = v
        return type("Res", (), {"modified_count": 1 if target else 0})()

    async def count_documents(self, query=None):
        return len([d for d in self.docs if self._match(d, query)])


class MockDB:
    def __init__(self):
        self.listings = MockCol()
        self.diagnosis_sessions = MockCol()
        self.diagnostics = MockCol()
        self.audit = MockCol()
        self.audit_log = MockCol()


@pytest.mark.asyncio
async def test_diagnosis_session_flow_and_token_security(monkeypatch):
    mock_db = MockDB()
    monkeypatch.setattr("app.core.db.get_db", lambda: mock_db)
    monkeypatch.setattr("app.api.diagnosis.get_db", lambda: mock_db)
    monkeypatch.setattr("app.core.audit.get_db", lambda: mock_db)

    seller_id = str(ObjectId())
    other_user_id = str(ObjectId())
    listing_id = str(ObjectId())

    # Seed listing
    await mock_db.listings.insert_one({
        "_id": ObjectId(listing_id),
        "seller_id": ObjectId(seller_id),
        "title": "Pixel 7 Pro 128GB",
        "category": "mobile",
        "status": "draft",
    })

    headers = _auth_headers(seller_id)
    other_headers = _auth_headers(other_user_id)

    # 1. Create session as seller -> generates short-lived token
    res = client.post("/diagnosis/sessions", json={"listing_id": listing_id, "category": "mobile"}, headers=headers)
    assert res.status_code == 201, res.text
    body = res.json()
    assert "token" in body and body["token"]
    assert "session" in body
    session_id = body["session"]["_id"]
    token = body["token"]
    assert body["session"]["state"] == "token_generated"
    assert "Open the SafeResale Diagnosis app and enter this token" in body["instructions"]

    # 2. Other user cannot access session without token
    res_forbidden = client.get(f"/diagnosis/sessions/{session_id}", headers=other_headers)
    assert res_forbidden.status_code == 403

    # 3. Invalid or guessed token is rejected
    res_bad = client.post("/diagnosis/pair", json={"token": "completely_fake_guessed_token", "device": {"model": "Pixel 7"}})
    assert res_bad.status_code == 401

    # 4. Pair device with valid token
    device_info = {"model": "Pixel 7 Pro", "os_version": "Android 14", "sdk": 34}
    res_pair = client.post("/diagnosis/pair", json={"token": token, "device": device_info, "device_id": "dev_test_123"})
    assert res_pair.status_code == 200, res_pair.text
    pair_body = res_pair.json()
    assert pair_body["paired"] is True
    assert pair_body["session"]["state"] == "paired"

    # 5. Reusing the token for pairing must be rejected (single-use limit)
    res_reused = client.post("/diagnosis/pair", json={"token": token, "device": device_info})
    assert res_reused.status_code == 401
    assert res_reused.json()["detail"]["code"] == "token_used"

    # 6. Report progress
    res_prog = client.post(f"/diagnosis/sessions/{session_id}/progress", json={"token": token})
    assert res_prog.status_code == 200
    assert res_prog.json()["status"] == "in_progress"

    # 7. Submitting results with valid token succeeds and updates listing
    tests = [
        {"id": "touch", "name": "Multi-touch screen", "status": "passed"},
        {"id": "battery", "name": "Battery health", "status": "passed"},
        {"id": "camera", "name": "Rear Camera", "status": "passed"},
        {"id": "wifi", "name": "Wi-Fi Connectivity", "status": "passed"},
    ]
    res_results = client.post(
        f"/diagnosis/sessions/{session_id}/results",
        json={"token": token, "tests": tests, "device": device_info, "category": "mobile"},
    )
    assert res_results.status_code == 200, res_results.text
    results_body = res_results.json()
    assert results_body["received"] is True
    assert results_body["session"]["state"] == "results_received"

    # Check listing status
    res_status = client.get(f"/diagnosis/listings/{listing_id}/status", headers=headers)
    assert res_status.status_code == 200
    st_body = res_status.json()
    assert st_body["diagnosis_state"] == "results_received"

    # 8. Seller verifies session
    res_verify = client.post(f"/diagnosis/sessions/{session_id}/verify", headers=headers)
    assert res_verify.status_code == 200
    assert res_verify.json()["verified"] is True
    assert res_verify.json()["session"]["state"] == "verification_completed"


@pytest.mark.asyncio
async def test_expired_token_rejected_and_regenerated(monkeypatch):
    mock_db = MockDB()
    monkeypatch.setattr("app.core.db.get_db", lambda: mock_db)
    monkeypatch.setattr("app.api.diagnosis.get_db", lambda: mock_db)
    monkeypatch.setattr("app.core.audit.get_db", lambda: mock_db)

    seller_id = str(ObjectId())
    listing_id = str(ObjectId())

    await mock_db.listings.insert_one({
        "_id": ObjectId(listing_id),
        "seller_id": ObjectId(seller_id),
        "title": "Galaxy S23",
        "category": "mobile",
        "status": "draft",
    })

    headers = _auth_headers(seller_id)

    # Create session
    res = client.post("/diagnosis/sessions", json={"listing_id": listing_id}, headers=headers)
    assert res.status_code == 201
    body = res.json()
    session_id = body["session"]["_id"]
    token = body["token"]

    # Artificially expire the token in DB
    await mock_db.diagnosis_sessions.update_one(
        {"_id": ObjectId(session_id)},
        {"$set": {"token_expires_at": time.time() - 100}}
    )

    # Attempting to pair with expired token must fail
    res_pair = client.post("/diagnosis/pair", json={"token": token, "device": {"model": "S23"}})
    assert res_pair.status_code == 401
    assert res_pair.json()["detail"]["code"] == "expired_token"

    # Regenerate token as seller
    res_regen = client.post(f"/diagnosis/sessions/{session_id}/regenerate-token", headers=headers)
    assert res_regen.status_code == 200
    new_token = res_regen.json()["token"]
    assert new_token != token

    # Pair with new token succeeds
    res_pair2 = client.post("/diagnosis/pair", json={"token": new_token, "device": {"model": "S23"}})
    assert res_pair2.status_code == 200
    assert res_pair2.json()["paired"] is True


@pytest.mark.asyncio
async def test_laptop_diagnosis_web_sync_flow(monkeypatch):
    mock_db = MockDB()
    monkeypatch.setattr("app.core.db.get_db", lambda: mock_db)
    monkeypatch.setattr("app.api.diagnosis.get_db", lambda: mock_db)
    monkeypatch.setattr("app.core.audit.get_db", lambda: mock_db)

    seller_id = str(ObjectId())
    listing_id = str(ObjectId())

    await mock_db.listings.insert_one({
        "_id": ObjectId(listing_id),
        "seller_id": ObjectId(seller_id),
        "title": "MacBook Air M2",
        "category": "laptop",
        "status": "draft",
    })

    headers = _auth_headers(seller_id)

    # 1. Create laptop diagnosis session
    res = client.post("/diagnosis/sessions", json={"listing_id": listing_id, "category": "laptop"}, headers=headers)
    assert res.status_code == 201
    body = res.json()
    token = body["token"]
    session_id = body["session"]["_id"]
    assert body["session"]["category"] == "laptop"

    # 2. Check session lookup by token (unauthenticated web browser query)
    res_lookup = client.get(f"/diagnosis/session-by-token?token={token}")
    assert res_lookup.status_code == 200
    assert res_lookup.json()["listing_id"] == listing_id

    # 3. Unauthenticated session get by token
    res_sess = client.get(f"/diagnosis/sessions/{session_id}?token={token}")
    assert res_sess.status_code == 200
    assert res_sess.json()["session"]["_id"] == session_id

    # 4. Pair from laptop browser
    laptop_device = {"model": "MacBook Air M2", "platform": "MacIntel", "device_type": "laptop"}
    res_pair = client.post("/diagnosis/pair", json={"token": token, "device": laptop_device, "device_id": "web_laptop_1"})
    assert res_pair.status_code == 200
    assert res_pair.json()["paired"] is True

    # 5. In progress
    res_prog = client.post(f"/diagnosis/sessions/{session_id}/progress", json={"token": token})
    assert res_prog.status_code == 200
    assert res_prog.json()["status"] == "in_progress"

    # 6. Submit laptop tests
    laptop_tests = [
        {"id": "battery", "status": "passed", "value": 98, "unit": "%", "simulated": False, "meta": {}},
        {"id": "display", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "keyboard", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "trackpad", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "camera", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "microphone", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "speaker", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "wifi", "status": "passed", "value": "ok", "unit": None, "simulated": False, "meta": {}},
        {"id": "bluetooth", "status": "unsupported", "value": "unsupported", "unit": None, "simulated": False, "meta": {}},
        {"id": "ports", "status": "unsupported", "value": "unsupported", "unit": None, "simulated": False, "meta": {}},
    ]
    res_res = client.post(
        f"/diagnosis/sessions/{session_id}/results",
        json={"token": token, "tests": laptop_tests, "device": laptop_device, "category": "laptop"},
    )
    assert res_res.status_code == 200
    assert res_res.json()["received"] is True
    assert res_res.json()["diagnostic_score"] == 100

    # 7. Check listing status has score 100
    res_status = client.get(f"/diagnosis/listings/{listing_id}/status", headers=headers)
    assert res_status.status_code == 200
    assert res_status.json()["diagnostic_score"] == 100
    assert res_status.json()["diagnosis_state"] == "results_received"
