"""Boundary / edge-case checks for the diagnostics scoring service."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SVC = os.path.join(HERE, "..", "services")
sys.path.insert(0, SVC)
import diagnostics as d  # noqa: E402


def tests(chosen):
    return [{"id": t, "status": "passed"} for t in chosen]


def main():
    mobile_ids = ["battery", "camera", "microphone", "speaker", "touch", "wifi",
                  "bluetooth", "gps", "sensor_accelerometer", "sensor_gyroscope",
                  "sensor_proximity"]
    # 1. single failed battery (20% weight) -> should be 80
    r = d.compute_diagnostics({
        "category": "mobile", "skipped": False,
        "tests": [{"id": "battery", "status": "failed"}] + tests(mobile_ids[1:]),
    })
    assert r["score"] == 80.0, f"battery fail expected 80, got {r['score']}"
    # 2. unknown category falls back to generic without crashing
    r = d.compute_diagnostics({"category": "toaster", "skipped": False,
                               "tests": tests(["power", "connectivity",
                                               "inputs", "outputs", "camera"])})
    assert r["score"] == 100.0
    # 3. unsupported status not penalized
    r = d.compute_diagnostics({"category": "mobile", "skipped": False,
                               "tests": [{"id": "gps", "status": "unsupported"}]
                               + tests([t for t in mobile_ids if t != "gps"])})
    assert r["score"] == 100.0, f"unsupported should be unpuzzled, got {r['score']}"
    # 4. permission_required counts as not passed
    r = d.compute_diagnostics({"category": "mobile", "skipped": False,
                               "tests": [{"id": "battery", "status": "permission_required"}]
                               + tests(mobile_ids[1:])})
    assert r["score"] == 80.0
    assert "permission:battery" in r["basis"]
    # 5. skipped whole -> score 0 + missing penalty
    r = d.compute_diagnostics({"category": "mobile", "skipped": True, "tests": []})
    assert r["score"] == 0.0 and r["missing_penalty"] > 0
    # 6. empty report, no category -> 0 + penalty (nothing trustworthy)
    r = d.compute_diagnostics({})
    assert r["score"] == 0.0 and r["missing_penalty"] > 0

    print("ALL 6 BOUNDARY CHECKS PASSED")


if __name__ == "__main__":
    main()
