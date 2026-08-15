# 10 — Setup Guide

Version 1.0 • August 2026

## 1. Prerequisites (this machine)

| Tool | Status on dev machine | Notes |
|---|---|---|
| MongoDB | ✅ running as a local service | `mongosh` available; connect at `mongodb://localhost:27017` |
| Android Studio | ✅ installed | Bundled JBR at `C:\Program Files\Android\Android Studio\jbr` (Java 25) |
| Android SDK | ✅ installed | `%LOCALAPPDATA%\Android\Sdk`, platforms 31→37, build-tools, emulator, system-images |
| Node.js | ✅ v24 + npm | For admin web (Next.js) |
| Python | ✅ 3.14 | ML heavy deps may need 3.12 venv (see §7) |
| Docker | ❌ not installed | Optional reference compose only — dev does not require it |

## 2. Directory layout (target)

```
saferesale/
  backend/     FastAPI app (python) + tests
  android-app/ Android app (Kotlin + Compose + CameraX)
  web/admin/   Next.js admin dashboard
  ml/          datasets, preprocessing, detection, classification, anomaly, experiments
  ai-worker/   optional model-serving service
  infra/       optional reference docker-compose.yml, .env.example
  docs/        this documentation set
```

## 3. Backend (FastAPI)

```powershell
cd backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env      # then edit values
uvicorn app.main:app --reload --port 8000
```

- API docs: http://localhost:8000/docs
- Health: http://localhost:8000/health
- Mongo is assumed at `MONGO_URL=mongodb://localhost:27017/saferesale`.

### Required env vars (`.env`)
| Var | Default | Purpose |
|---|---|---|
| `MONGO_URL` | `mongodb://localhost:27017` | Mongo connection |
| `DB_NAME` | `saferesale` | database |
| `JWT_SECRET` | (random) | access-token signing |
| `JWT_TTL_ACCESS` | `900` | seconds (≤15 min) |
| `JWT_TTL_REFRESH` | `2592000` | seconds (30 days) |
| `CACHE_DRIVER` | `memory` | `memory` \| `redis` |
| `REDIS_URL` | – | when `redis` |
| `STORAGE_DRIVER` | `local` | `local` \| `s3` |
| `STORAGE_DIR` | `uploads/` | local folder |
| `VISION_PROVIDER` | `stub` | `stub` \| `real` |
| `ML_WEIGHTS_DIR` | – | real provider weights |
| `DEV_VERIFY_ENABLED` | `true` | dev email verification bypass |
| `LOG_LEVEL` | `info` | logging |

## 4. Android app

1. Open `android-app/` in Android Studio.
2. Set `local.properties`:
   ```
   sdk.dir=C\:\\Users\\irahu\\AppData\\Local\\Android\\Sdk
   ```
   (Android Studio creates this automatically on first open.)
3. Base URL: `BUILD_CONFIG_API_BASE_URL=http://10.0.2.2:8000` for the emulator, or your machine's LAN IP for a
   physical device (`adb reverse tcp:8000 tcp:8000` also works).
4. Run on emulator (system images installed) or a physical device with USB debugging.

### Diagnostics note
Real measurements (battery, sensors, mic/speaker, touch-grid, connectivity) run natively. On emulators some values are
unavailable and will be reported as `unavailable`/`unsupported` with `simulated: false` — never fabricated.

## 5. Admin web (Next.js)

```powershell
cd web/admin
npm install
Copy-Item .env.example .env.local
npm run dev            # http://localhost:3000
```

Env: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8000`.

## 6. CLI build (without Android Studio)

The Gradle wrapper downloads Gradle; use the bundled JBR as `JAVA_HOME`:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd android-app
.\gradlew.bat assembleDebug     # build APK
.\gradlew.bat testDebugUnitTest # unit tests
```

## 7. Python version note for ML

System Python is 3.14. `torch`, `ultralytics`, and TensorFlow may lack 3.14 wheels. For the ML/`ai-worker` track:

```powershell
py -3.12 -m venv ml\.venv312    # requires Python 3.12 installed (winget install Python.Python.3.12)
ml\.venv312\Scripts\Activate.ps1
pip install -r ml\requirements.txt
```

The backend's `VISION_PROVIDER=stub` keeps everything else running regardless of ML dependency availability.

## 8. Optional Docker reference

`infra/docker-compose.yml` (mongo, redis, backend, ai-worker, admin-web) is provided as a reference for machines with
Docker. This dev machine does not require it.

## 9. Common issues

| Issue | Fix |
|---|---|
| `java` not on PATH | Use Android Studio JBR: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` |
| App can't reach backend | Use `10.0.2.2` for emulator or `adb reverse tcp:8000 tcp:8000` |
| Mongo connection refused | Start the local MongoDB service (Services → MongoDB) |
| 3.14 wheels missing for ML | Use the 3.12 venv in §7 |
