"""Package all images containing screen_damage boxes for Roboflow re-annotation.

Creates:
  <work>/roboflow_annotate/images/   - copies of the images (original names kept)
  <work>/roboflow_annotate/manifest.json - maps each file to its split + current boxes
  <work>/roboflow_annotate/upload.zip    - ready to drag into Roboflow
"""
import json
import sys
import zipfile
from pathlib import Path

import cv2
import yaml

WORK = Path("/home/irahu/safresale-ml/m1")
DATA = WORK / "data" / "datasets" / "defects"
OUT = WORK / "roboflow_annotate"
MAX_SIDE = 1280  # annotation copies only; YOLO boxes are normalized so they map back 1:1

names = yaml.safe_load((DATA.parent.parent / "datasets" / "defects" / "data.yaml").read_text())
# data.yaml may live elsewhere; fall back to known order
if isinstance(names, dict) and "names" in names:
    n = names["names"]
    class_names = [n[k] if isinstance(n, dict) else k for k in (n.keys() if isinstance(n, dict) else range(len(n)))]
else:
    class_names = ["scratch", "crack", "dent", "screen_damage", "glass_damage"]
SD_IDX = class_names.index("screen_damage")
print(f"class order: {class_names}; screen_damage idx={SD_IDX}")

(OUT / "images").mkdir(parents=True, exist_ok=True)

manifest = []
for split in ("train", "val"):
    img_dir = DATA / split / "images"
    lbl_dir = DATA / split / "labels"
    for lf in sorted(lbl_dir.glob("*.txt")):
        boxes = []
        for line in lf.read_text().splitlines():
            parts = line.split()
            if len(parts) == 5 and int(parts[0]) == SD_IDX:
                boxes.append({
                    "cx": float(parts[1]), "cy": float(parts[2]),
                    "w": float(parts[3]), "h": float(parts[4]),
                })
        if not boxes:
            continue
        src = None
        for ext in (".jpg", ".jpeg", ".png"):
            cand = img_dir / (lf.stem + ext)
            if cand.exists():
                src = cand
                break
        if src is None:
            print(f"WARN no image for {lf}", file=sys.stderr)
            continue
        dst = OUT / "images" / src.name
        if not dst.exists():
            img = cv2.imread(str(src))
            if img is None:
                print(f"WARN unreadable: {src}", file=sys.stderr)
                continue
            h, w = img.shape[:2]
            scale = MAX_SIDE / max(h, w)
            if scale < 1.0:
                img = cv2.resize(img, (int(w * scale), int(h * scale)),
                                 interpolation=cv2.INTER_AREA)
            cv2.imwrite(str(dst), img, [cv2.IMWRITE_JPEG_QUALITY, 90])
        manifest.append({"split": split, "file": src.name, "current_boxes": boxes})

(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2))

zip_path = OUT / "upload.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    for m in manifest:
        z.write(OUT / "images" / m["file"], f"images/{m['file']}")

n_train = sum(1 for m in manifest if m["split"] == "train")
print(f"packaged {len(manifest)} images ({n_train} train, {len(manifest)-n_train} val), "
      f"{sum(len(m['current_boxes']) for m in manifest)} existing screen_damage boxes")
print(f"zip: {zip_path} ({zip_path.stat().st_size/1e6:.1f} MB)")
