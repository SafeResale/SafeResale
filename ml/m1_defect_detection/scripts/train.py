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
    parser.add_argument("--workers", type=int, default=4,
                        help="dataloader workers (WSL with limited RAM: 4 works, 8 OOMs)")
    parser.add_argument("--aug", action="store_true",
                        help="realistic marketplace-photo augmentation (brightness/contrast/lighting/perspective/mild blur/noise)")
    parser.add_argument("--freeze", type=int, default=0,
                        help="freeze first N layers (e.g. 10 = backbone frozen, head only trains — much faster)")
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
    # Ultralytics >=8.4 expects a data yaml path (not a dict) in model.train(data=...).
    data_file = runs_dir / "data.yaml"
    with open(data_file, "w") as f:
        yaml.safe_dump(cfg, f, sort_keys=False)
    print(f"work root: {WORK_ROOT}")
    print(f"dataset:   {cfg['path']}")
    print(f"torch: {torch.__version__} | cuda: {torch.cuda.is_available()} | device: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'cpu'}")
    print("classes:", cfg.get("names"))

    # Baseline: YOLOv8n. Improved: YOLO11n. Same seed, data, image size.
    # Augmentation defaults tuned for marketplace photos (keep realistic, no
    # distortions that break object geometry): moderate lighting/color jitter,
    # small rotation/scale, slight blur/noise via hsv + scale + erasing only.
    base_aug = dict(
        hsv_h=0.015,   # tiny hue shift
        hsv_s=0.5,     # saturation jitter (indoor/outdoor lighting)
        hsv_v=0.4,     # brightness/exposure variation
        degrees=3.0,   # small rotations
        translate=0.1,
        scale=0.3,
        fliplr=0.5,
        erasing=0.2,   # mild object hiding (light occlusion)
        close_mosaic=10,
    )
    aug = dict(base_aug, **{
        "hsv_s": 0.7,
        "hsv_v": 0.6,
        "degrees": 5.0,
        "translate": 0.2,
        "scale": 0.4,
        "perspective": 0.0001,   # near-zero perspective, keeps geometry
        "erasing": 0.3,
    }) if args.aug else base_aug

    for model_name, tag in [("yolov8n.pt", "baseline-v8n"), ("yolo11n.pt", "improved-yolo11n")]:
        model = YOLO(model_name)
        print(f"\n=== Training {model_name} ({tag}) ===")
        model.train(
            data=data_file,
            epochs=args.epochs,
            imgsz=args.imgsz,
            device=args.device,
            workers=args.workers,
            project=str(runs_dir),
            name=tag,
            seed=args.seed,
            val=True,
            plots=True,
            freeze=args.freeze,
            **aug,
        )
        metrics = model.val(data=data_file, project=str(runs_dir), name=f"{tag}-val", workers=args.workers)
        print(f"[{tag}] mAP50={metrics.box.map50:.4f} mAP50-95={metrics.box.map:.4f}")


if __name__ == "__main__":
    main()
