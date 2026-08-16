"""Run inference with a trained M1 model — NO dataset required.

Teammates download the published weights (GitHub Releases) and run this on any images.

Usage (in WSL or Docker):
  ~/safresale-ml/.venv/bin/python scripts/infer.py --weights <best.pt> --source <img_or_folder>
  docker compose -f ml/docker-compose.yml run --rm ml scripts/infer.py --weights /ml/work/runs/improved-yolo11n/weights/best.pt --source /ml/data/m1/sample.jpg
"""
import argparse
from pathlib import Path

from ultralytics import YOLO

DEFECT_CLASSES = ["scratch", "crack", "dent", "screen_damage", "port_damage", "camera_damage", "body_deformation"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", required=True, help="Path to trained weights (best.pt)")
    parser.add_argument("--source", required=True, help="Image path or folder of images")
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--save", default=True, help="Save annotated outputs next to inputs (_annotated)")
    args = parser.parse_args()

    if not Path(args.weights).exists():
        raise SystemExit(f"weights not found: {args.weights} — download from GitHub Releases first (see docs/10-setup-guide.md §8.3)")

    model = YOLO(args.weights)
    results = model.predict(source=args.source, conf=args.conf, save=args.save)

    for r in results:
        print(f"\nimage: {r.path}")
        if r.boxes is None or len(r.boxes) == 0:
            print("  no defects detected")
            continue
        for box in r.boxes:
            cls = int(box.cls[0])
            conf = float(box.conf[0])
            name = r.names.get(cls, DEFECT_CLASSES[cls] if cls < len(DEFECT_CLASSES) else str(cls))
            print(f"  {name:<16} conf={conf:.3f} bbox={[round(float(v),1) for v in box.xyxy[0].tolist()]}")


if __name__ == "__main__":
    main()
