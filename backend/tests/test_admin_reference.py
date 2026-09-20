"""Hermetic, DB-free invariant tests for the admin expansion slice.

Mirrors the style of test_escrow_rules.py: pure, deterministic checks that
only change if someone edits the constants/transitions in the new admin routers
(users, catalog, content, support, settings).
"""
import pytest

from app.api.admin_users import ROLES, STATUSES
from app.api.admin_catalog import _slugify, DEFAULTS
from app.api.admin_content import CONTENT_STATUSES, _ATTRS
from app.api.admin_support import REPORT_STATUSES, REPORT_TYPES, MESSAGE_STATUSES, NOTIFICATION_AUDIENCES
from app.api.admin_settings import DEFAULT_SETTINGS, GROUPS


def test_roles_and_statuses_are_lowercase_distinct():
    assert len(ROLES) == len(set(ROLES)) == 4
    assert {"seller", "buyer", "admin", "inspector"} == set(ROLES)
    assert STATUSES == ("active", "suspended")


def test_slugify_normalizes():
    assert _slugify(" Hello, World! ") == "hello-world"
    assert _slugify("Mobile Phones") == "mobile-phones"
    assert _slugify("   ") == ""


def test_default_categories_are_valid_and_distinct():
    from app.core.catalog import LEGACY_SLUGS
    slugs = [c["slug"] for c in DEFAULTS]
    assert len(slugs) == len(set(slugs)) == 10
    for c in DEFAULTS:
        assert re_full_slug(c["slug"])
        assert isinstance(c["fields"], list) and c["fields"]
    # PRD product scope (docs/08-ml-plan.md §3.1) + furniture/accessory
    assert {"mobile", "electronics", "camera", "gaming", "appliance", "car", "bike", "furniture", "accessory"} <= set(slugs)
    assert not (LEGACY_SLUGS & set(slugs))


import re


def re_full_slug(s: str) -> bool:
    return bool(re.fullmatch(r"[a-z0-9-]+", s))


def test_content_statuses_and_attrs():
    assert CONTENT_STATUSES == ("published", "draft")
    for coll in ("blogs", "faqs", "tips"):
        assert coll in _ATTRS
        assert "status" in _ATTRS[coll]
        assert "created_at" not in _ATTRS[coll]


def test_support_enum_sets():
    assert REPORT_STATUSES == ("pending", "resolved", "dismissed")
    assert set(REPORT_TYPES) == {"listing", "user"}
    for s in MESSAGE_STATUSES:
        assert s in ("new", "read", "resolved", "archived")
    assert NOTIFICATION_AUDIENCES[0] == "all"


def test_settings_groups_known_keys_are_unique():
    keys = [k for g in DEFAULT_SETTINGS.values() for k in g]
    assert len(keys) == len(set(keys))
    assert set(GROUPS) == set(DEFAULT_SETTINGS.keys())
    # every entry carries the metadata the admin UI renders
    for group in DEFAULT_SETTINGS.values():
        for meta in group.values():
            assert "type" in meta and "description" in meta and "value" in meta


def test_settings_key_overlap_with_seed_reference():
    import seed_reference  # type: ignore

    seed_keys = {k for group in seed_reference.DEFAULT_SETTINGS.values() for k in group}
    app_keys = {k for group in DEFAULT_SETTINGS.values() for k in group}
    assert seed_keys == app_keys


def test_admin_listing_payload_is_json_encodable():
    """Listing-detail evidence docs carry nested ObjectIds; the response must
    be deep-serialized or FastAPI 500s (regression: admin listing detail)."""
    import json
    from bson import ObjectId
    from fastapi.encoders import jsonable_encoder

    from app.core.serialize import s as _s

    raw = {
        "_id": ObjectId(),
        "seller_id": ObjectId(),
        "created_at": 1234.0,
        "notes": {"visible": None},
    }
    evidence = {
        "images": [{"_id": ObjectId(), "listing_id": ObjectId(), "sha256": "a", "quality": {"passed": True}}],
        "detections": [{"_id": ObjectId(), "det_type": "iron", "confidence": 0.9}],
        "condition": [{"_id": ObjectId(), "grade": "good"}],
        "diagnostics": [{"_id": ObjectId(), "test": "x", "passed": True}],
        "risk_history": [{"_id": ObjectId(), "adjusted_score": 12.0, "factors": {"pf": 0.1}}],
        "decisions": [{"_id": ObjectId(), "status": "approved"}],
        "audit_trail": [{"_id": ObjectId(), "actor_id": ObjectId(), "target_id": ObjectId(), "action": "admin.approve"}],
    }
    listing = _s(raw)
    payload = {"listing": listing, "evidence": {k: _s(v) for k, v in evidence.items()}}
    # must not raise; nothing left unconverted
    encoded = jsonable_encoder(payload)
    s = json.dumps(encoded)
    assert ObjectId.__name__ not in s
    json.loads(s)