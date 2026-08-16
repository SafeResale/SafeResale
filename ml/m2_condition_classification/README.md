# M2 — Condition classification (Keras 3 / TensorFlow)

**Owner:** rahulpandiyan + veereshkp · GitHub issue · `docs/08-ml-plan.md` §2/M2, §3.2, §5

Estimates the **overall physical condition** of a product photo as `Good | Moderate | Defective`
(docs/01-prd.md §10.3). Transfer learning with two backbones (MobileNetV3 lightweight baseline,
EfficientNetV2-S stronger improved) on the **same splits/augmentation/seed**, so the comparison is fair.
Outputs feed the physical-risk component of the risk engine via the `VisionProvider` (`VISION_PROVIDER=real`,
`KerasProvider` in `docs/03-architecture.md` §5).

## Env

Runs in WSL venv `~/ml312` (tensorflow 2.21 + keras 3, GPU). See `docs/10-setup-guide.md` §7.
**Also runs fully containerized** via `ml/Dockerfile.m2` (`tensorflow/tensorflow:2.21.0-gpu`).

```bash
# verify GPU (host venv)
~/ml312/bin/python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"

# verify GPU (docker)
docker compose -f ml/docker-compose.yml run --rm m2
# prints: tensorflow 2.21.x | gpu: True | [...]
```

> Docker GPU note: the `m2*` compose services reserve an nvidia GPU. If the host's
> WSL/Docker GPU passthrough isn't configured (no adapter found), run the containerized
> pipeline on CPU instead:
> `docker compose -f ml/docker-compose.yml run --rm m2-smoke`

## No GPU? Use `notebooks/M2_train_condition_classifier.ipynb`

`notebooks/M2_train_condition_classifier.ipynb` is a **Colab + Kaggle** notebook (free T4 GPU)
that clones the repo and drives these same scripts end-to-end: download (Roboflow / Kaggle /
zip upload) → class-map → 70/15/15 split → train both models → evaluate → download results.
Open it on [Colab](https://colab.research.google.com/) with **Runtime → Change runtime type →
T4 GPU**, set `REPO_URL` in cell 1, and run cells in order.

Local CPU is validated too: the full `prepare → train → evaluate → infer` chain was run in the
Docker container on a synthetic 72-image set (splits.json 48/9/15, two-stage training, frozen-test
metrics + latency + confusion matrix, run-vision contract output). Expected: correct pipeline,
garbage accuracy on random-noise data — real numbers come from the notebook on real data.

## Dataset (docs/08-ml-plan.md §3.2)

Labels derive from M1 (defect) annotations + expert labels; meanwhile bootstrap with a
**license-permitted public damage/condition set** — document URL + license per dataset in the report.

No single free dataset has all three grades, so we **combine two sources** into
`good / moderate / defective` (see `configs/class_map.yaml`):

| Class | Source | License |
|-------|--------|---------|
| good + defective | [`damaged-vs-good-packages`](https://universe.roboflow.com/aadhavs-first-workspace/damaged-vs-good-packages) (Roboflow, **Classification**, v1, 581 images, classes `damaged`/`intact`) | CC BY 4.0 |
| moderate | [`Cracked Mobile Screen Dataset`](https://www.kaggle.com/datasets/dataclusterlabs/cracked-screen-dataset) (cracked screens) | CC0 (Public Domain) |

```bash
export ROBOFLOW_API_KEY=your_key
~/ml312/bin/python ml/m2_condition_classification/scripts/download_dataset.py \
  --source roboflow --workspace aadhavs-first-workspace --project damaged-vs-good-packages \
  --version 1 --name condition_packages

export KAGGLE_USERNAME=... KAGGLE_KEY=...        # or ~/.kaggle/kaggle.json
~/ml312/bin/python ml/m2_condition_classification/scripts/download_dataset.py \
  --source kaggle --kaggle-dataset dataclusterlabs/cracked-screen-dataset \
  --name condition_cracked --as-class cracked-screen   # flat set -> single class folder
```

> License caveat: many phone/electronics sets on Kaggle are **CC BY-NC** (non-commercial,
> e.g. other cracked-phone sets) or paid — not suitable for a marketplace. Verify each
> license before use. The cracked-screen set above is **CC0 (Public Domain)**.

Then merge the two sources into one class-folder dataset and build a stratified
70/15/15 split (`configs/class_map.yaml` maps `intact=good`, `cracked-screen=moderate`,
`damaged=defective`; the `:1000` cap on moderate keeps classes balanced):

```bash
~/ml312/bin/python ml/m2_condition_classification/scripts/combine_datasets.py \
  --out condition_combined \
  --src condition_packages/intact \
  --src condition_packages/damaged \
  --src condition_cracked/cracked-screen:1000 \
  --overwrite

~/ml312/bin/python ml/m2_condition_classification/scripts/prepare_dataset.py \
  --dataset condition_combined --out condition_combined \
  --class-map ml/m2_condition_classification/configs/class_map.yaml
```

Splits are 70/15/15; **the test split is frozen — never used in training**. Imbalanced classes are
handled with inverse-frequency class weights (no fabricated labels).

## Smoke test (verify GPU pipeline, no data needed)

```bash
# host venv
~/ml312/bin/python ml/m2_condition_classification/scripts/smoke_test.py
# prints: SMOKE OK | val_loss=... val_acc=...   (synthetic data — not a real metric)

# docker (same script, image-safe)
docker compose -f ml/docker-compose.yml run --rm m2-smoke
```

## Train (baseline vs improved, same seed/data/augmentation)

Edit `configs/m2.yaml` → set `dataset:` to your `--out` name, then:

```bash
# host venv
~/ml312/bin/python ml/m2_condition_classification/scripts/train.py \
  --yaml ml/m2_condition_classification/configs/m2.yaml [--epochs 50]

# docker
docker compose -f ml/docker-compose.yml run --rm m2-train
```

Runs `mobilenetv3small` (baseline) then `efficientnetv2s` (improved) into
`~/safresale-ml/m2/runs/<tag>/` and prints val accuracy + macro P/R/F1.
Two-stage schedule: head on frozen base → full fine-tune at 1/10 LR.
Docker runs persist to the `ml_data_m2` volume (`/ml/work` = `M2_WORK_ROOT`); bind-mount
`../ml/m2_condition_classification/data:/ml/data/m2` is available for manual data drops.

## Evaluate (frozen test split + latency)

```bash
~/ml312/bin/python ml/m2_condition_classification/scripts/evaluate.py \
  --model ~/safresale-ml/m2/runs/improved-effv2s/model.keras [--cpu]

# docker (weights from ml_data_m2 volume at /ml/work/runs/<tag>/model.keras)
docker compose -f ml/docker-compose.yml run --rm m2 \
  scripts/evaluate.py --model /ml/work/runs/improved-effv2s/model.keras
```

Writes `test_metrics.json` (accuracy, macro P/R/F1, per-class, confusion matrix), a confusion-matrix
PNG + CSV, and per-image latency mean/p50/p95. **Only measured numbers go in** (academic-integrity rule).
Fill `model_metrics` from these files for the admin Models page.

## Infer (no dataset required — publish weights via GitHub Releases)

```bash
~/ml312/bin/python ml/m2_condition_classification/scripts/infer.py \
  --model ~/safresale-ml/m2/runs/improved-effv2s/model.keras --source <img_or_folder>
```

Optional 8-view quality-weighted aggregation (docs/01-prd.md §10.3):

```bash
~/ml312/bin/python ml/m2_condition_classification/scripts/infer.py --model <model.keras> \
  --source <8view_folder> \
  --quality-weights '{"front":0.9,"back":0.8,"left":0.7,"right":0.7,"top":0.6,"bottom":0.6,"front45":0.5,"back45":0.5}'
```

Docker: bind-mount your images and call `m2` with the script path, e.g.
`docker compose -f ml/docker-compose.yml run --rm m2 scripts/infer.py --model /ml/work/runs/improved-effv2s/model.keras --source /ml/data/m2/<folder>`.

Output matches the `run-vision` condition block: `{"simulated": false, "condition": {"class", "probabilities"},
"model_version"}` (docs/04-api-contract.md).

## Research story (docs/08-ml-plan.md §5–6)

- Lightweight (MobileNetV3) vs stronger (EfficientNetV2-S) — accuracy vs latency trade-off.
- Ablations: raw vs OpenCV-preprocessed inputs; single-view vs eight-view quality-weighted aggregation.

## Definition of done

- `VISION_PROVIDER=real` + `ML_WEIGHTS_DIR` set → `run-vision` returns condition with `simulated:false`.
- `model_metrics` holds frozen-test results (accuracy, macro P/R/F1, confusion matrix, latency).
- Admin Models page displays those metrics.
- Swap back to `stub` restores deterministic output (no code change).
