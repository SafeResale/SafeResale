# M1 — Defect detection (YOLO11)

**Owner:** rahulpandiyan + veereshkp · GitHub issue #1 · docs/08-ml-plan.md §2/M1, §3.1, §4

Detect localized defects in product photos. M1 v1 targets the **core 14 classes** (docs/08-ml-plan.md §3.1):
scratch, crack, dent, screen_damage, glass_damage, camera_damage, port_damage, casing_damage,
body_deformation, paint_damage, chip, rust, corrosion, water_damage — across 8 categories (phones,
laptops, consumer electronics, cars, bikes, home appliances, gaming devices, cameras).
Outputs feed the physical-risk component of the risk engine via the `VisionProvider` (`VISION_PROVIDER=real`).

## Env

Runs in WSL venv `~/safresale-ml/.venv` (torch 2.6.0+cu124). See `docs/10-setup-guide.md` §7.

```bash
# verify GPU
~/safresale-ml/.venv/bin/python -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"
```

## Dataset (docs/08-ml-plan.md §3.1)

1. Pick license-permitted public defect sets (Roboflow Universe, Kaggle, MVTec AD/2, Hugging Face,
   Crack-Seg — see docs/08-ml-plan.md §3.1). **Only train classes with ≥100 annotated instances.**
   Document licensing + URL for every dataset.
2. Download to YOLO format:
   ```bash
   export ROBOFLOW_API_KEY=your_key
   ~/safresale-ml/.venv/bin/python ml/m1_defect_detection/scripts/download_dataset.py \
     --workspace <ws> --project <proj> --version <n>
   ```
3. Check `data/datasets/defects/` has `train/{images,labels}` + `val/...` (test optional).
4. Align class names in `configs/m1.yaml` with the dataset's `data.yaml`.

Splits 70/15/15. **The test split is frozen — never used in training.**
If licensing blocks public data: build a small internal device-photo set and document annotation.

## Train (baseline vs improved, same seed/data/imgsz)

```bash
~/safresale-ml/.venv/bin/python ml/m1_defect_detection/scripts/train.py \
  --yaml ml/m1_defect_detection/configs/m1.yaml --epochs 50 --imgsz 640
```

Runs `yolov8n` (baseline) then `yolo11n` (improved) into `ml/m1_defect_detection/runs/` and prints mAP50/mAP50-95.

## Report (only measured numbers)

- Precision, recall, mAP50, mAP50-95 per class + overall on the frozen test set.
- Latency: CPU + GPU mean/p50/p95 per image (`yolo val ... speed` or a timing loop).
- Fill `model_metrics` from `runs/*/results.csv`; write-up for the admin Models page / viva.

## Definition of done

- `VISION_PROVIDER=real` + `ML_WEIGHTS_DIR` set → `run-vision` returns detections with `simulated:false`.
- `model_metrics` holds frozen-test results.
- Swap back to `stub` restores deterministic output (no code change).
