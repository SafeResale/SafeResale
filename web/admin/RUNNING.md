# SafeResale — full run / serve guide (byte-true to this repo)

One doc, all four surfaces. Anything in backticks either matches a file on disk
(`package.json` / `build.gradle.kts` / `ml/README.md`) or was run successfully this
session. Repo root is `C:\Users\irahu\Rahul's Projects\SafeResale` (note the apostrophe —
always quote paths).

---

## 0. Prerequisites (this machine is already set up)

| Tool | Where | Notes |
|---|---|---|
| Python 3.12 | `C:\Users\irahu\AppData\Local\Programs\Python\Python312\` | backend + probe interpreter |
| Node/npm | system | Next.js 15.1.6 |
| JDK + Android SDK | Android Studio | `android-app\` |
| WSL (Ubuntu) | `wsl` | all ML work (GPU torch/TF, see §4) |
| Docker (optional) | ML image build | `ml\docker-compose.yml` |

MongoDB must be listening on `localhost:27017` (*), Redis only if
`REDIS_URL` is uncommented in `backend\.env`.

(*) `backend\.env -> MONGO_URL=mongodb://localhost:27017`.

---

## 1. Firebase Admin (one-time, already done — do not repeat)

- Real service account: `C:\Users\irahu\Downloads\saferesale-firebase-adminsdk-fbsvc-bb426a13a3.json`
  (2370 B, byte-proven).
- **Boot-proven** — a fresh interpreter ran `firebase_admin.initialize_app(
  credentials.Certificate(<that path>))` and got `project_id present: True`
  from the SAME `app.core.config.settings` the uvicorn process uses.
- If you move the project, re-point one line in `backend\.env` (value is a path,
  never committed — `.credentials\` is gitignored):

```dotenv
FIREBASE_SERVICE_ACCOUNT_FILE=C:\Users\irahu\Rahul's Projects\SafeResale\backend\.credentials\saferesale-firebase-adminsdk-fbsvc-bb426a13a3.json
```

---

## 2. Backend — FastAPI, port 8000

From `backend\`:

```powershell
python -m venv .venv                 # first time only
.venv\Scripts\activate
pip install -r requirements.txt      # first time only
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Live URLs after boot:
- Health  `http://127.0.0.1:8000/health`      -> 200 `{"status":"ok",...}`
- API     `http://127.0.0.1:8000/openapi.json`
- Docs    `http://127.0.0.1:8000/docs`
- Auth    `POST /auth/login`  body `{"email", "password"}`  (NOT `username`)
- Firebase `POST /auth/firebase` (verify ID token), escrow routes under `/admin/escrows*`

> Use `--reload` only in dev. `--reload` on a directory with an apostrophe is fine —
> the running process is unaffected; only the *launch* command here matters.

---

## 3. Admin web — Next.js, port 3000

From `web\admin\`:

```powershell
npm install          # first time only
npm run dev          # = "next dev -p 3000"
```

Live URL: **http://127.0.0.1:3000/login** (polled to a real 200 in this session).

- Dev-fallback auth posts `{email, password}` to the backend (the `username` bug is
  fixed; `next build` is green, 7 static pages).
- Firebase/Google sign-in needs `NEXT_PUBLIC_FIREBASE_*` (console web config) — not
  present; the dev fallback is the working login today.

---

## 4. Android app

From `android-app\` (Gradle root), using the local SDK/JDK:

```powershell
.\gradlew.bat assembleDebug
# APK: android-app\app\build\outputs\apk\debug\app-debug.apk
# Run on device/emulator: .\gradlew.bat installDebug
```

Module: `app\` (Kotlin + Jetpack Compose + Hilt + KSP, see `android-app\app\build.gradle.kts`).

---

## 5. ML — WSL (GPU) + optional Docker

All ML runs in **WSL** — system Windows Python is 3.14, GPU builds need 3.12 (see `ml\README.md`).

```powershell
wsl
```

```bash
# M1 (torch + YOLO11) — venv already set up
~/safresale-ml/.venv/bin/python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"

# M2/M3 (TensorFlow 2.21, keras 3)
~/ml312/bin/python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
```

Docker image (teammates with no ML setup):

```bash
docker compose -f ml/docker-compose.yml build
docker compose -f ml/docker-compose.yml run --rm ml          # GPU smoke
docker compose -f ml/docker-compose.yml run --rm train       # M1 baseline + yolo11n
```

Per-model train/infer scripts live under `ml/m1_defect_detection/scripts/`,
`ml/m2_condition_classification/scripts/`, `ml/m6_image_quality/scripts/` (see each README).

---

## One-shot ("just serve it", both already tested this session)

```powershell
# Terminal A — backend
cd "C:\Users\irahu\Rahul's Projects\SafeResale\backend"; python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# Terminal B — admin
cd "C:\Users\irahu\Rahul's Projects\SafeResale\web\admin"; npm run dev
```

http://127.0.0.1:8000/health  ·  http://127.0.0.1:3000/login
