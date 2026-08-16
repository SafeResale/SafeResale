"""Merge downloaded condition datasets into one class-folder dataset.

The M2 model grades `good | moderate | defective`. No single free dataset has all three
grades (docs/08-ml-plan.md §3.2), so we combine license-permitted sources into one
class-folder layout that prepare_dataset.py can split, e.g.:

  damaged-vs-good-packages  (intact=good, damaged=defective)   CC BY 4.0
  cracked-screen-dataset    (cracked screens = moderate)       CC0 Public Domain

Usage:
  python scripts/combine_datasets.py --out condition_combined \
      --src condition_packages/intact \
      --src condition_packages/damaged \
      --src condition_cracked/cracked-screen:1000 \
      --overwrite

Each --src is DATASET/CLASS[:CAP]:
  - DATASET = folder under $M2_WORK_ROOT/data/datasets/ (see download_dataset.py)
  - CLASS   = class folder; Roboflow split layouts ({train,valid,test}/<class>) are merged
  - CAP     = optional max images to copy from that class (imbalance control)

Images are copied (not moved) so the source datasets stay intact for re-runs.
Then map source labels -> good/moderate/defective in configs/class_map.yaml and run
scripts/prepare_dataset.py on the combined name.
"""
import argparse
import shutil
from pathlib import Path

import common

IMG_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def images_for_class(dataset_dir: Path, cls: str) -> list[Path]:
    """All images of class <cls> in a Roboflow split or flat class-folder layout."""
    has_splits = any((dataset_dir / s).is_dir() for s in ("train", "valid", "test"))
    roots = [dataset_dir / s / cls for s in ("train", "valid", "test")] if has_splits \
        else [dataset_dir / cls]
    imgs = []
    for root in roots:
        if not root.is_dir():
            continue
        for p in sorted(root.iterdir()):
            if p.is_file() and p.suffix.lower() in IMG_EXTS:
                imgs.append(p)
    return imgs


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="combined dataset name under data/datasets/")
    ap.add_argument("--src", action="append", required=True,
                    help="DATASET/CLASS[:CAP] source (repeatable)")
    ap.add_argument("--overwrite", action="store_true", help="wipe --out first")
    args = ap.parse_args()

    datasets_root = common.WORK_ROOT / "data" / "datasets"
    out_dir = datasets_root / args.out
    if out_dir.exists():
        if not args.overwrite:
            raise SystemExit(f"{out_dir} already exists - pass --overwrite to rebuild")
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    totals = {}
    for spec in args.src:
        cls_spec, _, cap_s = spec.partition(":")
        cap = int(cap_s) if cap_s else None
        ds_name, _, cls_name = cls_spec.rpartition("/")
        dataset_dir = datasets_root / ds_name
        if not dataset_dir.is_dir():
            print(f"skip {spec}: dataset '{ds_name}' not found - run download_dataset.py first")
            continue
        imgs = images_for_class(dataset_dir, cls_name)
        if not imgs:
            print(f"skip {spec}: no images for class '{cls_name}' in {ds_name}")
            continue
        print(f"{spec:44s} found {len(imgs)} images")
        cls_out = out_dir / cls_name
        cls_out.mkdir(exist_ok=True)
        n = 0
        for img in imgs:
            if cap is not None and n >= cap:
                break
            dst = cls_out / img.name
            if dst.exists():
                dst = cls_out / f"{img.stem}_{n}{img.suffix.lower()}"
            shutil.copy2(img, dst)
            n += 1
        totals[cls_name] = n
        print(f"  -> {cls_out} ({n} images)" + (f", cap {cap} applied" if cap is not None else ""))

    if not totals:
        raise SystemExit("nothing copied - check --src entries")

    print("\ncombined dataset:", out_dir)
    for cls, n in sorted(totals.items()):
        print(f"  {cls:16s} {n}")
    print("\nNext: map labels in configs/class_map.yaml, then run:\n"
          f"  python scripts/prepare_dataset.py --dataset {args.out} "
          f"--out {args.out} --class-map configs/class_map.yaml")


if __name__ == "__main__":
    main()
