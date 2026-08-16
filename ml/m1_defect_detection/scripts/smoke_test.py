"""Smoke test: 1 epoch on ultralytics' coco8 to verify the full GPU pipeline in WSL.

Runs entirely under ~/safresale-ml/m1 (clean path + fast I/O), not /mnt/c.
"""
import os
from pathlib import Path

from ultralytics import YOLO

WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))
runs_dir = WORK_ROOT / "runs"
runs_dir.mkdir(parents=True, exist_ok=True)

m = YOLO("yolo11n.pt")
m.train(data="coco8.yaml", epochs=1, imgsz=640, device=0, project=str(runs_dir), name="smoke", plots=False)
r = m.val(data="coco8.yaml", project=str(runs_dir), name="smoke-val")
print(f"SMOKE OK | mAP50={r.box.map50:.4f} mAP50-95={r.box.map:.4f}")
