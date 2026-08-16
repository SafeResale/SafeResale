# 02 — Requirements Specification

This document defines every requirement as a numbered, verifiable item. Priorities use **MoSCoW**:
**M**ust have (P0), **S**hould have (P1), **C**ould have (P2/future), **W**on't have (out of MVP).

Each requirement has an acceptance criterion so it can be objectively verified. IDs link to PRD sections in `01-prd.md`.

---

## 1. Authentication (R-AUTH)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-AUTH-01 | M | User can sign up with name, email, password and optional phone. | `POST /auth/register` creates a user, hashes password with Argon2id, returns 201. |
| R-AUTH-02 | M | User can verify email/phone via a one-time code (dev mailer). | A dev-verify endpoint succeeds with the printed code; unverified users cannot login until verified (configurable). |
| R-AUTH-03 | M | User can log in with email + password. | `POST /auth/login` returns short-lived access token + refresh token only with valid credentials. |
| R-AUTH-04 | M | Access tokens expire quickly (default ≤ 15 min). | Expired access token is rejected with 401; refresh endpoint issues a new pair. |
| R-AUTH-05 | M | Refresh tokens rotate and can be revoked. | `POST /auth/refresh` invalidates the old refresh token; `POST /auth/logout` revokes the current pair. |
| R-AUTH-06 | M | Password reset via emailed code. | Request-reset → verify code → set new password flow works; token single-use. |
| R-AUTH-07 | M | Roles: user/seller, admin, inspector enforced by RBAC. | Endpoints declare required roles; non-authorized requests get 403. |
| R-AUTH-08 | M | Login, OTP and reset endpoints are rate-limited. | > N attempts per period from same IP/account returns 429 (N configurable). |
| R-AUTH-09 | M | Client (Android) stores tokens in EncryptedSharedPreferences; never stores passwords or long-lived secrets in plaintext. | Code review + test: no secret persisted in plaintext/SharedPreferences. |
| R-AUTH-10 | S | Profile view/update. | `GET /auth/me` and `PATCH /auth/me` work with validation. |
| R-AUTH-11 | S | Audit log for sensitive auth events (register, login fail/success, logout, reset). | Each event appends to `audit_logs` with actor, action, IP, timestamp. |

## 2. Listings (R-LIST)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-LIST-01 | M | Seller creates a category-specific listing draft (mobile, vehicle, accessory). | Dynamic form schema per category; draft persisted. |
| R-LIST-02 | M | Draft autosave and resume. | Unsaved form edits are recoverable; draft list shows resumable items. |
| R-LIST-03 | M | Validation for price, year, battery health (mobile), odometer (vehicle) and required fields. | Invalid values produce field-level errors and no save. |
| R-LIST-04 | M | Seller-declared condition stored separately from AI condition. | Listing stores `seller_condition` and AI writes to `condition_predictions` only. |
| R-LIST-05 | M | Listing status model: draft → capturing → submitted → verifying → approved/review/blocked → published/restricted. | Status transitions enforced by the backend; invalid transitions rejected. |
| R-LIST-06 | M | Price must be a positive number within sane category bounds. | Price ≤ 0 or wildly deviating rejected at creation. |
| R-LIST-07 | S | PATCH listing fields after creation (with draft/resubmission). | Owner can edit drafts; published listings require re-verification. |
| R-LIST-08 | S | Browse/public listing view with filter by category, condition band, price. | `GET /listings` returns only published listings with verified badge info. |

## 3. 8-Angle Capture (R-CAPTURE)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-CAPTURE-01 | M | App enforces exactly eight angles: front, back, left, right, top, bottom/charging port, front 45°, back 45°. | UI progress checklist shows all eight; capture state machine prevents skipping. |
| R-CAPTURE-02 | M | Live guide overlay shows product alignment for each angle. | Overlay drawn with CameraX preview; angle label + guidance shown. |
| R-CAPTURE-03 | M | On-device quality checks run before upload: blur (edge/Laplacian), exposure (luminance), glare (saturated-pixel ratio), duplicate (perceptual hash). | Each captured image gets a pass/fail result; failures trigger retake of only that angle. |
| R-CAPTURE-04 | M | Retake only the failed angle, keeping passed angles. | State machine marks individual angle status; upload proceeds with passed set. |
| R-CAPTURE-05 | M | Offline queue + resumable upload. | Interrupted upload resumes; images queue locally when offline. |
| R-CAPTURE-06 | M | Per-image metadata: angle, timestamp, device model, quality result, processing status. | `listing_images` documents contain all fields. |
| R-CAPTURE-07 | S | Auto-capture assist when product is framed and stable. | Optional flag; threshold-based auto-capture works. |

## 4. Image Quality & Preprocessing (R-QUALITY)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-QUALITY-01 | M | Server re-validates quality with OpenCV: Laplacian blur, mean luminance, saturated-pixel glare. | Quality results persisted and consistent with thresholds in config. |
| R-QUALITY-02 | M | Perceptual hash computed server-side for duplicate detection. | Duplicate pair detected across images in the same listing and across seller's listings. |
| R-QUALITY-03 | M | Quality score retained as evidence for confidence adjustment. | Quality scores stored on `listing_images` and consumed by risk engine. |
| R-QUALITY-04 | S | Preprocessing standardizes image size/format before AI inference. | Inference receives resized/normalized tensors; config documents pipeline. |

## 5. Vision: Defect Detection & Condition (R-VISION)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-VISION-01 | M | Vision runs behind a pluggable provider interface. | Switching provider via config changes results without code changes. |
| R-VISION-02 | M | Stub provider returns deterministic, clearly-labeled outputs. | `run-vision` returns valid structured output; `simulated: true` flag set. |
| R-VISION-03 | S | Real provider (YOLO11) returns boxes for the core defect classes (14: scratch, crack, dent, screen_damage, glass_damage, camera_damage, port_damage, casing_damage, body_deformation, paint_damage, chip, rust, corrosion, water_damage — see docs/08-ml-plan.md §3.1). | Provider returns `{class, bbox, confidence, severity, angle}` per detection. |
| R-VISION-04 | S | Condition classifier returns Good/Moderate/Defective with probabilities. | `condition_predictions` doc stores class probabilities + model version. |
| R-VISION-05 | M | Detections merged across the eight views (duplicate/overlap suppression). | Same physical defect appearing in multiple angles yields one merged entry. |
| R-VISION-06 | M | Annotated evidence (boxes overlaid) stored for admin review. | Admin listing detail renders annotations. |
| R-VISION-07 | M | Inference failure is handled gracefully (timeout, retry, error evidence). | Failure returns structured error; listing stays in capturing state with retry. |

## 6. Device Diagnostics (R-DIAG)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-DIAG-01 | M | Diagnostics run natively on Android and report real measurements (`simulated: false`). | Each test returns `{test, passed, value, unit, simulated:false, measured_at}`. |
| R-DIAG-02 | M | Battery % and health reported only where the OS exposes reliable values; otherwise `unavailable`. | Battery health never claims a fabricated value. |
| R-DIAG-03 | M | Wi-Fi, Bluetooth, GPS connectivity tests. | Results reflect actual adapter state. |
| R-DIAG-04 | M | Camera capture, microphone recording (+ waveform), speaker playback tests. | Each returns pass/fail with measurement metadata. |
| R-DIAG-05 | M | Touch-grid coverage test (Compose). | Reports covered/uncovered grid cells. |
| R-DIAG-06 | M | Sensor tests: accelerometer, gyroscope, proximity (and any supported sensors). | Per-sensor result or `unsupported` on devices lacking the sensor. |
| R-DIAG-07 | M | Timeouts, retry controls, permission fallbacks. | A denied permission yields a clear `permission_required` result, not a crash. |
| R-DIAG-08 | M | Diagnostics can be skipped; missing data triggers a risk penalty (not a block by itself). | Risk engine applies missing-data penalty (see R-RISK-06). |
| R-DIAG-09 | M | Diagnostics report syncs to backend as `diagnostics` document. | Backend persists structured report and computes diagnostic score. |

## 7. Anomaly Detection (R-ANOM)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-ANOM-01 | M | Seller behavior features computed: account age, listing frequency, price deviation, category switching, regional mismatch, complaint/return rate, duplicate images, repeated descriptions. | `seller_behavior_features` doc written per scoring run. |
| R-ANOM-02 | M | Isolation Forest anomaly score (unsupervised). | Score returned and used in behavioral risk; no labeled fraud data required. |
| R-ANOM-03 | M | Duplicate images via perceptual hash across seller's listings. | Flagged duplicates surface as a top signal. |
| R-ANOM-04 | M | Repeated descriptions via TF-IDF/cosine similarity (or embeddings). | Similarity above threshold surfaces as a signal. |
| R-ANOM-05 | M | Price anomaly via category/brand/model median + deviation ratio. | Deviation computed vs real median; extreme deviations flagged. |
| R-ANOM-06 | M | Cold-start rules for new sellers with insufficient history. | New sellers get rule-based risk until enough history exists; documented. |
| R-ANOM-07 | M | Top suspicious signals stored so every anomaly has an explanation. | Explanation surfaces in admin and seller-safe reasons. |
| R-ANOM-08 | S | Synthetic anomaly test cases for the detector. | Unit tests with injected anomalies confirm detection. |

## 8. Risk Engine (R-RISK)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-RISK-01 | M | Risk = 0.40 × Physical + 0.40 × Diagnostic + 0.20 × Behavioral. | Formula implemented and unit-tested. |
| R-RISK-02 | M | Every component normalized to 0–100. | Boundary tests at 0 and 100 for each component. |
| R-RISK-03 | M | Raw score and confidence-adjusted score kept separate. | Both persisted on `risk_scores`. |
| R-RISK-04 | M | Confidence reduced when image quality is poor. | Low quality weight lowers adjusted score confidence. |
| R-RISK-05 | M | Missing-data penalty when diagnostics are skipped. | Skipped diagnostics raise risk (configurable amount). |
| R-RISK-06 | M | Scoring configuration versioned. | Config version stored on every score. |
| R-RISK-07 | M | Score history persisted; admin/inspection changes trigger re-scoring. | `rescore` endpoint recomputes and appends to history. |
| R-RISK-08 | M | Risk bands: 0–30 Approved, 31–60 Review, 61–100 Blocked. | Band boundary tests: 30→approved, 31→review, 60→review, 61→blocked. |

## 9. Decision Engine (R-DECISION)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-DECISION-01 | M | Decision returns Approved / Review / Blocked based on risk band. | Band mapping verified by tests. |
| R-DECISION-02 | M | Hard-stop rules can override/block regardless of score: duplicate images, missing required angles, critical defect, invalid metadata. | Each hard-stop rule unit-tested. |
| R-DECISION-03 | M | Machine-readable reason codes + friendly explanations. | Response contains both `reason_code` and friendly text. |
| R-DECISION-04 | M | Decisions persisted with actor/timestamp; changes audited. | `admin_reviews`/decision record + audit entry. |
| R-DECISION-05 | M | Approved listings publish only if hard-stop rules pass. | Published listing guarantees hard-stops passed. |

## 10. Explainability (R-EXPLAIN)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-EXPLAIN-01 | M | Buyer/seller view shows score breakdown (physical, diagnostics, behavior) — never a bare number. | Report payload contains breakdown. |
| R-EXPLAIN-02 | M | Detected defects shown with annotated images. | Annotated image URLs returned. |
| R-EXPLAIN-03 | M | Failed diagnostic tests shown. | List of failed/unsupported tests returned. |
| R-EXPLAIN-04 | M | Price anomaly shown as simple comparison (asked vs median). | Report includes both values. |
| R-EXPLAIN-05 | M | Seller-risk reasons shown at high level without exposing anti-fraud thresholds. | Friendly text, no raw thresholds. |
| R-EXPLAIN-06 | M | Reason codes machine-readable for admin; friendly for users. | Two rendering forms of the same decision. |

## 11. Verification Report & Badge (R-REPORT)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-REPORT-01 | M | Badge states: Verified / Review Passed / Inspection Pending / Restricted. | Badge derived from decision + status; deterministic. |
| R-REPORT-02 | M | Report includes verification timestamp, visual condition summary, diagnostic summary, seller trust tier, risk band. | Report endpoint returns all fields. |
| R-REPORT-03 | M | Badge revoked if important listing evidence changes. | Editing critical evidence invalidates verification and forces re-verification. |
| R-REPORT-04 | S | Report exportable/printable. | Admin or buyer can export report (PDF/HTML). |

## 12. Admin Dashboard (R-ADMIN)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-ADMIN-01 | M | KPIs: total listings, pending review, high-risk listings, approval rate, average risk score, model confidence, diagnostic failure rate. | `GET /admin/kpis` returns all values. |
| R-ADMIN-02 | M | Flagged queue with filters (status, category, risk band, date). | Filtered query works. |
| R-ADMIN-03 | M | Listing detail shows images, defect boxes, condition probabilities, diagnostics, anomaly factors, risk history, audit trail. | Admin listing detail loads all evidence. |
| R-ADMIN-04 | M | Actions: approve, warn, block, request inspection, suspend seller — each with required reason. | Actions persist, apply effects, and are audited. |
| R-ADMIN-05 | M | Inspection workflow with evidence upload triggers re-scoring. | Inspector submits → new risk score computed and visible. |
| R-ADMIN-06 | M | Model/evaluation page shows current model versions + validation metrics from real measurements. | Data read from `model_metrics` (no fabricated values). |
| R-ADMIN-07 | M | Export moderation logs and evaluation data. | Export endpoints return CSV/JSON. |
| R-ADMIN-08 | M | Admin-only RBAC enforced. | Non-admin gets 403. |

## 13. Audit & Observability (R-AUDIT)

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-AUDIT-01 | M | All security and moderation actions logged: auth events, listing changes, admin actions, inspection, state changes. | `audit_logs` collection with actor/action/target/IP/timestamp. |
| R-AUDIT-02 | M | Audit logs are append-only from the API's perspective. | No public endpoint updates/deletes audit records. |
| R-AUDIT-03 | S | Structured JSON logs with request IDs. | Each request generates a request ID; logs are machine-parseable. |
| R-AUDIT-04 | S | Basic metrics endpoint (health, counters). | `GET /health` includes DB/cache status; metrics available. |

## 14. Security (R-SEC)

Covered in detail in `06-security.md`.

| ID | Priority | Requirement | Acceptance criterion |
|---|---|---|---|
| R-SEC-01 | M | Argon2id password hashing. | No plaintext or MD5/SHA stored; password field is argon2 hash. |
| R-SEC-02 | M | TLS/HTTPS in production; tokens never in query strings. | Config documents TLS; app uses HTTPS base URL. |
| R-SEC-03 | M | Short-lived access tokens + rotating refresh tokens. | See R-AUTH-04/05. |
| R-SEC-04 | M | RBAC for seller/admin/inspector. | See R-AUTH-07. |
| R-SEC-05 | M | Rate limiting on auth + sensitive endpoints. | See R-AUTH-08; 429 responses tested. |
| R-SEC-06 | M | Upload validation: MIME, size, and single-use upload tokens. | Oversized/incorrect-type uploads rejected; token reuse rejected. |
| R-SEC-07 | M | EXIF/GPS metadata not trusted as proof of location. | Location never derived from image EXIF. |
| R-SEC-08 | M | No API keys, JWT secrets or cloud credentials in the Android APK or git. | Repo scan + APK inspection in CI. |
| R-SEC-09 | M | Model inputs validated; corrupted/oversized images rejected. | Malformed image → structured 422/400, no crash. |
| R-SEC-10 | M | Environment/secret management in deployment. | `.env` example committed; real secrets excluded via `.gitignore`. |

## 15. Non-Functional Requirements

| ID | Category | Requirement | Target |
|---|---|---|---|
| R-NFR-01 | Performance | API latency | Median < 300 ms, p95 < 1000 ms for non-AI endpoints (local dev, unloaded). |
| R-NFR-02 | Performance | AI inference latency | Reported per provider (stub < 50 ms; real model measured and documented). |
| R-NFR-03 | Reliability | End-to-end success rate across test listings | ≥ 95% on seeded demo listings. |
| R-NFR-04 | Reliability | Upload resumability | Interrupted upload resumes without data loss. |
| R-NFR-05 | Usability | 8-angle capture completion | All eight angles capturable with retakes; UI progress visible. |
| R-NFR-06 | Maintainability | Modular monolith; AI behind provider interface | Documented module boundaries; provider swap via config. |
| R-NFR-07 | Portability | Backend runs on Windows dev machine + Docker reference | Setup guide reproduced on a clean machine. |
| R-NFR-08 | Testability | CI runs lint + backend tests + web build | One command runs all checks. |
| R-NFR-09 | Accessibility | Admin web usable with keyboard; contrast AA | Basic a11y checks in web lint. |

## 16. Out of Scope (Won't Have for MVP)

- Real money escrow / payment custody.
- Real KYC / government ID verification.
- Semantic search and recommendations.
- Model monitoring dashboards and automated retraining.
- Microservice decomposition.
- Public mobile web app for buyers (buyer experience lives in the Android app; admin lives on web).

## 17. Requirements Traceability Matrix (summary)

| PRD section | Requirement IDs |
|---|---|
| 9.1 Authentication | R-AUTH-01..11 |
| 9.2 Listing creation | R-LIST-01..08 |
| 9.3 8-angle capture | R-CAPTURE-01..07 |
| 10.1 Image quality | R-QUALITY-01..04 |
| 10.2/10.3 Vision | R-VISION-01..07 |
| 9.4/11 Diagnostics | R-DIAG-01..09 |
| 12 Anomaly | R-ANOM-01..08 |
| 13 Risk engine | R-RISK-01..08 |
| 13 Decision engine | R-DECISION-01..05 |
| 14 Explainability | R-EXPLAIN-01..06 |
| 16 Verification report | R-REPORT-01..04 |
| 15 Admin dashboard | R-ADMIN-01..08 |
| 24 Security | R-SEC-01..10 |
| 25 Testing strategy | R-NFR-01..09 + `07-testing-plan.md` |
