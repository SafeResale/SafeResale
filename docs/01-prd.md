# 01 — Product Requirements Document (PRD)

**SafeResale** — AI-powered second-hand product verification, fraud detection and trust platform
Version 1.0 • August 2026

> This document is the product source of truth. It refines the original `prd.pdf` and records confirmed project decisions.
> Requirement IDs referenced here (`R-AUTH-01`, etc.) are defined and detailed in `02-requirements.md`.

---

## 1. Executive Summary

SafeResale is a mobile-first second-hand marketplace that reduces buyer/seller trust problems by combining guided product
photography, computer vision, device diagnostics, seller-behavior anomaly detection, a transparent risk score, admin review
and a verification badge. The system is presented as an integrated engineering and AI system, not merely a marketplace app.

**Confirmed decision:** The seller/buyer experience is a native **Android app** because the PRD's device diagnostics
(battery health, sensor state, mic/speaker tests, touch-grid, camera test) require native OS access that no web page can
provide. The **Next.js admin web app** provides moderation, inspection, model and audit surfaces.

## 2. Problem Statement

Second-hand marketplaces depend heavily on seller claims and buyer judgment. Damage may be hidden, device functionality may
be misrepresented, and suspicious seller behavior may be difficult to detect. SafeResale creates a repeatable verification
pipeline that produces evidence before a listing is trusted.

## 3. Project Objectives

- Provide a polished Android application for sellers and buyers.
- Force structured 8-angle evidence capture before visual verification.
- Detect visible defects and estimate overall physical condition.
- Run supported device diagnostics on Android.
- Detect behavioral and pricing anomalies without requiring labeled fraud data.
- Combine physical, functional and behavioral signals into a 0–100 risk score.
- Provide explainable approve/review/block decisions.
- Give admins a professional moderation and audit dashboard.
- Provide a buyer-facing verification report and trust badge.
- Demonstrate measurable ML performance, latency, security and system reliability.

## 4. Scope

### 4.1 Priority features

| Priority | Features |
|---|---|
| **P0 — Mandatory** | Authentication, listings, 8-angle capture, image quality checks, image preprocessing, defect detection, condition classification, device diagnostics, risk score, decision engine, admin dashboard, audit logs, tamper-evident image hashing (SHA-256 + server timestamp), upload integrity checksums |
| **P1 — Strong marks** | Seller anomaly detection, price anomaly, duplicate-image detection, text similarity, explainability, verification report, inspection workflow, unified "SafeResale Verified" badge + above-the-fold trust summary, device fingerprinting, AI-generated-image detection (pre-publish gate), simulated escrow + protection, structured dispute workflow |
| **P2 — Extension (out of MVP)** | Semantic search/recommendations, model monitoring, automated retraining pipeline, OCR phone-number scanning, verified-transaction reviews, trust-aware ranking |
| **Avoid for MVP** | Real money escrow, real KYC, production payment custody, unnecessary microservices |

**Confirmed decision:** P2 items are documented as future scope and will not be implemented in this iteration.
**Confirmed decision:** Simulated escrow and the dispute workflow are P1 (simulated, clearly labeled per academic-integrity rule) because forward-looking protection is the strongest trust lever in this market (see `11-competitor-analysis.md`).

## 5. Target Users

| Role | Needs |
|---|---|
| Seller | Create listing, capture evidence, run diagnostics, see verification status and reasons |
| Buyer | Browse products, inspect verification summary, see condition/diagnostic evidence and trust badge |
| Admin | Review high-risk listings, inspect AI evidence, override decisions, suspend sellers and audit actions |
| Inspector | Optional role for inspection cases, evidence upload and re-scoring |

## 6. Core End-to-End Flow

1. Seller registers and verifies contact information (email/phone via dev mailer).
2. Seller creates a category-specific listing draft.
3. App validates required fields and asking price.
4. App guides seller through eight required camera views (Android CameraX).
5. On-device checks reject blurry, over/under-exposed or duplicate images; retake only the failed angle. Live-capture-only — no gallery uploads — blocks stolen/AI-generated photos at the source.
6. Images are securely uploaded to backend storage (local storage now, S3-ready). Each image is sealed with a SHA-256 hash + server-side timestamp; upload integrity is verified against the client checksum.
7. Backend queues preprocessing and AI inference.
8. OpenCV standardizes images (real, server-side).
9. YOLO11 detects localized defects; a Keras/TensorFlow classifier estimates overall condition (pluggable provider — stub default).
10. Android runs device diagnostics where supported (native, real measurements).
11. Backend computes seller/price anomaly signals (real).
12. Risk engine combines normalized physical, diagnostic and behavioral scores.
13. **Authenticity gate:** images flagged as AI-generated or unverifiable are sent to review and are never shown to buyers without human clearance.
14. Decision engine returns Approved, Review/Warning or Blocked with reason codes.
15. Admin receives flagged cases and can override with an auditable reason; inspection can trigger re-scoring.
16. Approved listings carry a buyer-safe verification report, one unified trust badge, and a tamper-evident evidence summary.

## 7. Recommended Technology Stack — 2026

| Layer | Recommendation | Reason |
|---|---|---|
| Android | Kotlin + Jetpack Compose + Material 3 + CameraX + Coroutines/Flow + ViewModel | Modern native UI, camera workflow, maintainable architecture; native diagnostics access |
| Android architecture | Layered architecture + MVVM + unidirectional data flow | Easy testing and clear ownership |
| Backend | Python 3.12+ / FastAPI + Pydantic v2 | Fast development, typed APIs, excellent ML integration |
| Database | MongoDB Atlas (local Mongo in dev) | Flexible listing/verification documents |
| Cache/queue | Redis (in-memory abstraction in dev) | Rate limits, short-lived jobs, caching |
| Object storage | S3-compatible / Cloudflare R2 (local folder in dev) | Separate large images from documents |
| ML detection | Ultralytics YOLO11 (YOLOv8 baseline for comparison) | Current family; research value |
| ML classification | Keras 3 / TensorFlow, EfficientNetV2 or MobileNetV3 | Transfer learning, reproducible |
| Classical ML | scikit-learn Isolation Forest | Unsupervised seller anomaly detection |
| Computer vision | OpenCV | Image quality and preprocessing |
| Admin | Next.js + TypeScript + Tailwind CSS + shadcn/ui | Modern admin dashboard |
| API docs | OpenAPI/Swagger (FastAPI) | Demo and development contract |
| Testing | pytest + HTTPX + Android unit/UI tests + Playwright | Evidence for final report |
| DevOps | Docker + GitHub Actions (reference only) | Reproducible builds and CI |
| Observability | Structured JSON logs + request IDs + basic metrics | Reliability and viva discussion |
| Security | Argon2id, short-lived access tokens, refresh rotation, RBAC, rate limiting, signed uploads | Strong security without unnecessary complexity |

## 8. Architecture

Modular monolith backend. Easier for a four-person final-year team to build, test and explain. AI modules are isolated
behind a provider interface so they can later be split into workers.

```
Android app ─► FastAPI API ─► Auth / Listings / Verification Orchestrator / Diagnostics / Risk Engine
                                                              │
                    MongoDB + cache(Redis-ready) + storage(local, S3-ready)
                                                              │
              AI provider: stub (default) OR ai-worker (YOLO11 + Keras/TF)
                                                              │
                                    Next.js Admin Dashboard ─ same API
```

Detailed component view: `03-architecture.md`.

## 9. Android Product Requirements

### 9.1 Authentication
- Signup, login, logout, forgot/reset password and profile.
- Email/phone verification (dev mailer prints codes; dev-verify endpoint).
- Short-lived access token + rotating refresh token.
- Seller/admin/inspector roles.
- Rate-limited login, OTP and reset endpoints.
- Never store passwords or long-lived secrets locally (EncryptedSharedPreferences).
- Register sends a **hashed device fingerprint** (device identifier hashed with a server salt; raw ID never stored) used for multi-account/ban-evasion detection.

### 9.2 Listing Creation
- Category-driven dynamic forms for mobile, vehicle and accessory.
- Draft autosave and resume.
- Validation for price, year, battery health, odometer and required fields.
- Seller-declared condition stored separately from AI condition.

### 9.3 Guided 8-Angle Capture
- Front, back, left, right, top, bottom/charging port, front 45°, back 45°.
- Live guide overlay and progress checklist.
- **Live-capture-only**: the app camera must be used; gallery uploads are blocked (anti stolen/AI-image measure).
- On-device blur, brightness, glare and duplicate checks before upload.
- Retake only the failed angle.
- Offline queue and resumable upload (upload integrity verified by checksum).
- Store angle, timestamp, device model and processing status.
- **Tamper-evidence**: every uploaded image is sealed with a SHA-256 hash and a server-side capture timestamp; any edit invalidates the hash and is visible on the verification report.

### 9.4 Device Diagnostics (native, real)
- Battery percentage/health where Android exposes it; clearly label unavailable metrics.
- Wi-Fi, Bluetooth and GPS functionality.
- Camera capture test.
- Microphone recording/waveform test.
- Speaker test.
- Touch-grid coverage test.
- Accelerometer, gyroscope and proximity/supported sensors.
- Timeouts, retry controls and permission fallbacks.
- Do not claim exact battery health where the OS does not expose a reliable value.

### 9.5 Buyer Experience
- Browse listings and open listing detail.
- View verification summary, condition/diagnostic evidence and trust badge.

## 10. AI/ML Requirements

### 10.1 Image Quality (real, OpenCV)
- Laplacian variance for blur; mean luminance for exposure; saturated-pixel ratio for glare; perceptual/image hash for duplicates.
- Quality score retained as evidence.

### 10.2 Defect Detection (pluggable provider)
- Primary classes: scratch, crack, dent, screen damage, port damage, camera damage, body deformation.
- Inference over all eight views; bounding boxes, confidence, severity, capture angle.
- Merge repeated detections across views.
- Store annotated evidence for admin review.
- Report precision, recall, mAP50, mAP50-95 and per-class results.

### 10.3 Condition Classification (pluggable provider)
- Classes: Good, Moderate, Defective.
- Transfer learning with Keras/TensorFlow; compare at least two backbones.
- Train/validation/test split and augmentation.
- Report accuracy, precision, recall, F1 and confusion matrix.
- Aggregate eight-view probabilities using image-quality weights.

### 10.4 Research Upgrade
- Train a YOLOv8 baseline and compare with YOLO11 on the same held-out test set.
- Compare a lightweight classifier against a stronger transfer-learning backbone.
- Measure accuracy versus latency (engineering trade-off, not just a model name).

### 10.5 AI-Generated Image Detection (Pre-Publish Gate)
- Runs behind an **authenticity provider** (parallel to the vision provider): `stub` default, `real` later.
- Every uploaded image is checked **before a listing can be published or shown to buyers**.
- Returns a synthetic-risk signal: `{synthetic_risk, provenance, simulated}`.
- High-risk images → listing sent to review; never shown to buyers without admin clearance.
- Stub provider is deterministic and labeled `simulated: true`; real provider = fine-tuned classifier and/or a
  detection API (see `08-ml-plan.md`).

## 11. Device Diagnostics

Covered in §9.4. Key rule: never claim exact battery health where unreliable; label unavailable metrics; all real
measurements marked `simulated: false`.

## 12. Fraud & Anomaly Detection (real)

- Isolation Forest on: account age, listing frequency, price deviation, category switching, regional mismatch,
  complaint/return rate, duplicate images, repeated descriptions.
- Duplicate images via perceptual hashing; repeated descriptions via TF-IDF/cosine similarity or sentence embeddings.
- Price anomaly via category/brand/model median and deviation ratio.
- Cold-start handling: use rules until sufficient seller history exists.
- Contact-velocity feature: buyer accounts messaging freshly-published listings within minutes (scraping-bot signal).
- Device-fingerprint clustering: same hashed device behind multiple accounts is a strong multi-account/ban-evasion signal.
- Store top suspicious signals so every anomaly has an explanation.

## 13. Risk Engine

```
Risk = 0.40 × Physical Risk + 0.40 × Diagnostic Risk + 0.20 × Behavioral Risk
```

- Normalize every component to 0–100.
- Keep raw score and confidence-adjusted score separate.
- Missing-data penalty when diagnostics are skipped.
- Reduce confidence when image quality is poor.
- Version every scoring configuration.
- Persist score history so an admin/inspection change triggers re-scoring.

| Risk | Decision | Action |
|---|---|---|
| 0–30 | Approved | Publish if hard-stop rules pass |
| 31–60 | Review / Warning | Manual review or inspection |
| 61–100 | Blocked | Do not publish; show seller-safe reason |

## 14. Explainability — High-Mark Feature

- Never show only a mysterious risk number.
- Show score breakdown: physical, diagnostics, behavior.
- Show detected defects with annotated images.
- Show failed diagnostic tests.
- Show price anomaly as a simple comparison.
- Show high-level seller-risk reasons without exposing anti-fraud thresholds.
- Machine-readable reason codes for admins; friendly explanations for sellers/buyers.

## 15. Admin Dashboard (Next.js)

- KPIs: total listings, pending review, high-risk listings, approval rate, average risk score, model confidence, diagnostic failure rate.
- Flagged-listing queue with filters.
- Listing detail: images, defect boxes, condition probabilities, diagnostics, anomaly factors, risk history, audit trail.
- Actions: approve, warn, block, request inspection, suspend seller.
- Inspection workflow with evidence upload (triggers re-scoring).
- Model/evaluation page showing current model versions and real validation metrics.
- Export moderation logs and evaluation data.

## 16. Buyer Verification Report

- **One unified "SafeResale Verified" badge** (AI evidence + diagnostics + seller tier combined — no badge clutter).
  States: Verified / Review Passed / Inspection Pending / Restricted.
- Verification timestamp; visual condition summary; diagnostic summary; high-level seller trust tier.
- **Above-the-fold trust summary** on the listing: badge, verified-at time, diagnostic pass summary, seller trust tier,
  response time, account age, verified contact status.
- **Tamper-evidence block:** image hash, server-side capture timestamp, authenticity check result — so buyers can see the
  photo has not been altered since capture.
- Risk band without exposing sensitive fraud logic.
- Badge revoked if important listing evidence changes.

## 17. Simulated Escrow & Protection (P1 — simulated, clearly labeled)

**SafeResale Protection** demonstrates forward-looking buyer protection without handling real money (academic-integrity
rule: always labeled as an MVP simulation).

- Finite state machine: `initiated → payment_held → shipped → delivered → buyer_verified → released/refunded/disputed`.
- Immutable state-transition records with actor + timestamp (auditable).
- Buyer confirmation or timeout release; explicit dispute path from every held state.
- "How it works in production" explanation page (real payment gateway = documented future extension).
- Real KYC and real payment custody remain out of MVP.

### 17.1 Structured Dispute Workflow (simulated)
- Buyers raise a dispute with categorized intake (item not received / not as described / damaged / other) + evidence upload.
- SLA timer (e.g., seller 48h to respond) with auto-escalation; admin resolves with an auditable decision that cites evidence.
- Disputes and escrow states are immutable, logged, and clearly marked as simulated.

## 18. Data Model

Covered fully in `05-data-model.md`. Collections: users, listings, listing_images, detections, condition_predictions,
diagnostics, seller_behavior_features, risk_scores, inspection_reports, admin_reviews, transactions (future),
audit_logs, model_metrics.

## 19. API Contract

Covered fully in `04-api-contract.md`.

## 20. Development Phases

| Phase | Outcome |
|---|---|
| 1. Foundation | Repo, architecture, API contract, database schema, Android skeleton, CI |
| 2. Core app | Auth, listing draft, 8-angle capture, upload and listing storage |
| 3. AI v1 | OpenCV quality pipeline + baseline detector/classifier |
| 4. Diagnostics | Android tests + diagnostic score + backend sync |
| 5. Trust engine | Isolation Forest, price anomaly, duplicate/text similarity, risk engine |
| 6. Admin | Flagged queue, evidence viewer, review actions, audit logs |
| 7. Integration | End-to-end verification pipeline and buyer report |
| 8. Research | Baseline vs improved model comparison, ablation/latency experiments |
| 9. Hardening | Security, testing, performance, failure cases, documentation |
| 10. Final demo | Dataset, metrics, architecture diagram, paper, report, PPT, viva rehearsal |

## 21. What Will Get You More Marks

- Real end-to-end execution, not screenshots alone.
- Own dataset/annotation process where licensing permits.
- Baseline vs improved model comparison.
- mAP, precision, recall, F1, confusion matrix and inference latency.
- Ablation experiments: raw vs preprocessed; single-view vs eight-view aggregation.
- Explainability and failure cases.
- Clean architecture, API documentation, automated tests.
- Git branches, PRs/issues, commit history.
- Security controls and explicit threat model.
- Measured latency and upload/inference reliability.
- Reproducible experiment configuration and model versioning.

## 22. Academic Integrity Rule

Do not claim accuracy, dataset size, fraud-detection performance, or successful verification rates until actually
measured. If a module is simulated (e.g., diagnostics unavailable on a device), label it clearly. This makes the project
credible in a viva.

## 23. Final Positioning for Viva

Position SafeResale as an explainable, multi-signal trust engine for second-hand commerce. The novelty is not simply 'AI
detects damage'; it is the integration of visual evidence, functional diagnostics, seller behavior, price anomalies and
human review into a measurable risk decision with auditability.
