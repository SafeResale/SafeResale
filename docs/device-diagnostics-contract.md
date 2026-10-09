# Device Diagnostics — Shared Report Contract v1.0

This is the single source of truth for the shape of a device diagnostics report.
It is the contract between:

- the **measurement producers** (Android native runner for mobiles/tablets, a
  Windows/OS runner for laptops, etc.) that run real tests on a physical device,
- the **backend scoring service** (`ml/device_diagnostics/services/diagnostics.py`)
  that turns a report into a 0-100 score + missing penalty,
- the **risk engine** that consumes `diagnostic_score` / `missing_penalty`
  (05-data-model.md 2.8, R-RISK-05).

A producer that emits a report conforming to this contract requires **zero
backend changes** to be scored. See `04-api-contract.md` `/listings/{id}/run-diagnostics`
for the transport.

---

## 1. Report envelope

```jsonc
{
  "device": { "model": "Karbon A52 Plus", "os_version": "Android 13", "sdk": 33 },
  "category": "mobile",                 // mobile | laptop | tablet | generic | <custom>
  "skipped": false,
  "tests": [
    { "id": "battery", "status": "passed", "value": 82, "unit": "%",
      "simulated": false, "measured_at": "2026-09-08T10:00:00Z", "meta": { "method": "BatteryManager" } }
  ]
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `device` | object | yes | `{model, os_version, sdk}` (05-data-model.md 2.8) |
| `category` | string | yes | device category; drives the test schema/weights |
| `skipped` | bool | yes | `true` when the seller skipped diagnostics entirely (R-DIAG-08) |
| `tests` | object[] | yes* | per-test results; `*` empty/omitted only when `skipped:true` |
| `tests[].id` | string | yes | a test id defined below |
| `tests[].status` | string | yes | one of the statuses below |
| `tests[].value` | number/string\|null | no | measured value |
| `tests[].unit` | string\|null | no | unit of `value` (empty for binary tests) |
| `tests[].simulated` | bool | yes | MUST be `false` for real measurements (R-DIAG-01) |
| `tests[].measured_at` | string\|null | no | ISO-8601 timestamp |
| `tests[].meta` | object\|null | no | producer-specific metadata (e.g. `{method, message, retries}`) |

## 2. Statuses (exhaustive)

| status | Meaning | Scored? | Backend behaviour |
|---|---|---|---|
| `passed` | capability confirmed working | yes (earns weight) | earns its weight |
| `failed` | capability present but not working | yes (not earned) | counts as failed |
| `permission_required` | capability present, user denied access (R-DIAG-07) | not passed | counts as "not validated", listed in basis |
| `unsupported` | capability absent on this device/OS (e.g. no GPS on a laptop) | ignored | **not penalized** |
| `unavailable` | OS does not expose a reliable value (e.g. battery health) | ignored | **not penalized** (R-DIAG-02) |
| `skipped` | test skipped (per-test / offline) | not passed | penalized for the skipped test |

Distinction that matters: `unsupported`/`unavailable` mean the capability never
applied (no penalty); `permission_required` and `skipped` mean it did apply but
wasn't verified (penalty). Never mark a real measurement `simulated:true`.

## 3. Test ids by category

The scoring service (`ml/device_diagnostics/services/diagnostics.py`) owns the
canonical weight table. Producers pick ids from the lists below for their category.

### mobile / tablet
`battery`, `camera`, `microphone`, `speaker`, `touch`, `wifi`, `bluetooth`, `gps`,
`sensor_accelerometer`, `sensor_gyroscope`, `sensor_proximity`
(tablets add `display`; laptops below)

### laptop
`battery`, `display`, `keyboard`, `trackpad`, `camera`, `microphone`, `speaker`,
`wifi`, `bluetooth`, `ports`

### generic (fallback for unexplored categories)
`power`, `connectivity`, `inputs`, `outputs`, `camera`

### Units
- battery: `%` (and `meta.health` for health where OS exposes it reliably — else omit)
- microphone: `dBFS`/`V` waveform peak; speaker: `ok`
- touch-grid: count of covered cells (`cells`) + `meta.total_cells`
- sensors: physical units (`g`, `rad/s`, `cm`, etc.)
- camera/display/keyboard/trackpad/ports/connectivity/inputs/outputs: `ok` or a numeric count

## 4. skipped vs empty

| Case | `skipped` | `tests` | Backend result |
|---|---|---|---|
| All tests ran, all passed | `false` | full list | score 100, no penalty |
| Some tests failed | `false` | full list | reduced score by weight |
| Diagnostics skipped entirely (R-DIAG-08) | `true` | `[]` | score 0 + `missing_penalty` |
| Empty/only unsupported | `false` | `[]` / all `unsupported` | score 0 + `missing_penalty` |

A missing (never-sent) report is treated the same as `{skipped:true, tests:[]}`.

## 5. Backend response (api/diagnostics)

`compute_diagnostics(report)` returns (see 05-data-model.md 2.8):

```jsonc
{
  "score": 80.0,            // 0-100
  "basis": ["failed:battery"],
  "skipped": false,
  "missing_penalty": 0.0,   // >0 when skipped/empty
  "metrics": { "applicable": 10.0, "earned": 8.0, "passed": 10,
               "failed": 1, "permission_required": 0, "unsupported": 0 },
  "category": "mobile",
  "report_version": "1.0"
}
```

## 6. Versioning

- `report_version` on the backend response reflects scoring weights/config (bump
  when weights change — R-RISK-06).
- Producers SHOULD also record their own app version in `device.os_version` /
  `tests[].meta`; the backend does not validate producer versions.

## 7. Producer responsibilities (native runners)

- Mobiles/tablets (Kotlin + Compose + CameraX): `BatteryManager` (health only where
  reliable), `WifiManager`/`BluetoothAdapter`/`LocationManager`, `Camera2`/CameraX
  capture, `AudioRecord` (mic waveform) + `AudioTrack`/`MediaPlayer` (speaker),
  Compose touch-grid, `SensorManager`.
- Laptops (Windows/OS runner): `Win32_Battery`, display panel, keyboard/trackpad,
  camera, mic/speaker, network interfaces.
- Every test MUST report `simulated:false` with a real measurement (R-DIAG-01).
