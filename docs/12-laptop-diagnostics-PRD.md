# 12 — Laptop Diagnostics PRD (v1.0, Sep 2026)

**Sources:** `yuan05-afk/CheckMyDevice` (9 browser tests, MIT) for web + `Fahath0x/corev-sysinfo-benchmark` (40+ sensors, CPU/RAM/storage bench, Apache 2.0) for mobile. Cloned to `_refs/` for reference — concepts ported, not vendored verbatim.

## 1. Goal
Give sellers a **one-browser-tab, no-upload, permission-aware** diagnostics bench for **laptops/desktops** (and reuse the same scoring contract for mobiles). Accurate 0-100 score drives `diagnostic_risk` → `risk_engine` (05-data-model.md 2.8, R-DIAG-09, device-diagnostics-contract.md).

## 2. Which repo does what

| Device | Repo (cloned) | What we borrow | What stays |
|---|---|---|---|
| **Laptop/Desktop** | `CheckMyDevice` (React 19 + Vite 7 + Tailwind 4, Vercel) | 9 guided tests T-01..T-09, localStorage `checkmydevice-results`, CSP, no account/tracking, dashboard `untested/working/issue/unsupported` | Web APIs only: MediaDevices, Battery Status, Network Information, DeviceOrientation/Motion, Touch, Fullscreen — honest `unsupported` is valid |
| **Mobile (Android)** | `CoreV` (Kotlin 2.0 + Compose M3 + Hilt, MVVM) | 40+ sensor streaming (vendor/power/range/minDelay, X/Y/Z SI units), CPU core freq/governor, RAM/disk I/O bench (Sieve), battery mA/mV/°C + capacity lifecycle, network SSID/BSSID/RSSI, root/SELinux audit, JSON export | Glassmorphism UI not needed; keep our 11-test mobile Runner |

## 3. Laptop — 9 Tests Mapped to SafeResale Contract

CheckMyDevice `T-01..T-09` → `device-diagnostics-contract.md:3` `category=laptop` ids:

| T | CheckMyDevice | SafeResale `id` | Verifies | Contract status |
|---|---|---|---|---|
| T-01 | Keyboard (40%→108 layouts) | `keyboard` | All keys emit events | `passed` / `failed` / `unsupported` |
| T-02 | Mouse & Trackpad | `trackpad` | pointer move, primary/secondary/middle, scroll | `passed`/`failed` |
| T-03 | Camera | `camera` | stream state, resolution, user picks source | `passed`/`permission_required`/`unsupported` |
| T-04 | Microphone | `microphone` | waveform, live level, 5s playback Blob (tab memory) | `passed`/`permission_required` |
| T-05 | Speaker | `speaker` | left/right/stereo, sweep/melody via Web Audio | `passed`/`failed` |
| T-06 | Display | `display` | solid colors, gradient, sharpness grid, checkerboard, fullscreen | `passed`/`failed` |
| T-07 | Battery | `battery` | charge %, charging, time estimate, 1s telemetry (Battery API) | `passed`/`unavailable` |
| T-08 | Network | `connectivity` | online, reachability, latency/jitter, download via Cloudflare (≤100 MB, optional Vercel relay) | `passed`/`failed`→`wifi`+`bluetooth` in generic fallback |
| T-09 | Sensors | `sensor_*` | orientation, acceleration, multi-touch after user start | `passed`/`unsupported` |

Dashboard states: `untested / working / issue / unsupported` → `passed / failed / unsupported / skipped`. Results in `localStorage` only, never uploaded except final `{device, category, skipped, tests[]}` when seller clicks **Sync to SafeResale**.

## 4. Mobile — CoreV Accurate Telemetry

**Existing Runner** `android-app/diagnostics/DiagnosticsRunner.kt` does binary `BatteryManager.isCharging / WifiManager.isWifiEnabled` etc. **Upgraded with CoreV data sources** (`_refs/corev-sysinfo-benchmark/app/src/main/java/com/corev/sysinfo/data/{battery,cpu,ram,sensors,network,camera,audio}`):

- **Battery:** `BatteryManager BATTERY_PROPERTY_CURRENT_NOW` (mA), `VOLTAGE` (mV), `temperature` (°C), `technology`, `health`, `capacity mAh`, charge timeline.
- **CPU/RAM/Storage:** per-core `cur_freq / governor / online cores` (via `/sys/devices/system/cpu`), RAM `ActivityManager.MemoryInfo`, storage `StatFs` — local line charts 60s, optional Sieve single/multi-core bench for `benchmark_score`.
- **Sensors:** 40+ via `SensorManager` — vendor, version, power mA, maxRange, minDelay µs, 3-axis `x/y/z` with units (`m/s²`, `rad/s`, `μT`, `lx`, `hPa`).
- **Network:** SSID/BSSID/RSSI/linkSpeed, operator, `5G/4G`, local IP, Ping/DNS.

All remain `simulated:false` with `measured_at` ISO. Unsupported sensors stay `unsupported` (not penalized).

## 5. Web for Laptop Testing (SafeResale-owned, CheckMyDevice-inspired)

**Location:** `web/laptop-diagnostics/` (Vite + React 19 + Tailwind 4 + Radix, no auth, no tracking — same privacy model). Adapted from `CheckMyDevice/check-my-device` (≈30 modules) but stripped of `pnpm workspace` scaffolding.

**Flow:** Seller on laptop opens `https://saferesale.local/laptop-diagnostics` (or `web/laptop-diagnostics` dev `pnpm dev` → `http://localhost:5173`), runs 9 tests guided, sees dashboard, clicks **Sync to listing** → `POST /listings/{id}/run-diagnostics` with `{device:{model,os_version,sdk}, category:"laptop", skipped, tests:[{id,status,value,unit,simulated:false,meta}]}`.

**Privacy (CheckMyDevice model preserved):** MediaStreams never recorded/uploaded, mic 5s Blob revoked on exit, `localStorage checkmydevice-results` only, CSP `frame-ancestors *`, no analytics.

## 6. Backend Scoring (same for both)

`ml/device_diagnostics/services/diagnostics.py:142` `compute_diagnostics(report)` already implements `category=laptop|mobile|tablet|generic` → `score 0-100` + `missing_penalty` + `metrics {applicable, earned, passed, failed, permission_required, unsupported}` + `report_version 1.0`.

- `wifi`/`bluetooth` map from CheckMyDevice T-08 `connectivity` when generic.
- Mobile `battery` weight 0.20, laptop `battery` 0.18, `keyboard` 0.12, `trackpad` 0.10 etc. — `SCHEMAS` in that file (already 0.18/0.12/0.10).
- `benchmark_score` from CoreV (optional) stored in `tests[].meta.benchmark` — not yet weighted, but persisted for audit.

`risk_engine` consumes `diagnostic_risk {score, confidence, basis, missing_penalty}` per `09-phase-plan.md:5` (already `api/verification.py:62`).

## 7. Out of scope

- Manufacturer service tools/electrical testing (CheckMyDevice limitation — documented in PRD, honest `unsupported`).
- eClassify `payment-gateway` live charges — keep simulated escrow.

## 8. Repo hygiene

`_refs/` is gitignored for reference only. Ported concepts are credited above (MIT/Apache 2.0). No vendored build artifacts.
