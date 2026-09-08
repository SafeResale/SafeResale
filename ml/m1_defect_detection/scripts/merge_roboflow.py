"""Merge the Roboflow mobile_damage train split into our YOLO dataset.

Class mapping (Roboflow -> ours, indices in our data.yaml):
  0 'dead pixel'   -> DROPPED (not in our 5-core vocab)
  1 'scratch'      -> 0 (scratch)
  2 'screen crack' -> 3 (screen_damage)

Images with a dropped-only label (dead pixel with no other keepable box) are NOT
copied, so we never introduce background noise. Images that keep >=1 box are copied
with only the keepable classes retained.

We use ONLY the 'train' split; the RF 'valid' split is left out entirely.
"""
import shutil
from pathlib import Path

import cv2

WORK = Path("/home/irahu/safresale-ml/m1")
RF = WORK / "data" / "raw" / "mobile_damage" / "Mobile Damage Diagnosis.yolov11" / "train"
DEST_IMG = WORK / "data" / "datasets" / "defects" / "train" / "images"
DEST_LBL = WORK / "data" / "datasets" / "defects" / "train" / "labels"

MAP = {1: 0, 2: 3}   # scratch->0, screen crack->3 ; dead pixel(0) dropped
PREFIX = "rfmd"      # prefix to avoid name collisions

copied_img = copied_box = dropped_img = 0
kept = {0: 0, 3: 0}

for lf in sorted((RF / "labels").glob("*.txt")):
    keep_lines = []
    for line in lf.read_text().splitlines():
        p = line.split()
        if len(p) != 5:
            continue
        src_cls = int(p[0])
        if src_cls not in MAP:
            continue
        keep_lines.append(f"{MAP[src_cls]} {p[1]} {p[2]} {p[3]} {p[4]}")

    if not keep_lines:
        dropped_img += 1
        continue

    # find the matching image file
    img_src = None
    for ext in (".jpg", ".jpeg", ".png"):
        cand = RF / "images" / (lf.stem + ext)
        if cand.exists():
            img_src = cand
            break
    if img_src is None:
        print(f"WARN no image for {lf.stem}")
        continue

    stem = f"{PREFIX}_{lf.stem}"
    # copy image (re-encode to jpg for consistent handling)
    img = cv2.imread(str(img_src))
    if img is None:
        print(f"WARN unreadable {img_src.name}")
        continue
    DEST_IMG.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(DEST_IMG / f"{stem}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 95])
    DEST_LBL.mkdir(parents=True, exist_ok=True)
    (DEST_LBL / f"{stem}.txt").write_text("\n".join(keep_lines) + "\n")

    copied_img += 1
    for line in keep_lines:
        c = int(line.split()[0])
        kept[c] = kept.get(c, 0) + 1
        copied_box += 1

print(f"merged {copied_img} images, {copied_box} boxes; dropped {dropped_img} dead-pixel-only images")
print(f"kept boxes: scratch={kept[0]}, screen_damage={kept[3]}")
