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

**Product scope (8 categories):** mobile/smartphone, laptop/computer, consumer electronics, cars,
bikes/motorcycles, home appliances, gaming devices, cameras.

**Shared defect vocabulary (29 classes).**
- **Core — M1 target (14 classes):** box-localizable, cross-category, best public-data support:
  scratch, crack, dent, screen_damage, glass_damage, camera_damage, port_damage, casing_damage,
  body_deformation, paint_damage, chip, rust, corrosion, water_damage.
- **Extensions — category-specific or data-gated (15 classes):** stain, discoloration, wear,
  broken_part, missing_part, button_damage, keyboard_damage, hinge_damage, cable_damage,
  connector_damage, tire_damage, wheel_damage, mirror_damage, light_damage, bumper_damage.

Rules:
- Train only classes with enough annotated instances (**target ≥100 boxes/class**; drop or defer otherwise).
  Never claim support for the full vocabulary without data backing each class.
- stain / discoloration / wear are area-level → prefer M2 condition classification over box detection.
- Sources (license-permitting): Roboflow Universe (defect/damage sets), Kaggle (camera damage/defects),
  MVTec AD + MVTec AD 2 (industrial anomalies), Hugging Face (synthetic-mvtec-ad,
  industrial-defect-dataset, Inspect-Anything, defect/crack-detection sets), Crack-Seg (segment/crack),
  Papers With Code, Google Dataset Search. Document URL + license per dataset in the report.
- If licensing blocks public data: build a small internal device-photo set; document annotation process.
- Annotations: YOLO format (bounding boxes per class). Splits: 70/15/15 train/val/test; **frozen test set never used in training**.

#### 3.1.1 Dataset shortlist (evaluated 2026-08)

No single public set covers all 14 core classes → **merge 3–5 sets**, reconcile class names,
then drop classes with <100 boxes. License must permit academic/FYP use; document each URL + license in the report.

| # | Dataset | Source | Format | Classes → core mapping | License/notes |
|---|---|---|---|---|---|
| 1 | **CarDD** (Car Damage Detection, 4000 img / 8740 boxes) | Kaggle `gabrielfcarvalho/cardd-with-yolo-annotations-images-labels` | YOLO, train/val/test split | dent, scratch, crack, glass_shatter→glass_damage, lamp_broken→(ext), tire_flat→(ext) | Academic (PIC Lab USTC). Strongest detection set for cars |
| 2 | **Cracked Mobile Screen** (~7000 img) | Kaggle `dataclusterlabs/cracked-screen-dataset` | COCO/VOC/YOLO | screen_damage, crack | DataCluster Labs; phones |
| 3 | **Car Damages Kaggle** | Roboflow `ai-proyect/car-damages-kaggle` | YOLO (8 classes) | scratch, dent, corrosion, paint_damage, cracked, flaking, broken_part→(ext), missing_part→(ext) | mirrors CarDD-style classes |
| 4 | **Rust Detection** (10072 img) | Roboflow `rust-detection/rust-detection-38s6e` | YOLO/seg | rust | Roboflow Universe |
| 5 | **Corrosion YOLOv8** | Roboflow `corrosion-yolo-v8/corrosion-yolov8` | YOLO | corrosion | Roboflow Universe |
| 6 | **Mobile Damage Diagnosis** | Roboflow `abhinavpoc/mobile-damage-diagnosis` | YOLO | scratch, screen_crack, dead_pixel→(skip) | phones |
| 7 | **MVTec AD / AD 2** (5354 img, 15 categories) | mvtec.com (form) or HF `Voxel51/mvtec-ad` | masks → convert | scratch/crack/dent type defects, industrial textures | CC BY-NC-SA 4.0; **anomaly-detection paradigm**, needs mask→box conversion; use as auxiliary/pre-fine-tune only |
| 8 | **Industrial Defect / Inspect-Anything** (HF) | HF `himanshu1257/industrial-defect-dataset`, `geonuk-kimmm/Inspect-Anything` | varies | generic industrial defects | verify license + format before use |
| 9 | **LCFC-Laptop** (14,478 defects) | MDPI Sensors 2025 supplementary (paper PMC12349538) | boxes + masks | scratch, dirt→stain(ext), plain_particle→(skip), collision→body_deformation/dent | **laptop surfaces**, real production photos; verify download URL + license before use |
| 10 | **MSD — Mobile Screen Defect** (1200 img) | GitHub `jianzhang96/MSD` | PASCAL VOC | scratch, oil/stain→stain(ext) | phone screen defects (industrial camera) |
| 11 | **Laptop Screen Damage** | Roboflow `aanish-usman/laptop-screen-damage-detection` | YOLO | crack, fade→(skip) | laptop screens |
| 12 | **Gaming Console Damage** (192 img) | Roboflow `joy-zhuge-oqnos/console-saloo` | YOLO/seg | scratch, dirty→stain(ext), collision→dent/body_deformation, gap→(skip) | gaming consoles; small |
| 13 | **Smartphone surface defect** (1857 img / 6651 boxes, 10 cls) | Tencent cloud dev article 2542114 | VOC + YOLO | chip, crack, dent, glass_broken, missing_part, peel, pitting, scratch, water_damage, wear_and_tear | phone surfaces; strong chip/scratch/dent counts but community-hosted → verify license + access |

**Category coverage (verified 2026-08):**
- **Mobile** ✅ — Cracked Mobile Screen (~7000), MSD (1200), Smartphone surface defect (1857), Mobile Damage Diagnosis
- **Laptop** ✅ — LCFC-Laptop (14,478 defects), Laptop Screen Damage
- **Cars** ✅ — CarDD (4000), Car Damages Kaggle, Rust (10072), Corrosion
- **Gaming devices** ⚠️ — Gaming Console Damage (192 img, small but box-annotated)
- **Consumer electronics** ⚠️ — covered indirectly via phone/laptop surface defects + MVTec industrial (bottle, cable, etc.)
- **Cameras** ❌ — industrial lens-defect papers don't release data; contamination sets (CLP, SIDL, flare-removal) are image-restoration, not detection → phone `camera_damage`/`glass_damage` + MVTec proxy, or defer
- **Bikes/motorcycles** ❌ — no public box-annotated set → transfer from cars (same surface damages: dent/scratch/paint/rust) + internal/synthetic set
- **Home appliances** ❌ — no public box-annotated set → MVTec industrial proxy + internal/synthetic set

**Coverage vs core 14:** scratch ✅ crack ✅ dent ✅ screen_damage ✅ glass_damage ✅ paint_damage ✅ rust ✅ corrosion ✅ chip ✅ (camera_damage, port_damage, casing_damage, body_deformation, water_damage — **weak or no large public set**; cover via category transfers above or drop/defer these from M1 v1).

Per the 8 product categories (M1 core): every category is represented in the merged set via real box-annotated data **except cameras, bikes, home appliances**, where we either (a) transfer from cars/phones, (b) build a small internal device-photo set (plan §3.1), or (c) cover those categories through M2 condition classification instead. Never claim per-category support without measured data.

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
