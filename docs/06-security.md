# 06 — Security Requirements & Threat Model

Version 1.0 • August 2026

## 1. Threat Model

| # | Threat | Assets | Likelihood | Impact | Mitigation |
|---|---|---|---|---|---|
| T1 | Brute-force login / credential stuffing | user accounts | high | high | Argon2id, rate limits, OTP verify, account lockout escalation |
| T2 | Token theft / replay (access & refresh) | sessions | medium | high | short-lived access, rotating refresh, revoke on rotate, encrypted local storage, HTTPS |
| T3 | Privilege escalation (buyer → admin/inspector) | RBAC | low | high | role enforcement server-side on every endpoint; never trust client roles |
| T4 | Tampered evidence (fake/reused images) | verification integrity | high | high | enforced angles, duplicate hash, server re-validation, audit |
| T5 | Malicious uploads (oversized, wrong MIME, polyglot, decompression bomb) | storage, backend | medium | high | size + MIME validation, single-use upload tokens, image re-encode, reject corrupted |
| T6 | EXIF/GPS metadata spoofing as "proof of location" | trust | medium | medium | never trust EXIF; region from user profile only |
| T7 | Secret leakage (JWT secret, DB creds, cloud keys in APK/repo) | whole system | medium | critical | env-based secrets, `.gitignore`, CI secret scan, no secrets in APK |
| T8 | Abuse of verification pipeline (run-vision spam, rescore loops) | compute | medium | medium | rate limits, ownership checks, admin-only rescore |
| T9 | Data exposure (other users' listings/images) | PII, listings | medium | high | authorization checks on every route (ownership + role) |
| T10 | Model poisoning / adversarial inputs | AI pipeline | low | medium | validate inputs, frozen test set, human review of anomalies |
| T11 | Audit tampering | accountability | medium | high | append-only audit, no update/delete surface |
| T12 | DoS on auth (OTP flooding) | auth | medium | medium | rate limits + exponential backoff |

## 2. Controls (mapped to requirements R-SEC-01..10)

### 2.1 Authentication & secrets
- **Argon2id** for password hashing (OWASP-recommended, memory-hard). No MD5/SHA/plaintext.
- Access tokens: JWT, TTL ≤ 15 min, claims `{sub, role, type}`. Signature key from env (`JWT_SECRET`).
- Refresh tokens: opaque random, stored **hashed** (SHA-256) in `refresh_tokens`, single-use + rotating, revocable.
- OTP/reset codes: random ≥ 6 digits, hashed at rest, TTL 15 min, single-use.
- Android: tokens only in `EncryptedSharedPreferences` (Keystore-backed). Never store password or refresh secret in plaintext/SharedPreferences. HTTPS-only base URL.

### 2.2 Authorization (RBAC)
- Roles: `buyer` (default), `seller`, `inspector`, `admin`.
- Every endpoint declares allowed roles + ownership rules:
  - listing writes: `seller` + owner (or admin).
  - review actions: `admin` only.
  - inspection: `inspector` only.
  - admin data: `admin` only.
- Client-received role is never trusted; enforcement is server-side.

### 2.3 Rate limiting
- Implemented via `Cache` abstraction (memory default, Redis when configured).
- Limits: auth endpoints 10/15min/IP + 5/15min/account; upload-token 60/10min/user; review 60/10min/admin; general 300/min/user.
- Response: `429` with `Retry-After`.

### 2.4 Upload security
- Single-use upload token bound to listing + angle, TTL ≤ 10 min, created by `POST /listings/{id}/upload-token`.
- Server validates content-type (jpeg/png/webp), size ≤ 10 MB, and re-decodes/re-encodes image on ingestion to strip payloads.
- Corrupted/oversized images → structured 422/400, never a crash.
- Stored keys are server-generated (never derived from client filename).

### 2.5 Data protection
- HTTPS-only in production; tokens never in query strings.
- Authorization checks on every listing/image route (ownership + role) → T9.
- EXIF/GPS never trusted as location proof → T6.

### 2.6 Secrets management
- `.env.example` committed; `.env` and secrets gitignored.
- No API keys / JWT secrets / cloud credentials in the Android APK or in git.
- CI includes a secret-scan step.

### 2.7 Audit & accountability
- `audit_logs` append-only; no public update/delete.
- Sensitive events: auth (register, login success/fail, logout, reset), listing create/update/submit, upload, vision/diagnostics/anomaly/risk runs, all admin actions, inspection submit, badge change.
- Each entry: actor, role, action, target, detail, ip, user_agent, request_id.

### 2.8 Input & model validation
- Pydantic v2 validation on all payloads.
- Model inputs validated (image dimensions, channels, file magic) before inference.
- Dangerous formats rejected; images re-encoded to a safe format.

## 3. Security Testing

| Test | Coverage |
|---|---|
| Auth tests | login brute-force → 429; expired token → 401; revoked refresh → 401; rotation reuse → 401 |
| RBAC tests | buyer hits admin route → 403; seller edits another's listing → 403/404 |
| Upload tests | wrong MIME, oversized, token reuse, expired token, corrupt image → rejected |
| Secret scan | CI scan fails on `JWT_SECRET=` in a committed file or APK string |
| Audit tests | admin action writes audit entry; no delete endpoint exists |
| Token storage (Android) | test asserts no plaintext token in SharedPreferences |

## 4. Production hardening (documented future)

- TLS termination at reverse proxy; HSTS.
- Redis with TLS + auth; Mongo Atlas with IP allowlist + encryption.
- Object storage: private bucket, presigned URLs only.
- Rotation playbook for `JWT_SECRET` and upload tokens.
- Dependency scanning (pip-audit, npm audit) in CI.
