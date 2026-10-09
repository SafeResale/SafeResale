"""Convert MVTec AD (HF FiftyOne export) from per-pixel masks to YOLO bounding boxes.

Reads samples.json to find defective images + their ground-truth mask paths,
converts each mask region to a bounding box, maps defect labels to core-14
classes, and writes a YOLO-format dataset at data/raw/mvtec_yolo/ with a
deterministic 70/15/15 split.

Relevant MVTec defect -> core-14 mapping (only defects >=100 boxes kept):
  scratch       -> scratch
  crack         -> crack
  bent          -> dent        (mechanical deformation)
  damaged_case  -> dent

All others (contamination, hole, cut, broken, etc.) are dropped as they
don't map cleanly to core-14 or are below the 100-instance threshold.

Usage:
  python scripts/convert_mvtec.py
"""
import hashlib
import shutil
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

WORK = Path("/home/irahu/safresale-ml/m1")
MVTEC = WORK / "data" / "raw" / "mvtec_ad"
OUT = WORK / "data" / "raw" / "mvtec_yolo"
PREFIX = "mvtec"

# MVTec defect label -> core-14 class (None = skip)
DEFECT_MAP = {
    "scratch": "scratch",
    "crack": "crack",
    "bent": "dent",
    "damaged_case": "dent",
}


def split_key(stem: str) -> str:
    bucket = int(hashlib.sha256(stem.encode()).hexdigest(), 16) % 100
    if bucket < 70:
        return "train"
    if bucket < 85:
        return "val"
    return "test"


def mask_to_yolo_boxes(mask: np.ndarray, class_id: int) -> list[str]:
    """Convert a binary mask (H, W) uint8 [0/255] to YOLO label lines."""
    ys, xs = np.where(mask > 127)
    if len(ys) == 0:
        return []
    H, W = mask.shape
    # Bounding box of all non-zero pixels (single box per image)
    x0, x1 = int(xs.min()), int(xs.max())
    y0, y1 = int(ys.min()), int(ys.max())
    xc = ((x0 + x1) / 2) / W
    yc = ((y0 + y1) / 2) / H
    bw = (x1 - x0) / W
    bh = (y1 - y0) / H
    if bw <= 0 or bh <= 0:
        return []
    return [f"{class_id} {xc:.6f} {yc:.6f} {bw:.6f} {bh:.6f}"]


def main() -> None:
    import json
    with open(MVTEC / "samples.json") as f:
        samples = json.load(f)["samples"]

    stats = Counter()
    written = 0
    skipped = 0

    # Create YOLO dirs
    for split in ("train", "val", "test"):
        (OUT / split / "images").mkdir(parents=True, exist_ok=True)
        (OUT / split / "labels").mkdir(parents=True, exist_ok=True)

    for i, s in enumerate(samples):
        defect = s["defect"]["label"]
        core = DEFECT_MAP.get(defect)
        if core is None:
            skipped += 1
            continue

        # Find mask
        dm = s.get("defect_mask")
        if dm is None or "mask_path" not in dm:
            skipped += 1
            continue

        mask_path = MVTEC / dm["mask_path"]
        if not mask_path.exists():
            skipped += 1
            continue

        # Load mask
        mask = np.array(Image.open(mask_path))
        class_id = ["scratch", "crack", "dent"].index(core)
        label_lines = mask_to_yolo_boxes(mask, class_id)
        if not label_lines:
            skipped += 1
            continue

        # Source image path
        src_img = MVTEC / s["filepath"]
        if not src_img.exists():
            skipped += 1
            continue

        # Deterministic split
        stem = f"{PREFIX}_{i:05d}"
        split = split_key(stem)

        # Copy image -> jpg
        dst_img = OUT / split / "images" / f"{stem}.jpg"
        img = Image.open(src_img)
        img.convert("RGB").save(dst_img, "JPEG", quality=95)

        # Write label
        (OUT / split / "labels" / f"{stem}.txt").write_text(
            "\n".join(label_lines) + "\n"
        )
        written += 1
        stats[core] += 1

    # Write data.yaml — include all present classes (threshold applied later by prepare_dataset.py)
    present = sorted(stats.keys(), key=lambda c: ["scratch", "crack", "dent"].index(c))
    out_names = {i: c for i, c in enumerate(present)}
    yaml_path = OUT / "data.yaml"
    import yaml
    with open(yaml_path, "w") as f:
        yaml.safe_dump({
            "path": str(OUT),
            "train": "train/images",
            "val": "val/images",
            "test": "test/images",
            "names": out_names,
        }, f, sort_keys=False)

    print(f"converted {written} images, skipped {skipped}")
    print("per-class:", dict(stats))
    print(f"wrote {yaml_path} with {len(out_names)} classes")


if __name__ == "__main__":
    main()
