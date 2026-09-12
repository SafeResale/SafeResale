# M6 — OpenCV image quality

**Owner:** kavya280229 + thanv · `docs/08-ml-plan.md` §3.3 / §5

Rejects bad product photos upload-side. Per-image checks: blur (Laplacian variance), exposure
(luminance ± under/over-exposed), glare (bright, low-saturation pixels in HSV), and duplicate /
near-duplicate detection via perceptual hashing (pHash Hamming distance). A 0–100 quality score is
derived per image (blur −40, under/over-exposure −30, glare −20), and the aggregate 8-angle result
passes only when all 8 images pass. This feeds the on-device/backend upload gate (see
`03-architecture.md`).

## Structure

```
m6_image_quality/
├── configs/m6.yaml        quality thresholds (see below)
├── notebooks/             experimentation notebooks
├── scripts/
│   ├── common.py          all M6 logic (checks, 8-angle analysis, accept/reject metrics)
│   ├── infer.py           CLI: analyze one image or exactly 8 images
│   ├── evaluate.py        CLI: accept/reject accuracy, or labeled-folder -> metrics CSV
│   ├── smoke_test.py      end-to-end on the tracked manual_test/ photos (no extra data)
│   └── generate_evaluation_data.py  synthesize sharp/blurry/under/over/glare eval sets
├── manual_test/           8-angle photo set (tracked in git, smoke-test input)
└── tests/                 pytest (test_quality.py) + manual check scripts (*_manual.py)
```

All commands run **from the repo root** (imports are absolute: `ml.m6_image_quality.scripts.common`).

## Env

Runs on **native Windows** in the M4–M6 venv (`.venv-m4m5m6`, git-ignored). Deps are the ones in
`ml/requirements.txt`: `opencv-python`, `imagehash` (brings Pillow), `numpy`, `pandas`; scripts also
use `yaml` (PyYAML, already a transitive dep via the M1 stack).

```powershell
py -3.11 -m venv .venv-m4m5m6
.\.venv-m4m5m6\Scripts\python -m pip install opencv-python imagehash "numpy<2" pandas pyyaml pytest
```

## Run

Scripts import the shared `ml.` package and use repo-root-relative data paths, so invoke them as
modules **from the repo root** (`python -m` puts the repo root on `sys.path`).

```powershell
# single image
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.infer --image some_photo.jpg

# 8-angle analysis (exactly 8 paths)
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.infer --images a.jpg b.jpg c.jpg d.jpg e.jpg f.jpg g.jpg h.jpg

# smoke test — uses only the tracked manual_test/ photos
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.smoke_test

# accept/reject accuracy from a labels CSV (image_path, actual_label)
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.evaluate --labels labels.csv

# metrics CSV from a folder of labeled subfolders (sharp/…, blurry/…, …)
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.evaluate --data-dir ml/m6_image_quality/data/evaluation/bikes_motorcycles

# synthesize an evaluation dataset from a source photo set
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.scripts.generate_evaluation_data
```

## Tests

```powershell
# pytest suite — REQUIRES the gitignored fixture data under data/fixtures and data/evaluation
.\.venv-m4m5m6\Scripts\python -m pytest ml/m6_image_quality/tests/test_quality.py

# manual check scripts (print PASS/FAIL, run as modules) — also use data/fixtures
.\.venv-m4m5m6\Scripts\python -m ml.m6_image_quality.tests.test_blur_manual
```

> `data/` (fixtures, evaluation sets, source datasets) is git-ignored — it is NOT in the checkout.
> Full `test_quality.py` passes in an env that has those images. The fixture-free smoke test
> (`scripts/smoke_test.py`) runs anywhere.

## Config (`configs/m6.yaml`)

Thresholds are loaded by `scripts/common.py`; the same historical defaults are baked in as fallback.
Values are asserted by exact-score tests — change them only if you rerun the suite.

| Key | Default |
|---|---|
| `quality.blur_threshold` | 250.0 |
| `quality.underexposed_luminance` | 85 |
| `quality.overexposed_luminance` | 160 |
| `quality.glare_ratio_threshold` | 0.059 |
| `quality.duplicate_hash_distance_threshold` | 8 |

## Report

- Accuracy = accept/reject agreement vs the manually-labeled eval set (`evaluate.py --labels`).
- Only **measured** numbers go into `model_metrics` (academic-integrity rule, `ml/README.md`).

## Definition of done

- Real photos: blur/over/under-exposed/glare images are flagged; the 8-angle result rejects when
  any angle fails or images are duplicate/near-duplicate (`simulated: false`).
- Override env `M6_CONFIG_PATH` (default `configs/m6.yaml`) restores deterministic output.