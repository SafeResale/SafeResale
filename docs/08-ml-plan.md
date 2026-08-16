# 08 — ML Plan

Version 1.0 • August 2026

## 1. Goals

1. Ship a **working verification pipeline** now using a clearly-labeled stub provider.
2. Train real models (defect detection + condition classification) and **swap them in via config** with zero app changes.
3. Produce a research story for viva: baseline vs improved, ablation, latency-vs-accuracy trade-off.
4. **Only report measured numbers** (academic-integrity rule).

## 2. Components

| ID | Component | Approach | Status in MVP |
|---|---|---|---|
| M1 | Defect detection | Ultralytics YOLO11 (+ YOLOv8 baseline) | Pluggable — stub default |
| M2 | Condition classification | Keras 3 / TensorFlow, EfficientNetV2 or MobileNetV3 | Pluggable — stub default |
| M3 | AI-generated-image detection | authenticity provider (fine-tuned classifier) | Pluggable — stub default |
| M4 | Price anomaly | category/brand/model median + deviation ratio | **Real, from day 1** |
| M5 | Seller anomaly | scikit-learn Isolation Forest | **Real, from day 1** |
| M6 | Image quality | OpenCV (Laplacian variance, mean luminance, saturated-pixel ratio, perceptual hash) | **Real, from day 1** |
| M7 | Duplicate images | perceptual hash (imagehash) | **Real, from day 1** |
| M8 | Repeated descriptions | TF-IDF + cosine similarity (or sentence embeddings) | **Real, from day 1** |

### 2.1 Model ownership (GitHub team `SafeResale/models`)

| Models | Owners | Notes |
|---|---|---|
| M1, M2, M3 | rahulpandiyan + veereshkp | The three pluggable deep-learning models (defect detection, condition classification, AI-image detection) |
| M4, M5, M6 | kavya280229 + thanv (pending join) | Price anomaly, seller anomaly, image quality |
| M7, M8 | Anyone — if time permits | Duplicate images, repeated descriptions |

Coordination rules:
- Every model owner works on a **branch** of `SafeResale/SafeResale` (single repo, one source of truth).
- Every pluggable model keeps the **stub implementation green** until its real weights are swapped via
  `VISION_PROVIDER` / `AUTHENTICITY_PROVIDER` config — never a blocker for the app pipeline.
- Results land in `model_metrics`; only **measured** numbers are reported (academic-integrity rule).
- M2 (condition) labels derive from M1 (defect) annotations — M2 owner bootstraps with a public dataset meanwhile.

## 3. Dataset Plan

### 3.1 Defect detection
- Primary classes: scratch, crack, dent, screen damage, port damage, camera damage, body deformation.
- Sources (license-permitting): public damage/defect datasets (e.g., Roboflow Universe crack/scratch/dent sets),
  public car/mobile damage datasets. Document licensing + URL in the report.
- If licensing blocks public data: build a small internal dataset from phone/device photos; document annotation process.
- Annotations: YOLO format (bounding boxes per class). Splits: 70/15/15 train/val/test; **frozen test set never used in training**.

### 3.2 Condition classification
- Classes: Good, Moderate, Defective.
- Labels derived from annotated defect evidence + expert labels; balance classes.
- Augmentation: flip, rotate, brightness/contrast, blur, JPEG noise (no fabrication of labels).

### 3.3 Image quality set
- Manually labeled set: blurry / over-exposed / under-exposed / glare / good → measure reject/accept accuracy.

### 3.4 Anomaly
- Synthetic seller histories with injected anomalies (spam listing frequency, price outliers, duplicated images,
  near-identical descriptions) to verify Isolation Forest sensitivity + cold-start rules.

## 4. Detection Experiments

1. **Baseline:** YOLOv8n/m — train/fine-tune on defect dataset.
2. **Improved:** YOLO11n/m — same data, same splits, same seeds.
3. Report: precision, recall, mAP50, mAP50-95 per class + overall on the frozen test set.
4. Latency: CPU and GPU (if available) mean/p50/p95 per image.

## 5. Classification Experiments

1. Lightweight backbone (MobileNetV3).
2. Stronger backbone (EfficientNetV2-S).
3. Same splits/augmentation; report accuracy, macro precision/recall/F1, confusion matrix, latency.

## 6. Ablations (for marks)

- Raw images vs preprocessed (OpenCV standardized) inputs.
- Single-view classification vs eight-view aggregation with quality weights.
- With/without defect-severity weighting in physical risk.

## 7. Serving & Versioning

- `VisionProvider` interface (see `03-architecture.md` §5): `stub` (default) | `real`.
- `ai-worker` service (optional, torch + ultralytics + keras) exposes an HTTP endpoint the backend provider calls;
  or the provider imports models in-process when the heavy deps are installed.
- Model version persisted on every detection/condition doc and on `model_metrics`.
- `model_metrics` collection = single source of truth for the admin Models page.

## 8. Environment Notes

- System Python is 3.14; **torch/ultralytics/tensorflow wheels may not exist for 3.14 yet.**
- Fallback: create a **Python 3.12 venv** for ML work (setup script provided). The serving provider only loads when
  those deps are present; otherwise the stub keeps the system green.
- TensorFlow is optional on the serving path — a lightweight PyTorch classifier can substitute if TF wheels are
  unavailable (document the decision).

## 9. Acceptance criteria for "real provider is done"

- `VISION_PROVIDER=real` + `ML_WEIGHTS_DIR` set → `run-vision` returns detections + condition with `simulated: false`.
- `model_metrics` contains the frozen-test results for each model.
- Admin Models page displays those metrics.
- Swap back to `stub` restores deterministic output (no code change).
