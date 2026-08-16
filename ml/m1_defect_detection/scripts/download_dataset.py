"""Download a defect-detection dataset from Roboflow Universe (YOLOv8/YOLO format).

Requires a free Roboflow API key: https://app.roboflow.com/settings/api
  export ROBOFLOW_API_KEY=...

Usage:
  python scripts/download_dataset.py --workspace <ws> --project <proj> --version <n> [--name defects]

Downloads into ~/safresale-ml/m1/data/datasets/<name> (WSL home, not /mnt/c) so training
reads fast and paths stay clean. Override root with M1_WORK_ROOT.

Public defect datasets to consider (from docs/08-ml-plan.md §3.1):
  - Roboflow Universe: "surface-crack-detection", "crack-detection", "scratch-detection",
    phone-screen-damage, car-damage sets
  - Kaggle / Hugging Face / MVTec AD(2) / Crack-Seg — see docs/08-ml-plan.md §3.1
  - Only train classes with >=100 annotated instances; document licensing + URL per dataset.
"""
import argparse
import os
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent.parent
WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workspace", required=True, help="Roboflow workspace name")
    parser.add_argument("--project", required=True, help="Roboflow project name")
    parser.add_argument("--version", type=int, required=True, help="Dataset version number")
    parser.add_argument("--name", default="defects", help="Output folder name under data/datasets/")
    args = parser.parse_args()

    api_key = os.environ.get("ROBOFLOW_API_KEY")
    if not api_key:
        raise SystemExit("ROBOFLOW_API_KEY not set (export ROBOFLOW_API_KEY=...)")

    from roboflow import Roboflow

    rf = Roboflow(api_key=api_key)
    project = rf.workspace(args.workspace).project(args.project)
    version = project.version(args.version)
    dest = WORK_ROOT / "data" / "datasets" / args.name
    dest.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {args.workspace}/{args.project}/v{args.version} -> {dest}")
    version.download("yolov8", location=str(dest))

    data_yaml = dest / "data.yaml"
    if data_yaml.exists():
        with open(data_yaml) as f:
            rf_cfg = yaml.safe_load(f)
        print("Roboflow dataset classes:", rf_cfg.get("names"))
        print("Dataset structure: verify train/val/test images+labels folders.")
        print("Next: align class names in configs/m1.yaml with the dataset, then train.")


if __name__ == "__main__":
    main()
