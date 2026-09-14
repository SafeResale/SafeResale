# Viva — Device Diagnostics

**Owner:** rahulpandiyan + veereshkp · `docs/device-diagnostics-contract.md` ·
`ml/device_diagnostics/` · `05-data-model.md` §2.8 · R-DIAG-01..09 · 09-phase-plan.md Phase 4

This is your viva talking-track for the **device diagnostics** feature: what it is,
the two halves (measure + score), the contract we defined, what we built, what we
measured, and the problems we hit. Only report genuine progress (academic-integrity
rule).

---

## 1. What device diagnostics is, in one sentence

Device diagnostics checks that the **physical device being sold actually works** —
battery, camera, mic, speaker, connectivity, sensors/touch — so the buyer isn't sold
a phone or laptop whose screen, keyboard, or battery is silently dead. Its result is
one scored component of the overall risk decision.

## 2. Why we need it (and why it spans categories)

Photos only show *exterior* damage (that's M1/M2). Many sale-killing faults are
**functional**: a drained/failing battery, dead microphone, broken touch cells, a
keyboard key that doesn't register. The PRD calls this out as a **P0 mandatory**
feature (01-prd.md:46) and it's a differentiator vs normal classifieds
(11-competitor-analysis.md).

Crucially it is **not mobile-only**: the same idea applies to laptops (battery,
display, keyboard, trackpad, ports), tablets, and generic electronics. So we built
it **category-aware** — one scoring engine, per-category test vocabulary.

## 3. The two halves (this is the key mental model)

| Half | What it does | Where it lives | Built? |
|---|---|---|---|
| **Measure** | Runs *real* tests on the physical device (battery %, camera capture, mic waveform, speaker playback, touch-grid, sensors; or for laptops: display, keyboard, trackpad, ports). `simulated:false`. | Android app (Kotlin/Compose/CameraX) for mobiles; a Windows/OS runner for laptops | **Native runner NOT yet built** — needs the app project |
| **Score** | Takes the flat report and turns it into a 0-100 diagnostic score + a missing-penalty for skipped tests. | Backend service | **Built + tested** |

The honest position for viva: we built and tested the **scoring + contract** half in
this repo; the **measurement** half runs on-device and is pending the app project.

## 4. The report contract (the enabler)

`docs/device-diagnostics-contract.md` is the single shared JSON shape so that any
producer (Android, laptop runner, future) interoperates with the scorer with zero
backend edits. Key rules:

- **Statuses are exhaustive** and each maps to a crisp scoring meaning:
  `passed` (earns weight), `failed` / `permission_required` / `skipped` (not earned /
  penalized), `unsupported` / `unavailable` (**not penalized** — the capability never
  applied, e.g. no GPS on a laptop).
- **`simulated:false` is mandatory** on real measurements (R-DIAG-01). Battery
  health is only reported where the OS reliably exposes it, else `unavailable`
  (R-DIAG-02 — never fabricate a value).
- A skipped/empty report -> score 0 + `missing_penalty` (R-DIAG-08 / R-RISK-05): a
  seller who skips diagnostics is not given a free pass, but isn't hard-blocked either.

## 5. What we built

`ml/device_diagnostics/`:
- `services/diagnostics.py` — **stdlib-only** score engine. `compute_diagnostics(report)`
  returns `{score, basis, skipped, missing_penalty, metrics, report_version}`.
  Category schemas: `mobile`, `laptop`, `tablet`, `generic` (fallback). No library
  deps so it slots into a FastAPI service and runs in CI.
- `scripts/make_sample_reports.py` — builds realistic reports for every category +
  variant (all-pass, failures, denied sensor, no-GPS, fully skipped).
- `scripts/test_diagnostics.py` — 6 boundary/edge-case checks.
- `README.md` + the contract doc.

## 6. What we measured (verified, engine-level)

| Scenario | Score | Missing penalty | Notes |
|---|---|---|---|
| mobile all pass | 100 | 0 | Good |
| laptop keyboard+display fail | 73 | 0 | Moderate, basis `failed:display,keyboard` |
| mobile battery fail (20% weight) | 80 | 0 | Moderate, basis `failed:battery` |
| mobile sensor denied (permission_required) | 95 | 0 | basis `permission:sensor_proximity` |
| mobile no GPS (unsupported) | 100 | 0 | **not penalized** — correct |
| diagnostics skipped entirely | 0 | 5.0 | Defective + penalty for risk |

All 6 boundary checks pass: battery fail -> exact weight deduction; unknown category
falls back to `generic` without crashing; `unsupported` never penalizes;
`permission_required` counts as not-validated; empty report -> penalty. **The
0-100 scoring math and penalty semantics are verified.**

Honest caveat: we have **not** measured real device results (no Android/laptop
runner built yet), so "it works on a real Karbon phone" is NOT claimed — only the
scoring engine is verified against synthetic reports.

## 7. Flutter vs native — the decision we made

We chose **native Kotlin + Compose + CameraX** for the mobile diagnostics runner
(matching docs/01-prd.md:86). Reason: every measurement (BatteryManager, sensors,
Camera2, AudioRecord/AudioTrack) needs native platform APIs. Flutter would still
require writing those in Kotlin inside a platform plugin plus a Dart<->native bridge
per call — extra overhead with no measurement benefit. Native keeps it direct. (If
we ever need one Dart codebase across iOS/desktop, Flutter + a plugin is the fallback,
but diagnostics themselves stay native.)

## 8. Problems faced

1. **"Device diagnostics" isn't mobile-only.** The docs assumed Android-centric tests
   (touch-grid etc.). We generalized the scorer to be category-aware so laptops/
   tablets/generic work too.
2. **No app repo to put the native runner in.** This repo holds only `ml/` + `docs/`;
   there is no Android project. So the *measurement* half has no home yet. We scoped
   to the parts that belong here (contract + scorer) rather than pretending to have a
   working on-device runner.
3. **Battery health is unreliable across devices.** Solved per R-DIAG-02: report
   `unavailable` where the OS doesn't expose it; never fabricate. The scorer treats
   it as a legitimate non-penalty.
4. **Skipped vs unsupported confusion.** A naive scorer would penalize a laptop for
   "no GPS" — wrong. We made `unsupported`/`unavailable` non-penalizing and reserve
   penalties for `failed`/`permission_required`/`skipped`.

## 9. Quick answers for a panel

- **Q: How do you know a real device works?** → The native runner runs the actual
  sensor/hardware calls and sends `simulated:false` results; the backend trusts only
  those. (Pending the runner/app.)
- **Q: What if the seller skips diagnostics?** → Not blocked, but the missing-data
  penalty bumps risk (R-RISK-05) — skipping has a cost, and it's surfaced in the
  report.
- **Q: How is score computed?** → Weighted fraction of passed vs applicable tests
  for the category, 0-100. `unsupported` capabilities don't count against you.
- **Q: Which is harder, measure or score?** → Measure. Real hardware access +
  permissions + device variance are the hard part; scoring is deterministic math we
  already verified.

## 10. Definition of done (honest status)

- [x] Report contract (`docs/device-diagnostics-contract.md`)
- [x] Backend scoring service + category schemas (mobile/laptop/tablet/generic)
- [x] Sample-report generator + 6 boundary tests (all passing)
- [ ] Native measurement runner (Android Kotlin + laptop/Windows) — **blocked**: no app project in this repo
- [ ] End-to-end test: app → `/run-diagnostics` → scorer → risk on a physical device
- [ ] Risk-engine integration consuming `missing_penalty` (comes with Phase 5 / trust engine)
