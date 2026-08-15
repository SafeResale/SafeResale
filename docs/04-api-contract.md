# 04 — API Contract

Version 1.0 • August 2026
Base URL (dev): `http://localhost:8000` · OpenAPI at `/docs`

## 1. Conventions

- All endpoints except `/auth/*` (and `/health`) require `Authorization: Bearer <access_token>`.
- Errors use HTTP status + JSON body: `{"detail": {"code": "...", "message": "...", "fields": {...}}}`
- Common status codes: `200` OK · `201` Created · `400` validation · `401` unauthenticated/expired ·
  `403` forbidden (role) · `404` not found · `409` conflict · `422` invalid payload · `429` rate-limited ·
  `500` internal.
- Dates as ISO-8601 UTC strings. IDs as MongoDB ObjectId hex strings.
- `X-Request-ID` echoed in headers for all responses.

## 2. Authentication

| Method | Path | Auth | Body / Query | Returns |
|---|---|---|---|---|
| POST | `/auth/register` | – | `{name, email, password, phone?}` | `201 {user, verify_required:true}` |
| POST | `/auth/verify` | – | `{email, code}` | `{verified:true}` |
| POST | `/auth/login` | – | `{email, password}` | `{access_token, refresh_token, user}` |
| POST | `/auth/refresh` | refresh | `{refresh_token}` | `{access_token, refresh_token}` |
| POST | `/auth/logout` | refresh | `{refresh_token}` | `204` |
| POST | `/auth/request-reset` | – | `{email}` | `202 {message}` |
| POST | `/auth/reset-password` | – | `{email, code, new_password}` | `200 {message}` |
| GET | `/auth/me` | access | – | `{user}` |
| PATCH | `/auth/me` | access | `{name?, phone?, ...}` | `{user}` |

**Token model**
- Access token: JWT, TTL ≤ 15 min, claims `{sub, role, type:"access"}`.
- Refresh token: opaque, stored hashed in DB (or Redis), single-use + rotating, revocable.
- Android stores both in `EncryptedSharedPreferences`.

## 3. Listings

| Method | Path | Auth | Body / Query | Returns |
|---|---|---|---|---|
| POST | `/listings/create-draft` | seller | `{category, title, price, ...}` | `201 {listing}` |
| PATCH | `/listings/{id}` | owner | partial fields | `{listing}` |
| GET | `/listings/{id}` | any | – | `{listing}` |
| GET | `/listings` | any | `?category=&min_price=&max_price=&condition=&page=` | `{items, total, page, page_size}` |
| GET | `/listings/categories` | any | – | `[{category, schema}]` |
| GET | `/listings/drafts` | seller | – | `[listing]` |
| POST | `/listings/{id}/submit` | owner | – | `{listing, status:"submitted"}` |

**Category schemas** (dynamic form definitions served by `/listings/categories`)
- `mobile`: price, year, brand, model, storage, battery_health, condition, notes
- `vehicle`: price, year, brand, model, odometer, condition, notes
- `accessory`: price, brand, type, condition, notes

**Status model:** `draft → capturing → submitted → verifying → {approved|review|blocked} → published|restricted`

## 4. Image Upload

| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| POST | `/listings/{id}/upload-token` | owner | `{angle, filename, content_type, size}` | `{upload_token, upload_url, expires_at}` |
| PUT | `upload_url` | – | raw image bytes | `200 {stored_key}` (or 204) |
| POST | `/listings/{id}/confirm-upload` | owner | `{upload_token, stored_key, angle, quality}` | `{listing_image}` |
| GET | `/listings/{id}/images` | owner/admin | – | `[listing_image]` |
| DELETE | `/listings/{id}/images/{image_id}` | owner | – | `204` |

**Rules**
- `upload-token` is single-use, expires (≤ 10 min), bound to listing + angle.
- Server validates MIME (image/jpeg, image/png, image/webp) and size (≤ 10 MB) on `confirm-upload`.
- On-device quality result is sent; the server re-runs OpenCV quality checks and merges both.

## 5. Verification Pipeline

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/listings/{id}/run-quality` | owner | `{quality_results}` |
| POST | `/listings/{id}/run-vision` | owner | `{detections, condition, simulated, model_versions}` |
| POST | `/listings/{id}/run-diagnostics` | owner | `{diagnostics_report, diagnostic_score}` |
| POST | `/listings/{id}/run-anomaly` | owner | `{signals, anomaly_score, explanation}` |
| POST | `/listings/{id}/compute-risk` | owner | `{risk_scores, decision}` |
| POST | `/listings/{id}/decision` | owner | `{decision, reason_code, reasons[]}` |
| POST | `/listings/{id}/rescore` | admin/inspector | `{risk_scores, decision}` |

**`run-vision` response**
```json
{
  "simulated": true,
  "detections": [
    {"image_id": "...", "angle": "front", "class": "scratch",
     "bbox": [x, y, w, h], "confidence": 0.87, "severity": "low"}
  ],
  "condition": {"class": "good", "probabilities": {"good": 0.9, "moderate": 0.08, "defective": 0.02}},
  "model_versions": {"detector": "stub-1.0", "classifier": "stub-1.0"}
}
```

**`compute-risk` response**
```json
{
  "physical_risk": {"score": 22, "confidence": 0.8, "basis": ["condition", "defects"]},
  "diagnostic_risk": {"score": 35, "confidence": 0.7, "basis": ["battery", "sensors"], "missing_penalty": 5},
  "behavioral_risk": {"score": 40, "confidence": 0.6, "basis": ["price_anomaly", "new_seller"]},
  "raw_score": 30.8, "adjusted_score": 33.1, "config_version": "risk-v1",
  "decision": {"status": "review", "reason_code": "REVIEW_PRICE_ANOMALY", "reasons": [...]}
}
```

## 6. Diagnostics Payload (from Android)

```json
{
  "simulated": false,
  "device": {"model": "Pixel 8", "os_version": "15", "sdk": 35},
  "tests": [
    {"id": "battery_level", "passed": true, "value": 82, "unit": "%", "simulated": false},
    {"id": "battery_health", "passed": true, "value": "good", "unit": null, "simulated": false},
    {"id": "wifi", "passed": true, "value": "connected", "unit": null},
    {"id": "bt", "passed": false, "value": "disabled", "unit": null},
    {"id": "gps", "passed": true, "value": "on"},
    {"id": "camera", "passed": true, "value": "captured"},
    {"id": "mic", "passed": true, "value": "waveform", "meta": {"peak_db": -6}},
    {"id": "speaker", "passed": true, "value": "played"},
    {"id": "touch_grid", "passed": true, "value": "9/9 cells"},
    {"id": "accelerometer", "passed": true, "value": "responding"},
    {"id": "gyroscope", "passed": true, "value": "responding"},
    {"id": "proximity", "passed": false, "value": "unsupported", "simulated": false}
  ],
  "skipped": false,
  "duration_ms": 8200
}
```

## 7. Admin

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/admin/kpis` | admin | `{total_listings, pending_review, high_risk, approval_rate, avg_risk, model_confidence, diag_failure_rate}` |
| GET | `/admin/listings/flagged` | admin | `?status=&category=&risk_band=&page=` → `{items, total}` |
| GET | `/admin/listings/{id}` | admin | full evidence: images, detections, condition, diagnostics, anomaly factors, risk history, audit trail |
| POST | `/admin/listings/{id}/review` | admin | `{action, reason, note?}` action ∈ `approve|warn|block|request_inspection|suspend_seller` → `{listing, review, new_status}` |
| POST | `/inspection/{id}/submit` | inspector | `{result, evidence_files[], note}` → triggers re-score |
| GET | `/admin/models` | admin | `{models: [{name, version, metrics, trained_at}]}` from `model_metrics` |
| GET | `/admin/audit-logs` | admin | `?actor=&action=&from=&to=&page=` → `{items, total}` |
| GET | `/admin/audit-logs/export` | admin | CSV download |

**Review actions**
| action | effect |
|---|---|
| `approve` | publish listing (if hard-stops pass) |
| `warn` | set status review, notify seller |
| `block` | status blocked, seller-safe reason |
| `request_inspection` | status inspection_pending |
| `suspend_seller` | seller suspended; their listings restricted |

## 8. Reports & Buyer

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/listings/{id}/report` | any | `{badge, verified_at, visual_summary, diagnostic_summary, seller_trust_tier, risk_band, breakdown, reasons[], annotated_images[]}` |
| GET | `/listings/{id}/report/export` | any | PDF/HTML download |

**Badge states:** `verified` · `review_passed` · `inspection_pending` · `restricted`

## 9. Audit Log Schema (write-only via API)

`audit_logs`: `{actor_id, actor_role, action, target_type, target_id, detail, ip, user_agent, request_id, created_at}`

## 10. Rate Limits (Redis-ready, memory default)

| Endpoint group | Limit |
|---|---|
| `/auth/login`, `/auth/verify`, `/auth/request-reset`, `/auth/reset-password` | 10 / 15 min / IP + 5 / 15 min / account |
| `/listings/*/upload-token` | 60 / 10 min / user |
| Admin review actions | 60 / 10 min / admin |
| General API | 300 / min / user |

## 11. Non-functional API guarantees

- All responses include `X-Request-ID`.
- Long `run-*` endpoints return the result synchronously in v1 (single process). If a job exceeds timeout, a
  `202 {job_id}` pattern may be adopted; the Android app polls `GET /listings/{id}` for evidence status.
- Invalid model inputs (corrupt/oversized images) return structured 422/400 and never crash the worker.
