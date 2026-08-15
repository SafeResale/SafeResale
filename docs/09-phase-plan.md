# 09 — Phase Plan

Version 1.0 • August 2026

## 0. Legend

Each phase has a **Done when** definition. Milestones tagged in git: `v0.1-core`, `v0.2-ai`, `v0.3-risk`, `v1.0-final`.
ML work is a parallel track (Phase ML) — it never blocks the app pipeline because the stub provider keeps the system green.

## 1. Phase 0 — Foundation

**Deliverables**
- Git repo + branch strategy (main / develop / feature/*, PRs required).
- `docs/` requirement set (this repository).
- Backend skeleton: FastAPI app, `core/config.py` (.env), `core/db.py` (Motor), health endpoint, OpenAPI at `/docs`.
- Cache + storage abstractions with memory/local implementations.
- pytest + HTTPX wired; CI lint job (optional).
- Android skeleton: Gradle wrapper, Kotlin + Jetpack Compose + Material 3 theme, navigation, Ktor/Retrofit client, DI.

**Done when**
- `GET /health` returns DB/cache/storage status.
- `pytest` runs green.
- Android app boots to a login screen on device/emulator.

## 2. Phase 1 — Auth

**Deliverables**
- Backend: register/verify/login/logout/refresh/reset/me; Argon2id; JWT access + rotating refresh; RBAC; dev mailer;
  rate limits; audit logging.
- Android: login/signup/reset screens, token store (EncryptedSharedPreferences), refresh handling, role routing.

**Done when**
- Full auth flow passes the R-AUTH test matrix (`docs/07-testing-plan.md` §2.1).
- Rate-limit → 429; rotated-refresh reuse → 401.

## 3. Phase 2 — Listings, Capture & Uploads

**Deliverables**
- Backend: category schemas, drafts, validation, status machine, upload tokens, OpenCV quality checks, image metadata.
- Android: listing wizard (dynamic forms), CameraX 8-angle capture with overlays + checklist, on-device quality checks,
  retake-per-angle, offline queue + resumable upload.

**Done when**
- E2E: create draft → capture 8 angles → quality checks → upload → `listing_images` in Mongo with quality evidence.
- Adversarial: blurry/duplicate uploads rejected or flagged per plan §2.3.

## 4. Phase 3 — Vision (stub-first)

**Deliverables**
- `VisionProvider` interface; `StubProvider` deterministic + labeled; cross-view merge; detections + condition evidence;
  annotated image storage; error handling.
- Android verification status screen showing evidence.

**Done when**
- `run-vision` returns structured evidence with `simulated: true`.
- Provider swap via env works (stub ↔ a fake real provider in tests).

## 5. Phase 4 — Diagnostics

**Deliverables**
- Android native diagnostics: battery (reliable-only), Wi-Fi/Bluetooth/GPS, camera, mic waveform, speaker, touch-grid,
  sensors; timeouts/retry/permission fallbacks.
- Backend: diagnostics ingestion + diagnostic score + missing-data handling.

**Done when**
- A real device run produces a full report with `simulated: false` and syncs to backend.
- Skipped diagnostics apply the risk penalty (Phase 5).

## 6. Phase 5 — Trust Engine

**Deliverables**
- Anomaly: feature builder, Isolation Forest, price median + deviation, duplicate hash, TF-IDF similarity, cold-start rules,
  top-signals explanation.
- Risk engine: formula, normalization, confidence adjustment, missing-data penalty, versioned config, score history + rescore.
- Decision engine: bands, hard-stop rules, reason codes, persistence.

**Done when**
- `compute-risk` → decision with explainable breakdown persists; band boundary + hard-stop tests pass.
- Admin/inspection rescore appends new history.

## 7. Phase 6 — Admin Web & Buyer Report

**Deliverables**
- Next.js admin: login/role gate, KPIs, flagged queue + filters, listing evidence detail, review actions, inspection
  workflow, models page, audit log + export.
- Buyer report + badge: badge states, report payload, revocation on evidence change; Android buyer screens + report view.

**Done when**
- Full E2E: seller captures → verification → admin reviews/blocks → buyer sees badge/report.
- Playwright admin flows pass.

## 8. Phase 7 — Hardening & Release

**Deliverables**
- Security pass per `06-security.md` (upload validation, RBAC sweep, secret scan).
- Adversarial test suite green; reliability/latency measurements recorded.
- Seed demo data + `README` run instructions; docs finalized (architecture diagram, report skeleton).

**Done when**
- Clean-machine setup guide works end-to-end.
- E2E success rate ≥ 95% on seeded listings.

## Phase ML — Research Track (parallel, non-blocking)

**Deliverables**
- Dataset collection + annotation + split (frozen test set).
- YOLOv8 baseline vs YOLO11; classifier backbone comparison; ablations (raw vs preprocessed, single vs 8-view).
- Latency-vs-accuracy measurement; `model_metrics` writes.
- Optional `ai-worker` serving service + `VISION_PROVIDER=real`.

**Done when**
- `model_metrics` contains real frozen-test numbers; admin Models page shows them; real provider swappable by config.

## Milestones

| Tag | Contents |
|---|---|
| `v0.1-core` | Phases 0–2 complete |
| `v0.2-ai` | Phase 3–4 complete (stub vision + diagnostics) |
| `v0.3-risk` | Phase 5 complete (trust engine) |
| `v1.0-final` | Phases 6–7 + ML track complete |
