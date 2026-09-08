"""Merge multiple downloaded defect datasets into one YOLO set aligned to the core-14 names.

Reads each source's layout (YOLO train/val/test + images/labels, or Pascal VOC XML) and a
per-source class map (source class name -> core class name, or empty/null to skip). Rewrites
label class ids, copies images, reports per-class instance counts, and writes a merged
data.yaml that lists only classes with >= min_instances boxes (core-14 from §3.1).

Config keys per source:
  name            short name for logging
  path            dir under M1_WORK_ROOT unless absolute
  format          "yolo" (default) | "voc"
  images_dir      for voc: dir of images (relative to path)
  annotations_dir for voc: dir of Pascal VOC .xml files (relative to path)
  class_map       source class name -> core class name ("" to drop)

VOC sources have no split folders, so images are split 70/15/15 deterministically by filename.

Usage:
  python scripts/prepare_dataset.py [--config configs/dataset_merge.yaml]

The config is generated/edited per acquisition pass (see configs/dataset_merge.yaml.example).
"""
import argparse
import hashlib
import shutil
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent.parent
WORK_ROOT = Path(__import__("os").environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))

CORE_CLASSES = [
    "scratch", "crack", "dent", "screen_damage", "glass_damage", "camera_damage",
    "port_damage", "casing_damage", "body_deformation", "paint_damage", "chip",
    "rust", "corrosion", "water_damage",
]
MIN_INSTANCES = 100


def normalize(name: str) -> str:
    return name.strip().lower().replace(" ", "_").replace("-", "_")


def voc_split(img_stem: str) -> str:
    """Deterministic 70/15/15 split from filename hash (voc sources have no splits)."""
    bucket = int(hashlib.sha256(img_stem.encode()).hexdigest(), 16) % 100
    if bucket < 70:
        return "train"
    if bucket < 85:
        return "val"
    return "test"


def merge_voc_source(src_dir: Path, images_dir: str, ann_dir: str,
                     class_map: dict[str, str], out_dir: Path, stats: Counter) -> None:
    """Convert Pascal VOC XML + images into YOLO labels under out_dir/<split>/."""
    img_dir = src_dir / images_dir
    xml_dir = src_dir / ann_dir
    for xml_path in sorted(xml_dir.glob("*.xml")):
        root = ET.parse(xml_path).getroot()
        size = root.find("size")
        if size is None:
            continue
        width = float(size.find("width").text)
        height = float(size.find("height").text)
        if width <= 0 or height <= 0:
            continue
        img_stem = xml_path.stem
        img_path = img_dir / f"{img_stem}.jpg"
        if not img_path.exists():
            img_path = img_dir / f"{img_stem}.JPG"
        if not img_path.exists():
            print(f"  [voc] missing image for {xml_path.name} — skipped")
            continue
        split = voc_split(img_stem)
        out_img = out_dir / split / "images" / img_path.name
        shutil.copy2(img_path, out_img)
        kept = []
        for obj in root.findall("object"):
            name = obj.find("name")
            bb = obj.find("bndbox")
            if name is None or bb is None:
                continue
            src_cls = name.text
            target = class_map.get(normalize(src_cls), class_map.get(src_cls))
            if not target:
                continue
            xmin = float(bb.find("xmin").text)
            ymin = float(bb.find("ymin").text)
            xmax = float(bb.find("xmax").text)
            ymax = float(bb.find("ymax").text)
            if xmax <= xmin or ymax <= ymin:
                continue
            xc = ((xmin + xmax) / 2) / width
            yc = ((ymin + ymax) / 2) / height
            bw = (xmax - xmin) / width
            bh = (ymax - ymin) / height
            if not (0 <= xc <= 1 and 0 <= yc <= 1):
                continue
            target_id = CORE_CLASSES.index(target)
            stats[target] += 1
            kept.append(f"{target_id} {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}\n")
        if kept:
            out_lab = out_dir / split / "labels" / f"{img_path.stem}.txt"
            with open(out_lab, "w") as f:
                f.writelines(kept)


def load_source_names(data_yaml: Path | None) -> dict[int, str]:
    """index -> class name from a YOLO data.yaml, or {} if absent."""
    if not data_yaml or not data_yaml.exists():
        return {}
    with open(data_yaml) as f:
        cfg = yaml.safe_load(f)
    names = cfg.get("names") or {}
    if isinstance(names, list):
        return {i: normalize(n) for i, n in enumerate(names)}
    if isinstance(names, dict):
        return {int(k): normalize(v) for k, v in names.items()}
    return {}


def merge_split(source_dir: Path, split: str, class_map: dict[str, str],
                src_names: dict[int, str], out_dir: Path, stats: Counter) -> None:
    img_dir = source_dir / split / "images"
    lab_dir = source_dir / split / "labels"
    if not img_dir.exists():
        return
    for img in sorted(img_dir.iterdir()):
        out_img = out_dir / split / "images" / img.name
        shutil.copy2(img, out_img)
        lab = lab_dir / f"{img.stem}.txt"
        out_lab = out_dir / split / "labels" / f"{img.stem}.txt"
        if not lab.exists():
            continue
        kept = []
        with open(lab) as f:
            for line in f:
                parts = line.split()
                if len(parts) < 5:
                    continue
                cls_id = int(parts[0])
                src_cls = src_names.get(cls_id) if src_names else str(cls_id)
                target = class_map.get(src_cls, class_map.get(str(cls_id)))
                if not target:
                    continue
                target_id = CORE_CLASSES.index(target)
                stats[target] += 1
                kept.append(f"{target_id} " + " ".join(parts[1:]) + "\n")
        if kept:
            with open(out_lab, "w") as f:
                f.writelines(kept)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default=str(HERE / "configs" / "dataset_merge.yaml"))
    args = ap.parse_args()

    cfg_path = Path(args.config)
    if not cfg_path.exists():
        sys.exit(f"config not found: {cfg_path} (copy configs/dataset_merge.yaml.example)")
    with open(cfg_path) as f:
        cfg = yaml.safe_load(f)

    out_name = cfg.get("output_name", "defects")
    min_instances = cfg.get("min_instances", MIN_INSTANCES)
    out_dir = WORK_ROOT / "data" / "datasets" / out_name
    for split in ("train", "val", "test"):
        (out_dir / split / "images").mkdir(parents=True, exist_ok=True)
        (out_dir / split / "labels").mkdir(parents=True, exist_ok=True)

    stats: Counter = Counter()
    for src in cfg["sources"]:
        src_dir = Path(src["path"])
        if not src_dir.is_absolute():
            src_dir = WORK_ROOT / src_dir
        class_map = {normalize(k): v for k, v in (src.get("class_map") or {}).items()}
        fmt = (src.get("format") or "yolo").lower()
        if fmt == "voc":
            images_dir = src.get("images_dir") or "images"
            ann_dir = src.get("annotations_dir") or "annotations"
            print(f"[{src['name']}] Pascal VOC source -> {images_dir} + {ann_dir}")
            merge_voc_source(src_dir, images_dir, ann_dir, class_map, out_dir, stats)
            continue
        src_names = load_source_names(src_dir / "data.yaml")
        if not src_names:
            print(f"[{src['name']}] no data.yaml — using raw class ids {sorted(class_map)}")
        else:
            print(f"[{src['name']}] source classes: {src_names}")
        for split in ("train", "val", "test"):
            merge_split(src_dir, split, class_map, src_names, out_dir, stats)

    present = {c: n for c, n in stats.items() if n > 0}
    print("\nPer-class boxes (merged):")
    for c in CORE_CLASSES:
        n = stats.get(c, 0)
        mark = "" if n >= min_instances else "  <-- BELOW THRESHOLD"
        print(f"  {c:18s} {n:6d}{mark}")
    dropped = set(CORE_CLASSES) - set(present)
    if dropped:
        print("\nClasses with no boxes (excluded from data.yaml):", ", ".join(sorted(dropped)))

    final_classes = [c for c in CORE_CLASSES if stats.get(c, 0) >= min_instances]
    if not final_classes:
        sys.exit("no classes met the threshold — check class_map / sources")
    out_yaml = out_dir / "data.yaml"
    with open(out_yaml, "w") as f:
        yaml.safe_dump({
            "path": str(out_dir),
            "train": "train/images",
            "val": "val/images",
            "test": "test/images",
            "names": {i: c for i, c in enumerate(final_classes)},
        }, f, sort_keys=False)
    print(f"\nWrote {out_yaml} with {len(final_classes)} classes: {final_classes}")
    print(f"Review classes in configs/m1.yaml if you want a different target set.")


if __name__ == "__main__":
    main()
