"""Download a defect-detection dataset from Roboflow Universe or Kaggle.

Two source modes:

1) Roboflow (YOLOv8/YOLO format)
   Requires a free Roboflow API key: https://app.roboflow.com/settings/api
     export ROBOFLOW_API_KEY=...
   Usage:
     python scripts/download_dataset.py --source roboflow --workspace <ws> \
       --project <proj> --version <n> [--name defects]

2) Kaggle
   Requires Kaggle API credentials: https://www.kaggle.com/settings -> Create API token
   Place the token JSON at ~/.kaggle/kaggle.json (or set KAGGLE_USERNAME/KAGGLE_KEY).
   Usage:
     python scripts/download_dataset.py --source kaggle --kaggle-dataset <owner/name> \
       [--name cardd] [--target-dir ""]

Downloads into ~/safresale-ml/m1/data/datasets/<name> (WSL home, not /mnt/c) so training
reads fast and paths stay clean. Override root with M1_WORK_ROOT.

Shortlist (docs/08-ml-plan.md §3.1.1):
  - CarDD (cars): gabrielfcarvalho/cardd-with-yolo-annotations-images-labels
  - Cracked Mobile Screen: dataclusterlabs/cracked-screen-dataset
  - Roboflow: ai-proyect/car-damages-kaggle, rust-detection/rust-detection-38s6e,
    corrosion-yolo-v8/corrosion-yolov8, abhinavpoc/mobile-damage-diagnosis
Only train classes with >=100 annotated instances; document licensing + URL per dataset.
"""
import argparse
import os
import shutil
import zipfile
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent.parent
WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))
DATASETS_ROOT = WORK_ROOT / "data" / "datasets"


def _print_dataset_layout(dest: Path) -> None:
    """Show classes + top-level structure so we know how to wire up configs/m1.yaml."""
    data_yaml = dest / "data.yaml"
    if data_yaml.exists():
        with open(data_yaml) as f:
            cfg = yaml.safe_load(f)
        print("dataset classes:", cfg.get("names"))
    print("top-level entries:", sorted(p.name for p in dest.iterdir() if p.is_dir()))
    for split in ("train", "val", "test"):
        img = dest / split / "images"
        lab = dest / split / "labels"
        if img.exists():
            n_imgs = len(list(img.iterdir()))
            n_labs = len(list(lab.iterdir())) if lab.exists() else 0
            print(f"  {split}: {n_imgs} images, {n_labs} labels")
    print("Next: merge/align class names in configs/m1.yaml with the dataset, then train.")


def _flatten_nested(src: Path) -> None:
    """If Kaggle extracts one subfolder containing the data, hoist it one level up."""
    subs = [p for p in src.iterdir() if p.is_dir()]
    if len(subs) == 1 and not any((src / d).exists() for d in ("train", "val", "test")):
        inner = subs[0]
        print(f"flattening nested folder {inner.name} -> {src}")
        for item in inner.iterdir():
            shutil.move(str(item), str(src / item.name))
        inner.rmdir()


def from_roboflow(args) -> None:
    api_key = os.environ.get("ROBOFLOW_API_KEY")
    if not api_key:
        raise SystemExit("ROBOFLOW_API_KEY not set (export ROBOFLOW_API_KEY=...)")

    from roboflow import Roboflow

    rf = Roboflow(api_key=api_key)
    project = rf.workspace(args.workspace).project(args.project)
    version = project.version(args.version)
    dest = DATASETS_ROOT / args.name
    dest.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {args.workspace}/{args.project}/v{args.version} -> {dest}")
    version.download("yolov8", location=str(dest))
    _flatten_nested(dest)
    _print_dataset_layout(dest)


def from_kaggle(args) -> None:
    dest = DATASETS_ROOT / args.name
    dest.mkdir(parents=True, exist_ok=True)
    print(f"Downloading kaggle {args.kaggle_dataset} -> {dest}")
    tmp = dest / ".download"
    tmp.mkdir(parents=True, exist_ok=True)

    # Use the Kaggle CLI installed in the same env (subprocess keeps creds handling native).
    import subprocess
    import sys

    cmd = [sys.executable, "-m", "kaggle", "datasets", "download",
           "-d", args.kaggle_dataset, "-p", str(tmp), "--unzip"]
    print("$", " ".join(cmd))
    subprocess.run(cmd, check=True)

    _flatten_nested(tmp)
    for item in tmp.iterdir():
        target = dest / item.name
        if target.exists():
            shutil.rmtree(target) if target.is_dir() else target.unlink()
        shutil.move(str(item), str(target))
    tmp.rmdir()
    print("extracted to:", dest)
    _print_dataset_layout(dest)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", choices=["roboflow", "kaggle"], required=True)
    parser.add_argument("--name", default="defects", help="Output folder under data/datasets/")
    parser.add_argument("--workspace", help="Roboflow workspace name")
    parser.add_argument("--project", help="Roboflow project name")
    parser.add_argument("--version", type=int, help="Roboflow dataset version number")
    parser.add_argument("--kaggle-dataset", help="Kaggle dataset id, e.g. owner/name")
    args = parser.parse_args()

    if args.source == "roboflow":
        if not (args.workspace and args.project and args.version):
            raise SystemExit("--source roboflow needs --workspace --project --version")
        from_roboflow(args)
    else:
        if not args.kaggle_dataset:
            raise SystemExit("--source kaggle needs --kaggle-dataset owner/name")
        from_kaggle(args)


if __name__ == "__main__":
    main()
