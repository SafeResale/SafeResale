"""Train M1 defect detection: YOLOv8n baseline -> YOLO11n improved, same data/splits/seeds.

Usage (in WSL):
  ~/safresale-ml/.venv/bin/python scripts/train.py --yaml configs/m1.yaml [--epochs 50] [--imgsz 640]

Why work root ~/safresale-ml/m1? Two reasons:
  1. WSL /mnt/c is slow (9p) — the repo path shows "Slow image access".
  2. AutoBackend corrupts paths containing an apostrophe (e.g. "Rahul's Projects").
Data and runs therefore live inside WSL home; the repo keeps only scripts + configs.

Paths:
  WORK_ROOT          ~/safresale-ml/m1        (override with M1_WORK_ROOT)
  dataset            $WORK_ROOT/data/datasets/<name>
  runs               $WORK_ROOT/runs
"""
import argparse
import os
from pathlib import Path

import torch
import yaml
from ultralytics import YOLO

HERE = Path(__file__).resolve().parent.parent
WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--yaml", default="configs/m1.yaml", help="Data yaml (relative to m1_defect_detection/)")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", default="0", help="0 = CUDA GPU, cpu = CPU")
    args = parser.parse_args()

    data_yaml = HERE / args.yaml
    if not data_yaml.exists():
        raise SystemExit(f"data yaml not found: {data_yaml}")

    with open(data_yaml) as f:
        cfg = yaml.safe_load(f)

    # Rewrite the dataset path to the WSL work root so /mnt/c is never used.
    dataset_root = WORK_ROOT / "data" / "datasets" / os.path.basename(str(cfg.get("path", "defects")))
    cfg["path"] = str(dataset_root)
    runs_dir = WORK_ROOT / "runs"
    runs_dir.mkdir(parents=True, exist_ok=True)
    print(f"work root: {WORK_ROOT}")
    print(f"dataset:   {cfg['path']}")
    print(f"torch: {torch.__version__} | cuda: {torch.cuda.is_available()} | device: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'cpu'}")
    print("classes:", cfg.get("names"))

    # Baseline: YOLOv8n. Improved: YOLO11n. Same seed, data, image size.
    for model_name, tag in [("yolov8n.pt", "baseline-v8n"), ("yolo11n.pt", "improved-yolo11n")]:
        model = YOLO(model_name)
        print(f"\n=== Training {model_name} ({tag}) ===")
        model.train(
            data=cfg,
            epochs=args.epochs,
            imgsz=args.imgsz,
            device=args.device,
            project=str(runs_dir),
            name=tag,
            seed=args.seed,
            val=True,
            plots=True,
        )
        metrics = model.val(data=cfg, project=str(runs_dir), name=f"{tag}-val")
        print(f"[{tag}] mAP50={metrics.box.map50:.4f} mAP50-95={metrics.box.map:.4f}")


if __name__ == "__main__":
    main()
