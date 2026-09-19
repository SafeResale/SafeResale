"""Hermetic tests for the unified 4-model AI report (app/services/ml_report.py).

Reads only persisted pipeline data — never recomputes. Confirms the product
page gets concrete verdicts from M1 (defects), M2 (condition), M3
(authenticity) and M6 (image quality).
"""
import pytest
from bson import ObjectId

from app.services.ml_report import models_report

# --- minimal in-memory Mongo -------------------------------------------------
from collections import defaultdict


class _Cursor:
    def __init__(self, rows):
        self._rows = rows

    async def to_list(self, n=None):
        return list(self._rows)


class _Col:
    def __init__(self, rows):
        self.rows = rows

    async def find_one(self, query=None, sort=None, **kw):
        return self.rows[-1] if self.rows else None

    def find(self, query=None, **kw):
        return _Cursor(self.rows)

    async def insert_one(self, doc):
        oid = ObjectId()
        d = dict(doc)
        d["_id"] = oid
        self.rows.append(d)
        return type("R", (), {"inserted_id": oid})()


class _FakeDB:
    def __init__(self):
        self._c = defaultdict(list)
        for name in ("listing_images", "detections", "condition_predictions", "authenticity"):
            setattr(self, name, _Col(self._c[name]))


def _install(monkeypatch, db):
    monkeypatch.setattr("app.services.ml_report.get_db", lambda: db)


async def _seed_with_defects():
    db = _FakeDB()
    lid = ObjectId()
    for i in range(8):
        await db.listing_images.insert_one({
            "listing_id": lid, "angle": str(i), "stored_key": f"k{i}",
            "server_quality": {"quality_score": 82, "passed": True,
                               "details": {"blur_score": 314}},
        })
    await db.detections.insert_one({"listing_id": lid, "class": "dent", "confidence": 0.87, "simulated": True, "model_version": "stub-1.0"})
    await db.detections.insert_one({"listing_id": lid, "class": "scratch", "confidence": 0.64, "simulated": True, "model_version": "stub-1.0"})
    await db.condition_predictions.insert_one({"listing_id": lid, "label": "Good", "probabilities": {"Good": 1.0, "Moderate": 0.0, "Defective": 0.0}, "simulated": True, "model_version": "stub-1.0"})
    for i in range(8):
        await db.authenticity.insert_one({"listing_id": lid, "label": "human", "ai_generated_prob": 0.05, "human_prob": 0.95, "simulated": True, "model_version": "stub-v1"})
    return db, str(lid)


@pytest.mark.asyncio
async def test_defects_plus_other_models(monkeypatch):
    db, lid = await _seed_with_defects()
    _install(monkeypatch, db)
    rep = await models_report(lid)

    assert rep["m1"]["verdict"].startswith("2 defects found")
    assert rep["m1"]["ok"] is False
    assert {"label": "dent", "value": "87%"} in rep["m1"]["items"]
    assert {"label": "scratch", "value": "64%"} in rep["m1"]["items"]

    assert rep["m2"]["verdict"].startswith("Good")
    assert rep["m2"]["ok"] is True

    assert rep["m3"]["verdict"].startswith("8 human")
    assert rep["m3"]["ok"] is True

    assert rep["m6"]["verdict"] == "8/8 photos passed quality check"
    assert rep["m6"]["ok"] is True
    assert {"label": "avg quality", "value": "82/100"} in rep["m6"]["items"]


@pytest.mark.asyncio
async def test_ai_generated_flags_m3(monkeypatch):
    db, lid = await _seed_with_defects()
    await db.authenticity.insert_one({"listing_id": lid, "label": "ai-generated", "ai_generated_prob": 0.93, "human_prob": 0.07, "simulated": True, "model_version": "stub-v1"})
    _install(monkeypatch, db)
    rep = await models_report(lid)
    assert rep["m3"]["ok"] is False
    assert "highest AI probability" in [i["label"] for i in rep["m3"]["items"]]
    assert "93%" in [i["value"] for i in rep["m3"]["items"]]


@pytest.mark.asyncio
async def test_empty_listing_reports_no_data(monkeypatch):
    db = _FakeDB()
    _install(monkeypatch, db)
    rep = await models_report(str(ObjectId()))
    assert rep["m1"]["verdict"] == "No defects detected"
    assert rep["m6"]["verdict"] == "No photos analysed yet"