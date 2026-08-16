# 05 — Data Model

Version 1.0 • August 2026
Database: MongoDB (local service in dev; Atlas-ready). Driver: Motor (async). All timestamps ISO-8601 UTC.

## 1. Conventions

- `_id`: ObjectId
- `created_at` / `updated_at` on every collection (managed by application).
- `status` enums documented per collection.
- Indexes listed per collection (TTL for short-lived docs where noted).

## 2. Collections

### 2.1 `users`
Identity, roles, verification and account-risk metadata.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `name` | string | required |
| `email` | string | unique, lowercased, indexed |
| `phone` | string? | optional |
| `password_hash` | string | Argon2id |
| `roles` | string[] | `seller`, `buyer`, `admin`, `inspector` (default: `buyer`) |
| `email_verified` | bool | |
| `phone_verified` | bool | |
| `status` | string | `active` \| `suspended` |
| `account_created_at` | datetime | for feature engineering |
| `region` | string? | declared region (not EXIF) |
| `listing_frequency` | meta | computed/updated by anomaly feature builder |
| `created_at`, `updated_at` | datetime | |

**Indexes:** `email` (unique), `roles`

### 2.2 `refresh_tokens`
Rotating refresh tokens (hashed).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `user_id` | ObjectId | |
| `token_hash` | string | SHA-256 of token, unique, indexed |
| `expires_at` | datetime | |
| `revoked_at` | datetime? | |
| `replaced_by` | string? | rotation chain |
| `created_at` | datetime | |

**Indexes:** `token_hash` (unique), `user_id`, `expires_at` (TTL)

### 2.3 `verification_codes`
Email/phone OTP and password reset codes (single-use).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `user_id` | ObjectId | |
| `kind` | string | `email_verify` \| `phone_verify` \| `reset_password` |
| `code_hash` | string | hashed |
| `expires_at` | datetime | |
| `used_at` | datetime? | |
| `created_at` | datetime | |

**Indexes:** `user_id+kind`, `expires_at` (TTL)

### 2.4 `listings`
Product data, seller claims, price and status.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `seller_id` | ObjectId | indexed |
| `category` | string | `mobile` \| `vehicle` \| `accessory` |
| `title` | string | |
| `description` | string | |
| `price` | number | validated |
| `attributes` | object | category-specific (year, brand, model, storage, battery_health, odometer…) |
| `seller_condition` | string | `good` \| `moderate` \| `defective` (seller-declared, kept separate from AI) |
| `status` | string | `draft` \| `capturing` \| `submitted` \| `verifying` \| `approved` \| `review` \| `blocked` \| `published` \| `restricted` \| `inspection_pending` |
| `hard_stops` | object[] | failed hard-stop checks with reason codes |
| `badge` | string? | `verified` \| `review_passed` \| `inspection_pending` \| `restricted` |
| `published_at` | datetime? | |
| `created_at`, `updated_at` | datetime | |

**Indexes:** `seller_id`, `category`, `status`, `price`, `created_at`

### 2.5 `listing_images`
Per-angle evidence.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `angle` | string | one of the 8 angles |
| `storage_key` | string | file key |
| `content_type`, `size_bytes` | string/number | |
| `captured_at` | datetime | |
| `device_model` | string? | |
| `quality` | object | `{blur, exposure, glare, hash, score, passed, method:"opencv"}` |
| `client_quality` | object? | on-device checks from the app |
| `status` | string | `uploaded` \| `processed` \| `failed` |
| `created_at` | datetime | |

**Indexes:** `listing_id`, `quality.passed`

### 2.6 `detections`
YOLO boxes (provider output), merged across views.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `image_id` | ObjectId? | source image (raw) |
| `class` | string | core defect class (docs/08-ml-plan.md §3.1): scratch/crack/dent/screen_damage/glass_damage/camera_damage/port_damage/casing_damage/body_deformation/paint_damage/chip/rust/corrosion/water_damage |
| `bbox` | [number×4] | xywh, normalized |
| `confidence` | number | |
| `severity` | string | low \| medium \| high |
| `angle` | string | capture angle |
| `merged_from` | ObjectId[] | detections merged into this one |
| `model_version` | string | |
| `simulated` | bool | |
| `created_at` | datetime | |

**Indexes:** `listing_id`

### 2.7 `condition_predictions`
Classifier probabilities + model version.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `class` | string | `good` \| `moderate` \| `defective` |
| `probabilities` | object | `{good, moderate, defective}` |
| `per_view` | object[] | per-angle probabilities |
| `quality_weights` | object | aggregation weights from image quality |
| `model_version` | string | |
| `simulated` | bool | |
| `created_at` | datetime | |

**Indexes:** `listing_id`

### 2.8 `diagnostics`
Device test outputs and score.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `device` | object | `{model, os_version, sdk}` |
| `tests` | object[] | `{id, passed, value, unit, simulated, meta}` |
| `skipped` | bool | |
| `score` | number | 0–100 |
| `missing_penalty` | number | applied by risk engine |
| `report_version` | string | |
| `created_at` | datetime | |

**Indexes:** `listing_id`

### 2.9 `seller_behavior_features`
Feature vector + anomaly outputs.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `seller_id` | ObjectId | indexed |
| `listing_id` | ObjectId | |
| `features` | object | account_age_days, listing_count, price_deviation_ratio, category_switches, regional_mismatch, complaint_rate, return_rate, duplicate_images, repeated_descriptions |
| `isolation_score` | number | −1..1 raw outlier score |
| `top_signals` | object[] | `{signal, value, severity, explanation}` |
| `cold_start` | bool | rule-based mode |
| `model_version` | string | |
| `created_at` | datetime | |

**Indexes:** `seller_id`, `listing_id`

### 2.10 `price_stats`
Category/brand/model medians (computed by price-anomaly service).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `key` | string | `mobile:Apple:iPhone 13` etc., unique |
| `median_price` | number | |
| `count` | number | |
| `updated_at` | datetime | |

**Indexes:** `key` (unique)

### 2.11 `risk_scores`
Raw/adjusted score, breakdown, version, history (append-only).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `physical_risk` | object | `{score, confidence, basis}` |
| `diagnostic_risk` | object | `{score, confidence, basis, missing_penalty}` |
| `behavioral_risk` | object | `{score, confidence, basis}` |
| `raw_score` | number | 0–100 |
| `adjusted_score` | number | 0–100 |
| `config_version` | string | `risk-v1` |
| `trigger` | string | `auto` \| `admin_review` \| `inspection` \| `rescore` |
| `created_at` | datetime | |

**Indexes:** `listing_id`, `created_at`

### 2.12 `decisions`
Decision engine output.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `status` | string | `approved` \| `review` \| `blocked` |
| `reason_code` | string | e.g. `REVIEW_PRICE_ANOMALY` |
| `reasons` | object[] | `{code, level, message}` |
| `hard_stops` | object[] | failing hard-stop rules |
| `risk_score_id` | ObjectId | links to risk_scores |
| `created_at` | datetime | |

**Indexes:** `listing_id`, `status`

### 2.13 `inspection_reports`
Inspector evidence and result.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `inspector_id` | ObjectId | |
| `result` | string | `pass` \| `fail` \| `needs_rework` |
| `evidence_keys` | string[] | uploaded files |
| `note` | string | |
| `rescored_risk_id` | ObjectId? | new risk_scores doc |
| `created_at` | datetime | |

**Indexes:** `listing_id`, `inspector_id`

### 2.14 `admin_reviews`
Actions, notes, previous/new states.

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `listing_id` | ObjectId | indexed |
| `admin_id` | ObjectId | |
| `action` | string | `approve` \| `warn` \| `block` \| `request_inspection` \| `suspend_seller` |
| `reason` | string | required |
| `note` | string? | |
| `prev_status` | string | |
| `new_status` | string | |
| `created_at` | datetime | |

**Indexes:** `listing_id`, `admin_id`, `created_at`

### 2.15 `audit_logs`
Security and moderation history (append-only from the API's perspective).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `actor_id` | ObjectId? | |
| `actor_role` | string? | |
| `action` | string | e.g. `auth.login`, `listing.update`, `admin.block`, `inspection.submit` |
| `target_type` | string? | `listing` \| `user` \| `image` … |
| `target_id` | ObjectId? | |
| `detail` | object? | |
| `ip` | string? | |
| `user_agent` | string? | |
| `request_id` | string? | |
| `created_at` | datetime | |

**Indexes:** `created_at`, `actor_id`, `action`, `target_type`

### 2.16 `model_metrics`
Real measured evaluation results (no fabricated values).

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `model_name` | string | `yolo11-defects` \| `condition-classifier` \| `isolation-forest` … |
| `version` | string | |
| `task` | string | `detection` \| `classification` \| `anomaly` |
| `metrics` | object | mAP50, mAP50-95, precision, recall, F1, accuracy, confusion matrix… |
| `dataset` | object | name, size, split sizes |
| `latency_ms` | object | mean, p50, p95 |
| `trained_at` | datetime | |
| `created_at` | datetime | |

**Indexes:** `model_name+version`

## 3. Integrity Rules

1. `seller_condition` (user-declared) never written by AI; `condition_predictions` only written by the vision provider.
2. `audit_logs` has no update/delete API surface.
3. `risk_scores` is append-only; a re-score inserts a new doc (history preserved).
4. Uploaded images are immutable after `confirm-upload` (new evidence → new image).
5. `decisions` reference the exact `risk_scores` document that produced them.
