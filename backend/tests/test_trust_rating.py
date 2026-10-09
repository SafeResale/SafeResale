"""Hermetic tests for the server-computed trust verdict (source of truth).

The marketplace badge must render whatever the backend says: the app does not
re-derive bands. Ranges (product rule): 70-100 good, 30-69 moderate, 1-29 poor.
"""
from app.api.listings import _trust_rating, _trust_score, trust_band
import pytest


def test_trust_band_boundaries():
    # Product rule (Trust Badge is a presentation layer, separate from the
    # Risk Score which stays untouched):
    #   1-30 -> Poor, 31-70 -> Moderate, 71-100 -> Good
    for s in (1, 15, 30):
        assert trust_band(s) == "poor"
    for s in (31, 50, 70):
        assert trust_band(s) == "moderate"
    for s in (71, 82, 99, 100):
        assert trust_band(s) == "good"
    assert trust_band(0) == "poor"
    assert trust_band(None) is None


def test_trust_score_generates_score_from_risk_data():
    # The Risk Score drives the trust score through inversion: HIGHER risk =
    # LOWER trust, so a legit (low-risk) product shows a HIGH trust score.
    # risk 82 (risky) -> trust 18 (poor), risk 20 (clean) -> trust 80 (good).
    doc = {"is_fit_to_show": None, "diagnostic_score": None, "risk": {"adjusted_score": 82}}
    assert _trust_score(doc) == 18.0
    doc = {"is_fit_to_show": None, "risk": {"adjusted_score": 20}}
    assert _trust_score(doc) == 80.0

    # The reported regression: a legit product with a low risk score must NOT be
    # a poor-trust product.
    doc = {"is_fit_to_show": None, "risk": {"adjusted_score": 14.8}}
    assert _trust_score(doc) == 85.2
    assert trust_band(_trust_score(doc)) == "good"


def test_trust_score_clamps_risk_inversion():
    assert _trust_score({"risk": {"adjusted_score": 200}}) == 0.0
    assert _trust_score({"risk": {"adjusted_score": -5}}) == 100.0


def test_trust_score_falls_back_to_diagnostic():
    doc = {"is_fit_to_show": None, "diagnostic_score": 42}
    assert _trust_score(doc) == 42.0


def test_trust_score_prefers_approved_submission():
    doc = {
        "is_fit_to_show": True,
        "submission_score": 81.0,
        "diagnostic_score": 55,
        "risk": {"adjusted_score": 20},
    }
    assert _trust_score(doc) == 81.0


def test_trust_score_hidden_when_withheld():
    doc = {"is_fit_to_show": False, "submission_score": 95, "diagnostic_score": 90}
    assert _trust_score(doc) is None


def test_trust_rating_payload():
    rating = _trust_rating({"is_fit_to_show": True, "submission_score": 81.0})
    assert rating == {"score": 81, "band": "good", "label": "Good"}

    rating = _trust_rating({"is_fit_to_show": False, "submission_score": 95})
    assert rating["score"] is None and rating["band"] is None and rating["label"] is None

    rating = _trust_rating({"is_fit_to_show": None, "risk": {"adjusted_score": 82}})
    assert rating == {"score": 18, "band": "poor", "label": "Poor"}

    rating = _trust_rating({"is_fit_to_show": None, "risk": {"adjusted_score": 50}})
    assert rating == {"score": 50, "band": "moderate", "label": "Moderate"}

    rating = _trust_rating({"is_fit_to_show": None, "risk": {"adjusted_score": 25}})
    assert rating == {"score": 75, "band": "good", "label": "Good"}

    rating = _trust_rating({})
    assert rating["score"] is None and rating["band"] is None and rating["label"] is None


# --- pipeline persistence (score_listing, hermetic in-memory DB) -------------
from collections import defaultdict
from bson import ObjectId


class _Cursor:
    def __init__(self, rows):
        self._rows = rows

    async def to_list(self, n=None):
        return self._rows


class _Col:
    def __init__(self, rows):
        self.rows = rows

    async def find_one(self, query=None, sort=None, **kw):
        return self.rows[-1] if self.rows else None  # insert order ≈ newest

    def find(self, query=None, **kw):
        return _Cursor(list(self.rows))

    async def insert_one(self, doc):
        oid = ObjectId()
        d = dict(doc)
        d["_id"] = oid
        self.rows.append(d)
        return type("R", (), {"inserted_id": oid})()

    async def update_one(self, query, update):
        if not self.rows:
            return type("R", (), {"matched_count": 0, "modified_count": 0})()
        for row in self.rows:
            if query.get("_id") == row.get("_id") or query.get("_id") is None:
                row.update(update.get("$set", {}))
                return type("R", (), {"matched_count": 1, "modified_count": 1})()
        return type("R", (), {"matched_count": 0, "modified_count": 0})()


class _FakeDB:
    def __init__(self):
        self._c = defaultdict(list)
        for name in ("listings", "diagnostics", "detections", "condition_predictions",
                     "listing_images", "risk_scores", "decisions", "price_stats"):
            setattr(self, name, _Col(self._c[name]))


async def _seed(rows: dict):
    db = _FakeDB()
    r = await db.listings.insert_one({"seller_id": None, "category": "mobile",
                                      "title": "t", "price": 20000,
                                      "status": "published", "created_at": 1})
    lid = r.inserted_id
    await db.diagnostics.insert_one({"listing_id": lid, **rows["diag"], "created_at": 1})
    await db.detections.insert_one({"listing_id": lid, "class": "screen_damage",
                                    "confidence": 0.9, "created_at": 1})
    return db, str(lid)


def _install(monkeypatch, db, stubs):
    import app.api.verification as ver
    monkeypatch.setattr(ver, "get_db", lambda: db)
    for name, fn in stubs.items():
        monkeypatch.setattr(ver, name, fn)


async def _no_anomaly(listing) -> dict:
    return {"deviation_ratio": 0}


async def _no_signals(seller_id, listing=None) -> list:
    return []


@pytest.mark.asyncio
async def test_score_listing_persists_trust_snapshot(monkeypatch):
    from app.api.verification import score_listing
    db, lid = await _seed({"diag": {"score": 88, "tests": []}})
    stubs = {"price_anomaly": _no_anomaly, "seller_signals": _no_signals}
    _install(monkeypatch, db, stubs)
    payload, listing = await score_listing(lid)
    assert payload["adjusted_score"] > 0  # screen damage detection => risk present

    refreshed = await db.listings.find_one({"_id": ObjectId(lid)})
    assert refreshed["risk"]["adjusted_score"] == payload["adjusted_score"]
    # canonical diagnostics qualify as the badge source when no risk snapshot
    assert refreshed["diagnostic_score"] == 88
    # trust persisted on the doc matches what the read path computes
    assert refreshed["trust"]["score"] == _trust_rating(refreshed)["score"]


@pytest.mark.asyncio
async def test_score_listing_skips_fallback_diagnostic_score(monkeypatch):
    from app.api.verification import score_listing
    from app.api.listings import _trust_rating
    db, lid = await _seed({"diag": {"score": 88, "tests": [], "error": "import failed"}})
    _install(monkeypatch, db, {"price_anomaly": _no_anomaly, "seller_signals": _no_signals})
    payload, listing = await score_listing(lid)
    refreshed = await db.listings.find_one({"_id": ObjectId(lid)})
    assert refreshed["risk"]["adjusted_score"] == payload["adjusted_score"]
    assert "diagnostic_score" not in refreshed  # fallback path is NOT a quality score
    # badge classifies the trust score generated from the risk data: higher = better
    expected = round(100 - payload["adjusted_score"])
    assert _trust_rating(refreshed)["score"] == expected
    from app.api.listings import trust_band
    assert _trust_rating(refreshed)["band"] == trust_band(expected)