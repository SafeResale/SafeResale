# SafeResale

AI-powered second-hand product verification, fraud detection and trust platform.

SafeResale is a mobile-first second-hand marketplace that reduces buyer/seller trust problems by combining guided product
photography, computer vision, device diagnostics, seller-behavior anomaly detection, a transparent risk score, admin review
and a verification badge.

**Status:** Requirements phase (v1.0, August 2026). No production code yet.

## What this repo contains

| Path | Purpose |
|---|---|
| `docs/01-prd.md` | Product requirements document (source of truth for scope) |
| `docs/02-requirements.md` | Functional & non-functional requirements, prioritized & verifiable |
| `docs/03-architecture.md` | System architecture, components, data flow, AI-provider model |
| `docs/04-api-contract.md` | REST API endpoints, schemas, error codes, auth flows |
| `docs/05-data-model.md` | MongoDB collections, fields and indexes |
| `docs/06-security.md` | Threat model and security controls |
| `docs/07-testing-plan.md` | Test strategy and evaluation metrics |
| `docs/08-ml-plan.md` | Dataset plan, model approach, experiments |
| `docs/09-phase-plan.md` | Milestones and acceptance criteria |
| `docs/10-setup-guide.md` | Environment, tooling and run instructions |

## Product in one paragraph

Sellers create a category-specific listing and are guided through eight required camera angles. On-device checks reject
blurry, over/under-exposed or duplicate images before upload. The backend runs OpenCV quality checks, a pluggable AI vision
pipeline (defect detection + condition classification), Android device diagnostics, and seller/price anomaly detection. A
risk engine combines physical, diagnostic and behavioral signals into a 0–100 risk score, and a decision engine returns
Approved / Review / Blocked with reason codes. Admins review high-risk listings with full AI evidence and can override with
an auditable reason. Approved listings carry a buyer-facing verification report and trust badge.

## Key decisions (see docs for detail)

- **Backend:** FastAPI modular monolith, MongoDB, cache abstraction (Redis-ready), local file storage (S3-ready).
- **Clients:** Android app (Kotlin + Jetpack Compose + CameraX) for sellers/buyers — required because device diagnostics
  need native OS access. Next.js web app for the admin moderation dashboard.
- **AI:** OpenCV image quality, Isolation Forest, price anomaly, duplicate-image hash and text similarity are real from day
  one. YOLO11 defect detection and Keras/TensorFlow condition classification are behind a **pluggable provider** — a
  clearly-labeled deterministic stub until trained models are swapped in.
- **Scope:** P0 + P1 from the PRD. Escrow (real/simulated), semantic search and automated retraining are out of MVP scope.
- **Academic integrity:** no fabricated metrics. Simulations (e.g. diagnostics on devices that don't expose data) are always
  labeled as such.

## Getting started

See `docs/10-setup-guide.md`. Quick start (once implemented):

```
# Backend (from backend/)
python -m venv .venv && .venv\Scripts\activate
pip install -r requirements.txt
python -m app.main            # FastAPI on :8000, docs at /docs

# Android app: open android-app/ in Android Studio and run on device/emulator
# Admin web (from web/admin)
npm install && npm run dev    # Next.js on :3000
```

MongoDB must be running locally (see setup guide).

## Verification checklist

Reviewers should read in order: `01-prd.md` → `02-requirements.md` → `03-architecture.md` → `04-api-contract.md` →
`05-data-model.md` → `06-security.md` → `07-testing-plan.md` → `08-ml-plan.md` → `09-phase-plan.md` → `10-setup-guide.md`.
The Requirements Traceability Matrix in `02-requirements.md` links requirements back to PRD sections.
