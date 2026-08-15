# 07 — Testing Plan & Evaluation

Version 1.0 • August 2026

## 1. Strategy

Automated tests at four levels. All results are recorded and become part of the final report. **No metrics are claimed
until actually measured** (academic-integrity rule).

| Level | Tooling | Scope |
|---|---|---|
| Unit | pytest (backend), JUnit (Android) | scoring, quality functions, validation, anomaly features |
| Integration | pytest + HTTPX | API↔Mongo, API↔cache, provider swap, upload flow |
| E2E | pytest script + Playwright (admin web) | register → listing → 8 photos → AI → diagnostics → risk → admin |
| UI/Device | Android unit/UI tests (Compose UI tests) + manual device | capture flow, permission flows, offline/retry |

## 2. Backend Test Plan (pytest + HTTPX)

### 2.1 Auth (R-AUTH)
- register → 201; duplicate email → 409.
- login correct/incorrect; verify gate before login.
- access token expiry → 401; refresh rotation; logout revokes; reuse of rotated refresh → 401.
- reset-password full flow; code single-use.
- RBAC matrix: user/seller/inspector/admin × endpoint → allowed/403.
- Rate limit → 429 after threshold (login, verify, reset).

### 2.2 Listings (R-LIST)
- draft create per category; invalid price/year/battery/odometer → field errors.
- status transition machine: invalid transitions rejected (e.g., publish without approval).
- owner-only edits; non-owner → 403/404.
- publish requires approved decision.

### 2.3 Uploads & quality (R-CAPTURE, R-QUALITY)
- upload-token single-use; expired token rejected; wrong MIME/size rejected.
- 8-angle enforcement: submitting without all eight → hard-stop.
- duplicate-image upload → duplicate signal + hard-stop rule.
- OpenCV quality: known-blurry image → blur fail; dark image → exposure fail; high-glare → glare fail (fixture images in repo).

### 2.4 Vision (R-VISION)
- stub provider returns deterministic structured output with `simulated: true`.
- provider selection: set `VISION_PROVIDER=real` with fake weights → provider loads or fails gracefully; fallback documented.
- cross-view merge: two boxes on same defect across angles → one merged detection.
- malformed image input → 422, no crash.

### 2.5 Diagnostics (R-DIAG)
- payload validation; `simulated` flags preserved.
- diagnostic score from tests; unsupported tests don't crash.
- skipped diagnostics → missing penalty applied in risk (see 2.6).

### 2.6 Risk & decision (R-RISK, R-DECISION)
- formula test: `0.40P + 0.40D + 0.20B` exact.
- normalization boundaries (0, 100 per component).
- band boundaries: 30→approved, 31→review, 60→review, 61→blocked.
- missing-data penalty; confidence adjustment for low-quality images.
- config versioning: score stores version; bump changes scores.
- rescore: admin/inspection action inserts new `risk_scores` and decision.
- hard-stop overrides: duplicate images, missing angles, critical defect → block regardless of score.

### 2.7 Admin & audit (R-ADMIN, R-AUDIT)
- KPIs compute correctly on seeded data.
- review actions apply effects + write `admin_reviews` + audit entry.
- inspection submit → re-score.
- audit export CSV; no audit delete endpoint exists.

### 2.8 Adversarial cases
- Blurry image, duplicate image, missing angle, extreme price, brand-new seller, contradictory claims (declared good but heavy defects), corrupt image bytes, oversized payload, token replay.

## 3. Android Test Plan (JUnit + Compose UI tests)

| Area | Tests |
|---|---|
| Auth | token storage in EncryptedSharedPreferences (no plaintext); login/refresh/401 handling |
| Wizard | dynamic form validation; draft autosave/resume; state survives process death |
| Capture | 8-angle state machine; retake-only-failed; offline queue; resumable upload; permission-denied path |
| Quality | blur/exposure/glare/hash checks on fixture bitmaps |
| Diagnostics | battery unavailable → labeled; permission fallback; touch-grid result; sensor unsupported |
| UI | Compose tests: capture checklist, report screen, buyer badge screen |

## 4. Admin Web Test Plan (Playwright)

- Login → role gate (non-admin redirect/403).
- KPI dashboard renders seeded numbers.
- Flagged queue filtering.
- Listing detail shows annotated images + diagnostics + anomaly factors + risk history.
- Review action flow (approve/block/suspend) reflects in UI.
- Models page shows only real metrics from `model_metrics`.
- Audit page + export CSV.

## 5. Model Evaluation Plan

| Model | Metrics | Evidence |
|---|---|---|
| Defect detector | precision, recall, mAP50, mAP50-95, per-class | frozen held-out test set |
| Condition classifier | accuracy, macro precision/recall/F1, confusion matrix | frozen test set |
| Image quality | reject/accept accuracy on manually-labeled quality set | labeled set |
| Anomaly detector | synthetic anomaly detection tests + qualitative case analysis | injected anomalies |
| Risk engine | agreement with expert/manual review labels on test set | label set |

- All model results written to `model_metrics` via training scripts (no hand-written numbers).
- Experiments tracked with reproducible config (fixed seeds, recorded splits).

## 6. Reliability & Performance

| Metric | Target (dev, unloaded) | How measured |
|---|---|---|
| API latency (non-AI) | median < 300 ms, p95 < 1000 ms | pytest timer / locust |
| AI inference | reported per provider | provider logs |
| E2E success rate | ≥ 95% on seeded listings | E2E script |
| Upload resumability | interrupted upload resumes losslessly | Android test |

## 7. CI (optional but recommended)

GitHub Actions: lint (ruff/flake8, ktlint) + backend tests + web build + secret scan + (optional) Android assembleDebug.
