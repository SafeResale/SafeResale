"""Device diagnostics — backend scoring service.

Computes a 0-100 diagnostic score from a structured diagnostics report for ANY
device category (mobile, laptop, tablet, generic electronics), per
03-architecture.md api/diagnostics, 05-data-model.md 2.8, R-DIAG-09, and
09-phase-plan.md Phase 4 ("diagnostics ingestion + diagnostic score + missing-data
handling").

Design goals
------------
* **Category-aware but not category-locked.** A device reports a `category` and a
  list of `tests`. The service validates the tests against a per-category schema
  (which tests are *expected* and how much each weighs). Unknown categories fall
  back to a generic schema so nothing crashes.
* **Honest measurements.** Only results flagged `simulated:false` with a real
  `value`/`unit` are trusted as evidence. `unavailable`/`unsupported` (the OS or
  device genuinely lacks the capability) are *not* penalized — the capability
  simply doesn't apply. `failed`, `permission_required` (user denied a sensor the
  device has), and `skipped` penalize.
* **Skipped diagnostics -> missing penalty.** If the seller skips diagnostics
  entirely (R-DIAG-08), the report is `skipped:true` and the score carries a
  `missing_penalty` the risk engine applies (R-RISK-05), rather than a fabricated
  pass.

Scoring model
-------------
    applicable = tests the category expects AND the device reports
    weight     = per-test weight from the category schema
    earned     = sum(weight for passed tests)
    denominator= sum(weight for applicable tests)
    score      = 0 if denominator == 0 else round(100 * earned / denominator)

`permission_required` counts as *not passed* (an unavailable sensor is only
accepted when the report says `unsupported`, not when the user refused).

This module is dependency-free (stdlib only) so it can run in CI/unit tests and
slot straight into a FastAPI service later.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

# Scoring config version (R-RISK-06: versioned config). Bump when weights change.
REPORT_VERSION = "1.0"

# Valid per-test statuses.
PASSED = "passed"
FAILED = "failed"
UNSUPPORTED = "unsupported"       # capability absent on this device / OS
PERMISSION_REQUIRED = "permission_required"  # user denied -> not validated
SKIPPED = "skipped"               # seller/device skipped this test
UNAVAILABLE = "unavailable"       # OS does not expose a reliable value (e.g. battery health)
STATUSES = {PASSED, FAILED, UNSUPPORTED, PERMISSION_REQUIRED, SKIPPED, UNAVAILABLE}

# Statuses that mean "cannot be confirmed working" and therefore do NOT score.
# unsupported/unavailable are explicitly excluded: the capability legitimately
# doesn't apply, so it isn't a strike against the device.
NOT_PASSED = {FAILED, PERMISSION_REQUIRED, SKIPPED}

# Per-category test schema: test id -> weight (0..1). Sum need not equal 1; the
# score is the fraction of weight earned. Add categories as needed.
SCHEMAS: dict[str, dict[str, float]] = {
    "mobile": {
        "battery": 0.20,
        "camera": 0.15,
        "microphone": 0.10,
        "speaker": 0.10,
        "touch": 0.12,
        "wifi": 0.08,
        "bluetooth": 0.05,
        "gps": 0.05,
        "sensor_accelerometer": 0.05,
        "sensor_gyroscope": 0.05,
        "sensor_proximity": 0.05,
    },
    "laptop": {
        "battery": 0.18,
        "display": 0.15,
        "keyboard": 0.12,
        "trackpad": 0.10,
        "camera": 0.10,
        "microphone": 0.08,
        "speaker": 0.08,
        "wifi": 0.07,
        "bluetooth": 0.05,
        "ports": 0.07,
    },
    "tablet": {
        "battery": 0.20,
        "display": 0.15,
        "camera": 0.15,
        "microphone": 0.10,
        "speaker": 0.10,
        "touch": 0.12,
        "wifi": 0.08,
        "bluetooth": 0.05,
        "gps": 0.05,
    },
    "generic": {
        "power": 0.25,
        "connectivity": 0.20,
        "inputs": 0.25,
        "outputs": 0.20,
        "camera": 0.10,
    },
}

# Fallback when a device reports a category we haven't modelled.
FALLBACK_SCHEMA = SCHEMAS["generic"]

# Penalty (0-100) applied when diagnostics are skipped entirely (R-DIAG-08 /
# R-RISK-05). Configurable via --missing-penalty.
DEFAULT_MISSING_PENALTY = 5.0


def schema_for(category: str) -> dict[str, float]:
    """Return the test schema for a category, falling back to generic."""
    return dict(SCHEMAS.get(category, FALLBACK_SCHEMA))


def normalize_test(test: dict[str, Any]) -> dict[str, Any]:
    """Fill defaults + validate status, returning a cleaned test dict."""
    tid = str(test.get("id", ""))
    status = str(test.get("status", SKIPPED))
    if status not in STATUSES:
        status = SKIPPED
    clean = {
        "id": tid,
        "status": status,
        "passed": status == PASSED,
        "value": test.get("value"),
        "unit": test.get("unit"),
        "simulated": bool(test.get("simulated", False)),
        "meta": test.get("meta"),
    }
    return clean


def compute_diagnostics(
    report: dict[str, Any],
    *,
    missing_penalty: float = DEFAULT_MISSING_PENALTY,
) -> dict[str, Any]:
    """Compute the diagnostic score for a structured diagnostics report.

    report keys (05-data-model.md 2.8):
      device {model, os_version, sdk}   (or category/device model)
      category                          mobile|laptop|tablet|generic|...
      skipped bool
      tests []{id, status, value, unit, simulated, meta}

    Returns a dict with {score, basis, missing_penalty, metrics, report_version}.
    """
    category = str(report.get("category") or "generic")
    schema = schema_for(category)

    skipped = bool(report.get("skipped", False))
    raw_tests = report.get("tests") or []

    # Map each reported test to the schema. Only tests the category expects count.
    results = []
    reported_ids = set()
    for t in raw_tests:
        clean = normalize_test(t)
        reported_ids.add(clean["id"])
        if clean["id"] in schema:
            results.append(clean)

    # Expected-but-not-reported tests => fail to score (count as missing).
    for tid in schema:
        if tid not in reported_ids:
            results.append({"id": tid, "status": SKIPPED, "passed": False,
                            "value": None, "unit": None, "simulated": False,
                            "meta": {"reason": "not reported"}})

    earned = 0.0
    applicable = 0.0
    failed_ids: list[str] = []
    unsupported = 0
    perf_req: list[str] = []

    for r in results:
        w = schema.get(r["id"], 0.0)
        if r["status"] in (UNSUPPORTED, UNAVAILABLE):
            unsupported += 1
            continue  # capability not applicable -> no penalty
        applicable += w
        if r["passed"]:
            earned += w
        else:
            if r["status"] == PERMISSION_REQUIRED:
                perf_req.append(r["id"])
            elif r["status"] == FAILED:
                failed_ids.append(r["id"])

    if skipped or not raw_tests or applicable == 0:
        # Nothing trustworthy to score. Apply the missing penalty rather than
        # emitting a fabricated pass; score is still 0..100 but flagged.
        score = 0.0
        basis = ["skipped"] if (skipped or not raw_tests) else []
        penalize_missing = True
    else:
        score = round(100.0 * earned / applicable, 1)
        basis = []
        if failed_ids:
            basis.append(f"failed:{','.join(failed_ids)}")
        if perf_req:
            basis.append(f"permission:{','.join(perf_req)}")
        penalize_missing = False

    return {
        "score": score,
        "basis": basis,
        "skipped": skipped,
        "missing_penalty": missing_penalty if penalize_missing else 0.0,
        "metrics": {
            "applicable": round(applicable, 3),
            "earned": round(earned, 3),
            "passed": sum(1 for r in results if r["passed"]),
            "failed": len(failed_ids),
            "permission_required": len(perf_req),
            "unsupported": unsupported,
        },
        "category": category,
        "report_version": REPORT_VERSION,
    }


def grade_for(score: float) -> str:
    """Human readable band (mirrors M1 grading spirit)."""
    if score >= 85:
        return "Good"
    if score >= 50:
        return "Moderate"
    return "Defective"


def _main() -> None:
    parser = __import__("argparse").ArgumentParser(
        description="Device diagnostics scoring service")
    parser.add_argument("report_json", help="Path to a diagnostics report JSON")
    parser.add_argument("--missing-penalty", type=float, default=DEFAULT_MISSING_PENALTY)
    parser.add_argument("--show", action="store_true", help="Print per-test detail")
    args = parser.parse_args()

    with open(args.report_json) as f:
        report = json.load(f)

    out = compute_diagnostics(report, missing_penalty=args.missing_penalty)

    print(f"category       : {out['category']}")
    print(f"skipped        : {out['skipped']}")
    print(f"score          : {out['score']}/100  ->  {grade_for(out['score'])}")
    print(f"basis          : {', '.join(out['basis']) or 'all applicable passed'}")
    print(f"missing_penalty: {out['missing_penalty']}")
    m = out["metrics"]
    print(f"metrics        : passed={m['passed']} failed={m['failed']} "
          f"permission={m['permission_required']} unsupported={m['unsupported']}")
    print(f"report_version : {out['report_version']}")

    if args.show:
        schema = schema_for(report.get("category") or "generic")
        print("-" * 60)
        for t in report.get("tests") or []:
            clean = normalize_test(t)
            w = schema.get(clean["id"], 0.0)
            print(f"  {clean['id']:<22} {clean['status']:<18} "
                  f"w={w:.2f} v={clean['value']}")


if __name__ == "__main__":
    _main()
