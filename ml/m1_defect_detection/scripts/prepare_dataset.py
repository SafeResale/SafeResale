"""Merge multiple downloaded defect datasets into one YOLO set aligned to the core-14 names.

Reads each source's layout (YOLO train/val/test + images/labels, or Pascal VOC XML) and a
per-source class map (source class name -> core class name, or empty/null to skip). Rewrites
label class ids, copies images, reports per-class instance counts, and writes a merged
data.yaml that lists only classes with >= min_instances boxes (core-14 from §3.1).

Two passes:
  1) scan all sources counting boxes per core class (respecting per-source class caps),
     then pick the final class set (>= min_instances).
  2) copy images + labels using the *final* class ids (core-14 order, stable across sources).

Per-source caps (`caps: {core_class: N}`) stop adding boxes of that class from that source
once N are reached, to prevent a single huge source (e.g. 68k laptop scratches) from
swamping smaller classes. Images whose boxes are entirely dropped by a cap are SKIPPED
(not emitted as background negatives); images with no keeper at all are kept as negatives.

Per-source `split_dirs: {train: <dir>, val: <dir>, test: <dir>}` lets a source use nonstandard
split folder names (e.g. mobile_damage uses `valid` instead of `val`).

Config keys per source:
  name            short name for logging
  path            dir under M1_WORK_ROOT unless absolute
  format          "yolo" (default) | "voc"
  images_dir      for voc: dir of images (relative to path)
  annotations_dir for voc: dir of Pascal VOC .xml files (relative to path)
  class_map       source class name -> core class name ("" to drop)
  caps            optional {core_class: max_boxes} per source
  split_dirs      optional {train: .., val: .., test: ..} for yolo sources

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


def _effective_split_dirs(src_dir: Path, src: dict) -> dict[str, str]:
    """Resolve yolo split folder names, falling back for Roboflow's standard `valid`.

    Default train/val/test; if `val` doesn't exist but `valid` does, use `valid`.
    """
    split_dirs = dict(src.get("split_dirs") or {})
    sdirs = {k: split_dirs.get(k, k) for k in ("train", "val", "test")}
    if not (src_dir / sdirs["val"] / "images").exists() and (src_dir / "valid" / "images").exists():
        sdirs["val"] = "valid"
    return sdirs


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


class CappedCounter:
    """Counts per-core-class boxes but ignores those beyond a per-source cap."""

    def __init__(self, caps: dict[str, int]):
        self.caps = caps or {}
        self.counts: Counter = Counter()

    def add_if_space(self, target: str) -> bool:
        """Returns True if this box counts (under cap)."""
        cap = self.caps.get(target)
        if cap is not None and self.counts[target] >= cap:
            return False
        self.counts[target] += 1
        return True


def _iter_voc_objs(src_dir: Path, images_dir: str, ann_dir: str, class_map: dict[str, str]):
    """Yield (img_path, split, kept_lines) for each voc image with keepable boxes."""
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
        lines: list[tuple[str, str, float, float, float, float]] = []
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
            lines.append((target, img_path.name, xc, yc, bw, bh))
        if lines:
            yield img_path, split, lines


def _iter_yolo_objs(source_dir: Path, split_dir: str, src_names: dict[int, str],
                    class_map: dict[str, str]):
    """Yield (img_path, lines_or_None) for each yolo image in split_dir.
    lines is None for background negatives (no label file)."""
    img_dir = source_dir / split_dir / "images"
    lab_dir = source_dir / split_dir / "labels"
    if not img_dir.exists():
        return
    for img in sorted(img_dir.iterdir()):
        lab = lab_dir / f"{img.stem}.txt"
        if not lab.exists():
            yield img, None  # background negative
            continue
        lines: list[tuple[str, str, float, float, float, float]] = []
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
                xc, yc, bw, bh = (float(x) for x in parts[1:5])
                lines.append((target, img.name, xc, yc, bw, bh))
        yield img, lines





def scan_source(src, src_dir, fmt, images_dir, ann_dir, class_map, caps,
                stats: CappedCounter) -> None:
    """Count boxes (under caps) per core class for the source."""
    if fmt == "voc":
        for _img, _split, lines in _iter_voc_objs(src_dir, images_dir, ann_dir, class_map):
            for target, *_rest in lines:
                stats.add_if_space(target)
        return
    src_names = load_source_names(src_dir / "data.yaml")
    sdirs = _effective_split_dirs(src_dir, src)
    for split in ("train", "val", "test"):
        for _img, lines in _iter_yolo_objs(src_dir, sdirs[split], src_names, class_map):
            if lines is None:
                continue
            for target, *_rest in lines:
                stats.add_if_space(target)


def merge_source(src, src_dir, fmt, images_dir, ann_dir, class_map, caps,
                 out_dir, final_id: dict[str, int]) -> Counter:
    """Copy source into out_dir using final class ids; returns per-class boxes actually written."""
    stats: Counter = Counter()
    used: Counter = Counter()  # boxes written from THIS source, per class
    split_dirs = src.get("split_dirs") or {}

    if fmt == "voc":
        for img_path, split, lines in _iter_voc_objs(src_dir, images_dir, ann_dir, class_map):
            kept, capped_hit = _apply_caps(lines, caps, used)
            if capped_hit and not kept:  # all boxes dropped by cap -> skip, not a negative
                continue
            _emit(img_path, split, kept, out_dir, final_id, stats)
        return stats

    src_names = load_source_names(src_dir / "data.yaml")
    sdirs = _effective_split_dirs(src_dir, src)
    for split in ("train", "val", "test"):
        for img_path, lines in _iter_yolo_objs(src_dir, sdirs[split], src_names, class_map):
            if lines is None:
                _emit(img_path, split, [], out_dir, final_id, stats)  # background negative
                continue
            kept, capped_hit = _apply_caps(lines, caps, used)
            if capped_hit and not kept:
                continue  # all boxes dropped by cap -> skip, not a negative
            _emit(img_path, split, kept, out_dir, final_id, stats)
    return stats


def _apply_caps(lines, caps, used: Counter):
    """Filter box lines for classes over their per-source cap.

    Returns (kept_lines, capped_hit) where capped_hit=True when at least one line was
    dropped *because the cap was hit* (not because the class isn't in final set).
    """
    caps = caps or {}
    kept: list = []
    capped_hit = False
    for target, name, xc, yc, bw, bh in lines:
        cap = caps.get(target)
        if cap is not None:
            if used[target] >= cap:
                capped_hit = True
                continue
            used[target] += 1
        kept.append((target, name, xc, yc, bw, bh))
    return kept, capped_hit


def _emit(img_path: Path, split: str, lines, out_dir: Path, final_id: dict[str, int],
          stats: Counter) -> None:
    """Copy image; write label (with final class ids) if lines non-empty."""
    out_img = out_dir / split / "images" / img_path.name
    shutil.copy2(img_path, out_img)
    if not lines:
        return  # negative image: no label
    kept = []
    for target, name, xc, yc, bw, bh in lines:
        if target not in final_id:
            continue
        stats[target] += 1
        kept.append(f"{final_id[target]} {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}\n")
    if kept:
        out_lab = out_dir / split / "labels" / f"{img_path.stem}.txt"
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

    sources = []
    for src in cfg["sources"]:
        src_dir = Path(src["path"])
        if not src_dir.is_absolute():
            src_dir = WORK_ROOT / src_dir
        class_map = {normalize(k): v for k, v in (src.get("class_map") or {}).items()}
        fmt = (src.get("format") or "yolo").lower()
        images_dir = src.get("images_dir") or "images"
        ann_dir = src.get("annotations_dir") or "annotations"
        caps = {normalize(k): int(v) for k, v in (src.get("caps") or {}).items()}
        sources.append((src, src_dir, fmt, images_dir, ann_dir, class_map, caps))

    # Pass 1: count boxes under caps -> decide final class set
    scan_stats: Counter = Counter()
    for src, src_dir, fmt, images_dir, ann_dir, class_map, caps in sources:
        sc = CappedCounter(caps)
        scan_source(src, src_dir, fmt, images_dir, ann_dir, class_map, caps, sc)
        scan_stats.update(sc.counts)

    print("Per-class boxes (capped scan):")
    for c in CORE_CLASSES:
        n = scan_stats.get(c, 0)
        mark = "" if n >= min_instances else "  <-- BELOW THRESHOLD"
        print(f"  {c:18s} {n:6d}{mark}")

    final_classes = [c for c in CORE_CLASSES if scan_stats.get(c, 0) >= min_instances]
    if not final_classes:
        sys.exit("no classes met the threshold — check class_map / caps / sources")
    final_id = {c: i for i, c in enumerate(final_classes)}
    print(f"\nFinal classes ({len(final_classes)}): {final_classes}")
    print(f"final_id: {final_id}")

    # Pass 2: rebuild output dirs
    if out_dir.exists():
        print(f"\nResetting {out_dir} (removing stale images/labels)")
        shutil.rmtree(out_dir)
    for split in ("train", "val", "test"):
        (out_dir / split / "images").mkdir(parents=True, exist_ok=True)
        (out_dir / split / "labels").mkdir(parents=True, exist_ok=True)

    written: Counter = Counter()
    for src, src_dir, fmt, images_dir, ann_dir, class_map, caps in sources:
        print(f"\n[{src['name']}] merging...")
        written.update(
            merge_source(src, src_dir, fmt, images_dir, ann_dir, class_map, caps,
                         out_dir, final_id))
        per_split = {s: len(list((out_dir / s / "images").glob("*"))) for s in ("train", "val", "test")}
        print(f"  total images so far: {per_split}")

    print("\nPer-class boxes (written):")
    for c in CORE_CLASSES:
        n = written.get(c, 0)
        print(f"  {c:18s} {n:6d}")

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
    print("Review classes in configs/m1.yaml if you want a different target set.")


if __name__ == "__main__":
    main()