# ML workspace

Each model lives in its own folder (see `docs/08-ml-plan.md` §2.1 for ownership):

| Folder | Model | Owner |
|---|---|---|
| `m1_defect_detection` | YOLO11 defect detection | rahulpandiyan + veereshkp |
| `m2_condition_classification` | Keras/TF condition classification | rahulpandiyan + veereshkp |
| `m3_ai_image_detection` | AI-generated image detection | rahulpandiyan + veereshkp |
| `m4_price_anomaly` | Price anomaly | kavya280229 + thanv |
| `m5_seller_anomaly` | Isolation Forest seller anomaly | kavya280229 + thanv |
| `m6_image_quality` | OpenCV image quality | kavya280229 + thanv |
| `m7_duplicate_images` | perceptual hash (if time permits) | anyone |
| `m8_repeated_descriptions` | TF-IDF (if time permits) | anyone |

## Environment

ML work runs in **WSL** (Ubuntu) with **Python 3.12 venvs** — GPU TF is impossible on native Windows, and
torch/ultralytics/tensorflow need 3.12 (system Python is 3.14). Full commands in `docs/10-setup-guide.md` §7.

```bash
# M1 venv (torch + YOLO) — already set up on this machine:
#   ~/safresale-ml/.venv   (torch 2.6.0+cu124, ultralytics, roboflow)
# TF venv for M2/M3 — already set up:
#   ~/ml312                 (tensorflow 2.21, keras 3, GPU enabled)

# Verify GPU:
~/safresale-ml/.venv/bin/python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"
~/ml312/bin/python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
```

New machine setup (from PowerShell):

```powershell
winget install Python.Python.3.12
wsl
```

Then in WSL:

```bash
mkdir -p ~/safresale-ml/tmp && python3.12 -m venv ~/safresale-ml/.venv
export TMPDIR=~/safresale-ml/tmp   # /tmp is a small RAM tmpfs; pip needs real disk
~/safresale-ml/.venv/bin/pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cu124
~/safresale-ml/.venv/bin/pip install -r ml/requirements.txt
```

TF venv + GPU lib fix: see `docs/10-setup-guide.md` §7.1–§7.3.

## Rules

- Data, weights and training runs are git-ignored (see root `.gitignore`).
- Only **measured** numbers go into `model_metrics` (academic-integrity rule).
- Each model stays swappable behind its provider interface — never a blocker.
