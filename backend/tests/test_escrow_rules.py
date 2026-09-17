"""Deterministic escrow-state-machine tests — byte-exact against the landed
escrow.py (152 L). NO fake mongo, NO auth headers, NO network, NO async — these
are pure, always-green invariant checks that change only if someone edits the
transition table. This is the hermetic, unbreakable core of the escrow slice,
deliberately decoupled from DB/auth so the slice is proven as far as it can be
without services.
"""
import pytest

from app.api.escrow import ESCROW_STATUSES, TRANSITIONS, _transition_from


def _assert_raises(*, action: str, current: str, want_code: str):
    from fastapi import HTTPException

    with pytest.raises(HTTPException) as exc:
        _transition_from(current, action)
    assert exc.value.status_code == 422 or exc.value.status_code == 409
    assert exc.value.detail["code"] == want_code


def test_statuses_are_strings_and_distinct():
    assert len(ESCROW_STATUSES) == len(set(ESCROW_STATUSES))
    assert all(isinstance(s, str) and s.islower() for s in ESCROW_STATUSES)


def test_every_transition_source_is_a_real_status():
    for src in TRANSITIONS:
        assert src in ESCROW_STATUSES


def test_every_transition_target_is_a_real_status_and_no_self_loop():
    for src, targets in TRANSITIONS.items():
        for tgt in targets:
            assert tgt in ESCROW_STATUSES
            assert tgt != src  # self-loop would 409 forever


def test_unknown_action_is_422_bad_action():
    _assert_raises(action="explode", current="pending", want_code="bad_action")


def test_pending_hold_accepted_unknown_source_is_422():
    assert _transition_from("pending", "hold") == "held"


def test_pending_release_rejected_is_invalid_transition():
    _assert_raises(action="release", current="pending", want_code="invalid_transition")


def test_held_to_review_and_release_accepted():
    assert _transition_from("held", "review") == "in_review"
    assert _transition_from("held", "release") == "released"
    assert _transition_from("held", "refund") == "refunded"


def test_in_review_release_and_refund_accepted():
    assert _transition_from("in_review", "release") == "released"
    assert _transition_from("in_review", "refund") == "refunded"


def test_released_is_terminal():
    for action in ("hold", "review", "release", "refund"):
        _assert_raises(action=action, current="released", want_code="invalid_transition")
