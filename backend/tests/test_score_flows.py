"""Score-flow unit tests (no DB): upload-key guards, diagnostics normalization,
diagnostic risk honoring unsupported/unavailable (R-DIAG-02)."""
from app.api.uploads import _is_safe_key, _safe_filename
from app.api.verification import _normalize_diag_tests
from app.services.risk import diagnostic_risk


def test_safe_key_rejects_traversal():
    assert not _is_safe_key("../../etc/passwd")
    assert not _is_safe_key("/abs/path.jpg")
    assert not _is_safe_key("")
    assert not _is_safe_key("x" * 300)
    assert _is_safe_key("64f0/front_ab12_photo.jpg")


def test_safe_filename_strips_dirs():
    assert _safe_filename("../../x.png") == "x.png"
    assert _safe_filename("my photo!.jpg") == "my_photo_.jpg"


def test_normalize_marks_passed_and_coerces_status():
    tests = _normalize_diag_tests([
        {"id": "battery", "status": "passed", "value": 82},
        {"id": "wifi", "status": "bogus"},
        {"id": "gps", "status": "unsupported"},
        "not-a-dict",
    ])
    assert len(tests) == 3
    by_id = {t["id"]: t for t in tests}
    assert by_id["battery"]["passed"] is True
    assert by_id["wifi"]["status"] == "skipped" and by_id["wifi"]["passed"] is False
    assert by_id["gps"]["status"] == "unsupported" and by_id["gps"]["passed"] is False


def test_diagnostic_risk_all_pass_is_zero():
    diag = {"skipped": False, "tests": [
        {"id": "battery", "status": "passed", "passed": True},
        {"id": "wifi", "status": "passed", "passed": True},
    ]}
    r = diagnostic_risk(diag)
    assert r["score"] == 0


def test_diagnostic_risk_ignores_unsupported():
    diag = {"skipped": False, "tests": [
        {"id": "battery", "status": "passed", "passed": True},
        {"id": "gps", "status": "unsupported", "passed": False},
        {"id": "sensor_x", "status": "unavailable", "passed": False},
    ]}
    r = diagnostic_risk(diag)
    assert r["score"] == 0  # unsupported/unavailable are not strikes


def test_diagnostic_risk_counts_failed_and_skipped():
    diag = {"skipped": False, "tests": [
        {"id": "battery", "status": "failed", "passed": False},
        {"id": "wifi", "status": "skipped", "passed": False},
        {"id": "camera", "status": "passed", "passed": True},
    ]}
    r = diagnostic_risk(diag)
    assert r["score"] == 44
    assert set(r["basis"]) == {"battery", "wifi"}
