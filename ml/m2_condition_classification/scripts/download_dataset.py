"""Download a condition-classification dataset from Roboflow Universe or Kaggle.

Two source modes:

1) Roboflow (classification projects)
   Requires a free Roboflow API key: https://app.roboflow.com/settings/api
     export ROBOFLOW_API_KEY=...
   Usage:
     python scripts/download_dataset.py --source roboflow --workspace <ws> \
       --project <proj> --version <n> [--name <name>]

2) Kaggle
   Requires Kaggle API credentials: https://www.kaggle.com/settings -> Create API token
   Place the token JSON at ~/.kaggle/kaggle.json (or set KAGGLE_USERNAME/KAGGLE_KEY).
   Usage:
     python scripts/download_dataset.py --source kaggle --kaggle-dataset <owner/name> \
       [--name <name>] [--as-class <label>]

     Flat sets (no per-class folders) become a single class folder: pass
     --as-class <label> to gather every image under data/datasets/<name>/<label>/ so
     prepare_dataset.py can map it (e.g. --as-class cracked-screen -> moderate).

Downloads into ~/safresale-ml/m2/data/datasets/<name> (WSL home, not /mnt/c) so training
reads fast and paths stay clean. Override root with M2_WORK_ROOT.

Pick license-permitted public damage/condition sets (docs/08-ml-plan.md §3.2) and document
URL + license per dataset in the report. Then run scripts/prepare_dataset.py to map source
labels -> good/moderate/defective and build the 70/15/15 split manifest.
"""
import argparse
import os
import shutil
from pathlib import Path

import common

DATASETS_ROOT = common.WORK_ROOT / "data" / "datasets"

IMG_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def _print_dataset_layout(dest: Path) -> None:
    """Show class folders + split sizes so we know how to wire the class_map."""
    print("top-level entries:", sorted(p.name for p in dest.iterdir() if p.is_dir()))
    for split in ("train", "valid", "test"):
        d = dest / split
        if not d.is_dir():
            continue
        total = 0
        classes = []
        for cls_dir in sorted(d.iterdir()):
            if not cls_dir.is_dir():
                continue
            n = len(list(cls_dir.iterdir()))
            total += n
            classes.append(f"{cls_dir.name}={n}")
        print(f"  {split}: {total} images | {', '.join(classes)}")
    print("Next: remap source labels in configs/class_map.yaml, then run prepare_dataset.py.")


def _flatten_nested(src: Path) -> None:
    """If Kaggle extracts one subfolder containing the data, hoist it one level up."""
    subs = [p for p in src.iterdir() if p.is_dir()]
    if len(subs) == 1 and not any((src / d).exists() for d in ("train", "valid", "test")):
        inner = subs[0]
        print(f"flattening nested folder {inner.name} -> {src}")
        for item in inner.iterdir():
            shutil.move(str(item), str(src / item.name))
        inner.rmdir()


def collect_class_images(src: Path, label: str, dest: Path) -> int:
    """Gather every image under src into dest/<label>/ (one flat class folder)."""
    cls_dir = dest / label
    cls_dir.mkdir(parents=True, exist_ok=True)
    n = 0
    for p in sorted(src.rglob("*")):
        if p.is_file() and p.suffix.lower() in IMG_EXTS:
            target = cls_dir / p.name
            if target.exists():
                target = cls_dir / f"{p.parent.name}_{p.stem}{p.suffix.lower()}"
            shutil.move(str(p), str(target))
            n += 1
    for p in sorted((d for d in src.rglob("*") if d.is_dir()), reverse=True):
        try:
            p.rmdir()
        except OSError:
            pass
    return n


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
    # "folder" = classification layout: train/valid/test/<class>/... (Roboflow)
    version.download("folder", location=str(dest))
    _flatten_nested(dest)
    _print_dataset_layout(dest)


def from_kaggle(args) -> None:
    dest = DATASETS_ROOT / args.name
    dest.mkdir(parents=True, exist_ok=True)
    print(f"Downloading kaggle {args.kaggle_dataset} -> {dest}")
    tmp = dest / ".download"
    tmp.mkdir(parents=True, exist_ok=True)

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

    if args.as_class:
        n = collect_class_images(dest, args.as_class, dest)
        print(f"collected {n} images into {dest / args.as_class}/ (class: {args.as_class})")
    print("extracted to:", dest)
    _print_dataset_layout(dest)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", choices=["roboflow", "kaggle"], required=True)
    parser.add_argument("--name", default="condition", help="Output folder under data/datasets/")
    parser.add_argument("--workspace", help="Roboflow workspace name")
    parser.add_argument("--project", help="Roboflow project name")
    parser.add_argument("--version", type=int, help="Roboflow dataset version number")
    parser.add_argument("--kaggle-dataset", help="Kaggle dataset id, e.g. owner/name")
    parser.add_argument("--as-class", default=None,
                        help="collect flat images into <name>/<label>/ (single class folder)")
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
