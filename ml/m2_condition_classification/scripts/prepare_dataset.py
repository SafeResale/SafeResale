"""Build a balanced 70/15/15 split manifest from a labeled condition dataset.

Input layouts (under $M2_WORK_ROOT/data/datasets/<name>):
  1) class folders directly:   <name>/<class>/*.jpg            (re-split here)
  2) roboflow classification:  <name>/{train,valid,test}/<class>/*.jpg
  3) CSV:                      <name>/labels.csv  (columns: path,label)

Source labels are remapped to good/moderate/defective via a class-map YAML
(copy configs/class_map.yaml.example -> configs/class_map.yaml and edit).
Unmapped labels are dropped. The frozen test split is NEVER used in training.

Writes $M2_WORK_ROOT/data/condition/<out>/splits.json, consumed by scripts/train.py
and scripts/evaluate.py.

Usage:
  ~/ml312/bin/python scripts/prepare_dataset.py --dataset <name> [--out <name>] \
    [--class-map configs/class_map.yaml] [--seed 42] [--min-per-class 20]
"""
import argparse
import csv
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import yaml

import common

IMG_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def collect_folder(dataset_dir: Path) -> list[tuple[Path, str]]:
    """(path, label) from a class-folder or Roboflow train/valid/test layout."""
    samples: list[tuple[Path, str]] = []
    splits = [s for s in ("train", "valid", "test") if (dataset_dir / s).is_dir()]
    roots = [(dataset_dir / s) for s in splits] or [dataset_dir]
    for root in roots:
        for cls_dir in sorted(p for p in root.iterdir() if p.is_dir()):
            for img in sorted(p for p in cls_dir.iterdir() if p.suffix.lower() in IMG_EXTS):
                samples.append((img, cls_dir.name))
    return samples


def collect_csv(dataset_dir: Path) -> list[tuple[Path, str]]:
    samples: list[tuple[Path, str]] = []
    csv_path = dataset_dir / "labels.csv"
    if not csv_path.exists():
        raise SystemExit(f"no labels.csv at {csv_path} — use a class-folder layout instead")
    with open(csv_path, newline="") as f:
        for row in csv.DictReader(f):
            p = Path(row["path"])
            if not p.is_absolute():
                p = dataset_dir / p
            samples.append((p, row["label"].strip()))
    return samples


def apply_class_map(samples: list[tuple[Path, str]], class_map: dict) -> list[tuple[Path, str]]:
    """Reverse-lookup mapping: target -> [source labels]. Unmapped = dropped."""
    if not class_map:
        return samples
    lookup = {}
    for target, sources in class_map.items():
        for s in sources:
            lookup[str(s).strip().lower().replace(" ", "_")] = target
    out = []
    for path, label in samples:
        target = lookup.get(label.strip().lower().replace(" ", "_"))
        if target:
            out.append((path, target))
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", required=True, help="source dir under data/datasets/<name>")
    ap.add_argument("--out", default=None, help="manifest name under data/condition/ (default: <dataset>)")
    ap.add_argument("--class-map", default=None, help="YAML remap config (copy class_map.yaml.example)")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--min-per-class", type=int, default=20, help="warn below this many images/class")
    args = ap.parse_args()

    dataset_dir = common.WORK_ROOT / "data" / "datasets" / args.dataset
    if not dataset_dir.is_dir():
        raise SystemExit(f"dataset dir not found: {dataset_dir} — run scripts/download_dataset.py first")

    class_map = {}
    if args.class_map:
        cm_path = Path(args.class_map)
        if not cm_path.is_absolute():
            cm_path = common.HERE / cm_path
        if not cm_path.exists():
            raise SystemExit(f"class-map not found: {cm_path}")
        with open(cm_path) as f:
            class_map = yaml.safe_load(f) or {}

    samples = collect_csv(dataset_dir) if (dataset_dir / "labels.csv").exists() else collect_folder(dataset_dir)
    samples = apply_class_map(samples, class_map)
    if not samples:
        raise SystemExit("no samples after class mapping — check configs/class_map.yaml")

    counts = Counter(lbl for _, lbl in samples)
    classes = sorted(counts)
    if not class_map:
        print(f"no class-map used — labels kept as-is: {classes}")

    train, val, test = common.split_by_class(samples, classes, seed=args.seed)

    def rel(p: Path) -> str:
        try:
            return p.relative_to(common.WORK_ROOT).as_posix()
        except ValueError:
            return p.as_posix()

    print(f"Per-class images (source):")
    for c in classes:
        mark = "" if counts[c] >= args.min_per_class else "  <-- BELOW --min-per-class"
        print(f"  {c:12s} {counts[c]:6d}{mark}")
    print("\nSplit sizes (stratified 70/15/15):")
    for name, split in (("train", train), ("val", val), ("test", test)):
        per = Counter(lbl for _, lbl in split)
        print(f"  {name:5s} {len(split):6d}  {dict(per)}")

    out_name = args.out or args.dataset
    out_dir = common.WORK_ROOT / "data" / "condition" / out_name
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "dataset": out_name,
        "source": str(dataset_dir),
        "classes": classes,
        "seed": args.seed,
        "split_ratios": [0.7, 0.15, 0.15],
        "per_class": dict(counts),
        "train": [[rel(p), lbl] for p, lbl in train],
        "val": [[rel(p), lbl] for p, lbl in val],
        "test": [[rel(p), lbl] for p, lbl in test],
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    with open(out_dir / "splits.json", "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"\nWrote {out_dir / 'splits.json'} ({len(manifest['train'])}/{len(manifest['val'])}/{len(manifest['test'])} train/val/test)")
    print("Next: set dataset/name in configs/m2.yaml to this --out name, then train.")


if __name__ == "__main__":
    main()
