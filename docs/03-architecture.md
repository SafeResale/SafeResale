# 03 — Architecture

Version 1.0 • August 2026

## 1. Principles

1. **Modular monolith backend.** One deployable FastAPI service with clear internal module boundaries. A four-person team
   can build, test and explain it; AI modules are isolated so they can be split into workers later.
2. **AI is pluggable.** The vision pipeline sits behind a provider interface. The default provider is a deterministic,
   clearly-labeled stub. A real provider (YOLO11 + Keras/TF) is swapped in via configuration with zero application changes.
3. **Storage is abstracted.** File storage behind an interface (local disk now, S3/MinIO/R2 later). Cache behind an
   interface (in-memory now, Redis later).
4. **Trust is explainable.** Every risk decision carries a breakdown, reason codes, and evidence references.
5. **No fabricated data.** Simulations are always labeled. Model/evaluation pages show only measured values.

## 2. Component Diagram

```
┌─────────────────────────────┐        ┌──────────────────────────────────┐
│  Android app (seller/buyer) │        │  Next.js Admin Web               │
│  Kotlin + Compose + CameraX │        │  TypeScript + Tailwind + shadcn  │
│  auth · wizard · 8-angle    │        │  KPIs · queue · evidence ·       │
│  capture · quality · diag   │        │  actions · inspections · models  │
│  status · buyer report      │        │  · audit                          │
└─────────────┬───────────────┘        └───────────────┬──────────────────┘
              │ HTTPS + JWT (access + refresh)          │
              ▼                                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│ FastAPI backend — modular monolith                                     │
│                                                                        │
│  api/            routers: auth, listings, uploads, vision,             │
│                  diagnostics, anomaly, risk, decisions, admin,         │
│                  inspections, reports, audit                            │
│  core/           config(.env) · security (Argon2id/JWT/RBAC) · db      │
│                  (motor) · cache (memory|redis) · logging · rate_limit  │
│  services/       storage · image_quality(OpenCV) · mailer(dev) ·       │
│                  vision_provider · diagnostics · anomaly · risk ·      │
│                  decision · verification orchestrator · explainability │
│                                                                        │
│  Verification Orchestrator:                                            │
│   validate → quality → vision → diagnostics → anomaly → risk →        │
│   decision → report/badge                                              │
└───┬──────────────┬──────────────────┬───────────────────┬─────────────┘
    │              │                  │                   │
    ▼              ▼                  ▼                   ▼
 MongoDB        Cache             Storage            AI provider
 (local,       (memory,          (local files,      (stub default)
 running)       Redis-ready)      S3-ready)
                                                     ai-worker (optional)
                                                     YOLO11 + Keras/TF
```

## 3. Module Boundaries (backend)

| Module | Responsibility | Depends on |
|---|---|---|
| `api/auth` | Register/login/logout/refresh/reset/me, dev-verify | core.security, services.mailer |
| `api/listings` | CRUD drafts, validation, status transitions | models, core |
| `api/uploads` | Upload tokens, MIME/size validation, store image + metadata | services.storage |
| `api/vision` | Trigger quality + inference, persist detections/condition | services.vision_provider, services.image_quality |
| `api/diagnostics` | Accept diagnostics reports, compute diagnostic score | services.diagnostics |
| `api/anomaly` | Compute behavior features + anomaly signals | services.anomaly |
| `api/risk` | Compute risk + rescore | services.risk |
| `api/decisions` | Decision engine + reason codes | services.decision |
| `api/admin` | KPIs, queue, listing detail, review actions, models, audit export | services, models |
| `api/inspections` | Inspection workflow + evidence upload | services.risk (rescore) |
| `api/reports` | Buyer verification report + badge | services.explainability, services.risk |
| `api/audit` | Read/export audit logs | models |

## 4. Key Flows

### 4.1 Image capture → evidence
```
Android CameraX → on-device checks (blur/luminance/glare/hash)
  → request upload token (POST /listings/{id}/upload-token)
  → upload image → listing_images {angle, meta, quality, status}
  → POST /listings/{id}/run-vision
      → OpenCV quality recheck (real)
      → vision provider (stub|yolo11) → detections + condition
      → cross-view merge → persist detections + condition_predictions
```

### 4.2 Diagnostics → risk
```
Android native tests (BatteryManager, ConnectivityManager, SensorManager,
MediaRecorder, AudioTrack, Compose touch-grid)
  → structured report {test, passed, value, unit, simulated:false, ...}
  → POST /listings/{id}/run-diagnostics → persist diagnostics doc
```

### 4.3 Full verification
```
verify(listing):
  quality = check_image_quality(images)                 # real OpenCV
  vision  = run_vision(images)                          # provider
  diag    = get_diagnostics(listing) or {skipped:true}  # native report
  behavior= anomaly.build_features(seller, listing)     # real
  risk    = risk_engine.compute(physical, diagnostic, behavioral)
  decision= decision_engine.decide(risk, hard_stops)
  persist risk_scores + decision + audit
  if approved: publish + issue badge/report
```

## 5. Provider Interface (vision)

```python
class VisionProvider(Protocol):
    def detect_defects(self, images: list[ImageInput]) -> list[Detection]:
        ...  # {class, bbox, confidence, severity, angle, image_id}

    def classify_condition(self, images: list[ImageInput]) -> ConditionResult:
        ...  # {good|moderate|defective, probabilities, model_version}

    def simulated(self) -> bool: ...
```

- **StubProvider** — deterministic, seeded outputs; `simulated = True`; always available; used by default and CI.
- **Yolo11Provider** — loads Ultralytics YOLO11 weights (supplied by training); `simulated = False`.
- **KerasProvider** — loads trained condition classifier; `simulated = False`.
- Selection via `VISION_PROVIDER=stub|real` in `.env`. The real provider may run in-process or as a separate
  `ai-worker` service (see §8).

## 6. Storage & Cache Abstractions

### FileStorage
```python
class FileStorage(Protocol):
    def create_upload_url(self, key: str, content_type: str) -> UploadSpec: ...
    def get_public_url(self, key: str) -> str: ...
    def delete(self, key: str) -> None: ...
```
- **LocalStorage**: uploads to `backend/uploads/` via a single-use token flow; serves files locally.
- **S3Storage**: presigned PUT + public/read URL (MinIO/R2/S3). Swap via `STORAGE_DRIVER=local|s3`.

### Cache
```python
class Cache(Protocol):
    async def get(self, key) -> bytes | None
    async def set(self, key, value, ttl=None)
    async def incr(self, key, expire) -> int
    async def exists(self, key) -> bool
```
- **MemoryCache**: asyncio-safe dict with TTL (dev default).
- **RedisCache**: real Redis client (`CACHE_DRIVER=memory|redis`).

## 7. Data Flow & Consistency

- Writes flow through API → Motor (async pymongo) → MongoDB (local, already running).
- Jobs (quality, vision, anomaly) are short-lived and invoked synchronously via API in v1 (single process).
  A `run-*` request may be long-running; the Android app polls listing status until evidence lands.
- Score history is append-only: `risk_scores` holds raw + adjusted + version + timestamp.

## 8. Deployment (reference)

- **Dev (this machine):** backend `uvicorn` on :8000, Android app in Android Studio, admin web `next dev` on :3000.
  Mongo local service. Cache/storage default to in-memory/local. No Docker required.
- **Reference Docker Compose** (`infra/docker-compose.yml`, optional): `mongo`, `redis`, `backend`, `ai-worker`,
  `admin-web`. Provided for reproducibility/demo on machines with Docker.
- **Production (future):** HTTPS via reverse proxy, Mongo Atlas, Redis, S3/R2, env-based secrets.

## 9. Observability

- Structured JSON logs with `request_id` (middleware-generated).
- `GET /health` returns status of DB + cache + storage.
- Basic counters (requests, errors, inference calls) in memory; exportable.

## 10. Non-Goals (v1)

- Microservices; multi-region; message queues; semantic search; ML training in the serving process.
