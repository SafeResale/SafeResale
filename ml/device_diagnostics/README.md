# Device Diagnostics — backend scoring service

Computes a 0-100 diagnostic score from a structured diagnostics report for **any
device category** (mobile, laptop, tablet, generic), plus the missing-data penalty
for skipped diagnostics. This is the backend half of "device diagnostics"
(09-phase-plan.md Phase 4); the native measurement half lives in the device apps
and syncs reports shaped like `sample_reports/`.

**Contract:** the exact JSON shape + statuses + test ids are defined in
`docs/device-diagnostics-contract.md`. A native runner that emits that shape needs
**zero backend changes** to be scored.

## Files

```
ml/device_diagnostics/
  services/diagnostics.py        # score engine (stdlib-only, no deps)
  scripts/make_sample_reports.py # generate sample reports for any category
  scripts/test_diagnostics.py    # edge-case/boundary tests
  sample_reports/                # generated sample reports for demo/tests
```

## Run

```bash
# score a report
~/safresale-ml/.venv/bin/python services/diagnostics.py sample_reports/mobile_battery_fail.json

# per-test detail
~/safresale-ml/.venv/bin/python services/diagnostics.py sample_reports/laptop_keyboard_fail.json --show

# regenerate sample reports
~/safresale-ml/.venv/bin/python scripts/make_sample_reports.py sample_reports

# boundary tests
~/safresale-ml/.venv/bin/python scripts/test_diagnostics.py
```

## Scoring model

* `applicable` = tests the category expects AND the device reports.
* `earned` = sum of weights for `passed` tests.
* `score = 100 * earned / applicable`.
* `unsupported` / `unavailable` (capability absent) are **not penalized**.
* `failed`, `permission_required`, `skipped` reduce the score.
* Skipped/empty report -> score 0 + `missing_penalty` (consumed by the risk engine,
  R-RISK-05).

## Categories & weights

Defined in `services/diagnostics.py` (`SCHEMAS`). Currently: `mobile`, `laptop`,
`tablet`, `generic` (fallback). Add a category or tweak weights there; bump
`REPORT_VERSION` when weights change (R-RISK-06).
