# 10 — Setup Guide

Version 1.0 • August 2026

## 1. Prerequisites (this machine)

| Tool | Status on dev machine | Notes |
|---|---|---|
| MongoDB | ✅ running as a local service | `mongosh` available; connect at `mongodb://localhost:27017` |
| Android Studio | ✅ installed | Bundled JBR at `C:\Program Files\Android\Android Studio\jbr` (Java 25) |
| Android SDK | ✅ installed | `%LOCALAPPDATA%\Android\Sdk`, platforms 31→37, build-tools, emulator, system-images |
| Node.js | ✅ v24 + npm | For admin web (Next.js) |
| Python | ✅ 3.14 (system) + 3.12 | ML heavy deps need 3.12 venv (see §7) |
| WSL | ✅ Ubuntu, python3.12, GPU passthrough | ML runs in WSL (see §7) |
| NVIDIA GPU | ✅ RTX 4050 (6 GB), driver 610.74 | CUDA training via WSL |
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

## 7. ML environment (WSL — recommended)

**Why WSL?** TensorFlow has no GPU pip wheels for native Windows (CPU-only since TF 2.11), and torch/ultralytics/tensorflow
need Python 3.12 (system Python is 3.14). WSL2 gives a Linux environment with GPU passthrough via the Windows NVIDIA driver.

The team's confirmed layout:

| Venv | Python | Stack | Owner models |
|---|---|---|---|
| `~/safresale-ml/.venv` (WSL) | 3.12 | torch 2.6.0+cu124, ultralytics, roboflow | **M1** defect detection |
| `~/ml312` (WSL) | 3.12 | TensorFlow 2.21 + Keras 3 | **M2** condition, **M3** AI-image |

Repo code stays on Windows (`C:\...\SafeResale`); data/weights/runs live inside WSL (`~/safresale-ml/`) to avoid slow
`/mnt/c` I/O. Scripts are invoked through WSL.

### 7.1 One-time setup

```bash
# 0) Windows: install Python 3.12 (once)
winget install Python.Python.3.12

# 1) Open WSL (Ubuntu 24.04). Check python3.12 exists and GPU passes through:
wsl
python3.12 --version
nvidia-smi

# 2) M1 venv (torch + YOLO). NOTE: TMPDIR must point at real disk — /tmp is a small RAM tmpfs (~4 GB).
mkdir -p ~/safresale-ml/tmp
python3.12 -m venv ~/safresale-ml/.venv
export TMPDIR=~/safresale-ml/tmp
~/safresale-ml/.venv/bin/pip install torch==2.6.0 torchvision==0.21.0 \
  --index-url https://download.pytorch.org/whl/cu124
~/safresale-ml/.venv/bin/pip install ultralytics opencv-python roboflow imagehash \
  'numpy<2' pandas matplotlib tqdm

# 3) TF venv (reuse existing ~/ml312 or create a copy)
python3.12 -m venv ~/ml312
~/ml312/bin/pip install tensorflow keras
# GPU libs: TF 2.21 needs exact CUDA libs (already added to ~/ml312):
~/ml312/bin/pip install nvidia-cudnn-cu12 nvidia-cublas-cu12
```

### 7.2 Verify GPU

```bash
~/safresale-ml/.venv/bin/python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"
# True NVIDIA GeForce RTX 4050 Laptop GPU

~/ml312/bin/python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
# [PhysicalDevice(name='/physical_device:GPU:0', device_type='GPU')]
```

### 7.3 TF GPU fix (persistent)

TF's loader doesn't always find CUDA libs from site-packages. The fix was appended to `~/.bashrc`:

```bash
if [ -d "$HOME/ml312/lib/python3.12/site-packages/nvidia" ]; then
  export LD_LIBRARY_PATH="$(echo $HOME/ml312/lib/python3.12/site-packages/nvidia/*/lib | tr ' ' ':'):$LD_LIBRARY_PATH"
fi
```

Reload: `source ~/.bashrc`, then re-run the TF GPU check.

### 7.4 Running M1 scripts from Windows PowerShell

```powershell
wsl -- bash -lc "export TMPDIR=~/safresale-ml/tmp; ~/safresale-ml/.venv/bin/python ml/m1_defect_detection/scripts/train.py --yaml configs/m1.yaml"
```

The backend's `VISION_PROVIDER=stub` keeps everything else running regardless of ML dependency availability.

## 8. Docker (optional — teammates need NO local ML setup)

Two Docker options exist:

- **§8 infra stack** (mongo, redis, backend, ai-worker, admin-web) — reference compose for machines with Docker.
- **§8.1–8.3 ML container** — the main one for the team: a ready-made torch+ultralytics+roboflow image, so
  teammates never install Python/torch/CUDA. Trained weights ship via **GitHub Releases**, so teammates
  don't download datasets either.

### 8.1 Build the ML image + GPU requirement

GPU needs NVIDIA container support:
- **Docker Desktop (Windows/WSL2):** Settings → Resources → WSL Integration → enable GPU acceleration.
- **Linux:** `sudo apt install nvidia-container-toolkit && sudo nvidia-ctk runtime configure --runtime=docker && sudo systemctl restart docker`

```powershell
docker compose -f ml/docker-compose.yml build
docker compose -f ml/docker-compose.yml run --rm ml
# prints: torch <ver> | cuda: True   (True = GPU inside container works)
```

### 8.2 Train / smoke / jupyter

```powershell
# 1-epoch smoke test on coco8 (verifies full pipeline inside container)
docker compose -f ml/docker-compose.yml run --rm smoke

# train M1 (baseline yolov8n + yolo11n), runs land in the `ml_data` volume
docker compose -f ml/docker-compose.yml run --rm train

# jupyter lab for experiments (http://localhost:8888)
docker compose -f ml/docker-compose.yml up lab
```

Datasets/weights/runs persist in the named volume `ml_data` across container runs.

### 8.3 Publish weights — so nobody downloads datasets

After training, publish the model (weights are ~5 MB; datasets are not):

```bash
# 1) copy best weights out of the volume
docker run --rm -v saferesale_ml_data:/ml/work -v "$PWD/dist:/out" saferesale/ml:latest \
  bash -c "cp /ml/work/runs/improved-yolo11n/weights/best.pt /out/m1-defect-yolo11n.pt"
gh release create m1-v0.1 dist/m1-defect-yolo11n.pt --repo SafeResale/SafeResale -t "M1 defect detection v0.1"
```

```bash
# 2) teammate runs inference with ONLY the weights + image — no dataset, no training
gh release download m1-v0.1 --repo SafeResale/SafeResale --pattern m1-defect-yolo11n.pt
docker compose -f ml/docker-compose.yml run --rm ml scripts/infer.py \
  --weights /ml/work/runs/improved-yolo11n/weights/best.pt \
  --source /ml/data/m1/sample.jpg
# (drop the image into ml/m1_defect_detection/data/m1/ — bind-mounted at /ml/data/m1)
```

## 9. Common issues

| Issue | Fix |
|---|---|
| `java` not on PATH | Use Android Studio JBR: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` |
| App can't reach backend | Use `10.0.2.2` for emulator or `adb reverse tcp:8000 tcp:8000` |
| Mongo connection refused | Start the local MongoDB service (Services → MongoDB) |
| 3.14 wheels missing for ML | Use the 3.12 WSL venvs in §7 |
| pip: `No space left on device` during ML install | `/tmp` is a RAM tmpfs; set `export TMPDIR=~/safresale-ml/tmp` first |
| TF reports CPU-only | Check `nvidia-cudnn-cu12`/`nvidia-cublas-cu12` installed + `.bashrc` LD_LIBRARY_PATH fix (§7.3) |
| `docker: cuda unavailable` | NVIDIA container toolkit not configured (§8.1); check Docker Desktop WSL GPU setting |
| AutoBackend: weights not found on `/mnt/c/...` paths | Ultralytics corrupts paths with apostrophes; run from WSL home or Docker (see §7.4 note) |
