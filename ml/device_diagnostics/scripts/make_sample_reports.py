"""Generate sample device diagnostics reports to exercise the scoring service.

Used for local testing / demos (09-phase-plan.md Phase 4: "diagnostics ingestion +
diagnostic score + missing-data handling"). The native measurement half (real
battery/camera/mic/... results on physical hardware) lives in the device app and
syncs a report shaped exactly like these -- 05-data-model.md 2.8.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

# realistic per-category test ids (subset of the schema in services/diagnostics.py)
CATEGORY_TESTS = {
    "mobile": ["battery", "camera", "microphone", "speaker", "touch", "wifi",
               "bluetooth", "gps", "sensor_accelerometer", "sensor_gyroscope",
               "sensor_proximity"],
    "laptop": ["battery", "display", "keyboard", "trackpad", "camera",
               "microphone", "speaker", "wifi", "bluetooth", "ports"],
    "tablet": ["battery", "display", "camera", "microphone", "speaker", "touch",
               "wifi", "bluetooth", "gps"],
    "generic": ["power", "connectivity", "inputs", "outputs", "camera"],
}

DEVICES = {
    "mobile": {"model": "Karbon A52 Plus", "os_version": "Android 13", "sdk": 33},
    "laptop": {"model": "Dell Inspiron 15", "os_version": "Windows 11", "sdk": ""},
    "tablet": {"model": "Samsung Tab A8", "os_version": "Android 12", "sdk": 31},
    "generic": {"model": "Sony PS5 Controller", "os_version": "", "sdk": ""},
}


def sample_value(test: str):
    values = {
        "battery": (82, "%"), "camera": (1, "ok"), "microphone": (0.5, "V"),
        "speaker": (1, "ok"), "touch": (240, "cells"), "wifi": (1, "connected"),
        "bluetooth": (1, "connected"), "gps": (1, "locked"),
        "sensor_accelerometer": (0.01, "g"), "sensor_gyroscope": (0.0, "rad/s"),
        "sensor_proximity": (0, "cm"), "display": (1, "ok"), "keyboard": (86, "keys"),
        "trackpad": (1, "ok"), "ports": (3, "ok"), "power": (1, "ok"),
        "connectivity": (1, "ok"), "inputs": (1, "ok"), "outputs": (1, "ok"),
    }
    return values.get(test, (1, "ok"))


def make_report(category: str, *, statuses: dict[str, str] | None = None,
                skipped: bool = False, unsupported: set | None = None,
                permission: set | None = None) -> dict:
    """Build a report. statuses overrides specific test -> status; default all passed."""
    unsupported = unsupported or set()
    permission = permission or set()
    statuses = statuses or {}
    tests = []
    for tid in CATEGORY_TESTS[category]:
        value, unit = sample_value(tid)
        if tid in unsupported:
            status, value, unit = "unsupported", None, None
        elif tid in permission:
            status, value, unit = "permission_required", None, None
        else:
            status = statuses.get(tid, "passed")
        tests.append({
            "id": tid, "status": status, "value": value, "unit": unit,
            "simulated": False, "measured_at": "2026-09-08T10:00:00Z",
        })
    return {
        "device": DEVICES[category],
        "category": category,
        "skipped": skipped,
        "tests": tests,
    }


def write(folder: Path) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    scenarios = {
        "mobile_all_pass.json": make_report("mobile"),
        "mobile_battery_fail.json": make_report("mobile",
            statuses={"battery": "failed"}),
        "mobile_denied_sensor.json": make_report("mobile",
            permission={"sensor_proximity"}),
        "mobile_no_gps.json": make_report("mobile", unsupported={"gps"}),
        "laptop_all_pass.json": make_report("laptop"),
        "laptop_keyboard_fail.json": make_report("laptop",
            statuses={"keyboard": "failed", "display": "failed"}),
        "tablet_all_pass.json": make_report("tablet"),
        "generic_all_pass.json": make_report("generic"),
        "skipped_report.json": {"device": DEVICES["mobile"], "category": "mobile",
                                "skipped": True, "tests": []},
    }
    for name, rep in scenarios.items():
        (folder / name).write_text(json.dumps(rep, indent=2), encoding="utf-8")
        print(f"wrote {folder / name}")


if __name__ == "__main__":
    write(Path(sys.argv[1]) if len(sys.argv) > 1
          else Path(__file__).resolve().parent / "sample_reports")
