"""Compare local YOLO vs Roboflow hosted workflow on device photos.

Diagnoses reflection false positives: if Roboflow's model also flags clean
reflections, the confusion is inherent to visual patterns; if it doesn't,
our training data is the problem.

Usage (WSL):
  export ROBOFLOW_API_KEY=...   # from app.roboflow.com
  ~/safresale-ml/.venv/bin/python scripts/compare_models.py \
    --weights <best.pt> --source <folder> \
    --rf-workspace mr-x-musom --rf-workflow general-segmentation-api
"""
import argparse
import os
import sys
from pathlib import Path

from ultralytics import YOLO


def run_local(model, names, path: Path, conf: float) -> list:
    r = model.predict(source=str(path), conf=conf, verbose=False)[0]
    out = []
    if r.boxes is not None:
        for box in r.boxes:
            cls = int(box.cls[0])
            out.append((names.get(cls, str(cls)), float(box.conf[0])))
    return out


def summarize_rf(result) -> str:
    """Extract class@conf (+boxes) from a Roboflow workflow result, skipping base64 images."""
    parts = []

    def walk(node):
        if isinstance(node, dict):
            if "class_name" in node or ("class" in node and "confidence" in node):
                cls = node.get("class_name", node.get("class"))
                conf = node.get("confidence")
                box = node.get("bbox") or node.get("box")
                b = f" box={tuple(round(v) for v in box.values())}" if isinstance(box, dict) else ""
                parts.append(f"{cls}@{conf:.2f}{b}" if isinstance(conf, float) else f"{cls}{b}")
            elif "predictions" in node and isinstance(node["predictions"], list):
                for pr in node["predictions"]:
                    walk(pr)
            else:
                for v in node.values():
                    walk(v)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(result)
    return "; ".join(parts) if parts else "no detections"


def run_roboflow(path: Path, workspace: str, workflow: str, classes: str) -> str:
    from inference_sdk import InferenceHTTPClient
    client = InferenceHTTPClient(
        api_url="https://serverless.roboflow.com",
        api_key=os.environ["ROBOFLOW_API_KEY"],
    )
    result = client.run_workflow(
        workspace_name=workspace,
        workflow_id=workflow,
        images={"image": str(path)},
        parameters={"classes": classes},
        use_cache=True,
    )
    return summarize_rf(result)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--rf-workspace", default="mr-x-musom")
    parser.add_argument("--rf-workflow", default="general-segmentation-api")
    parser.add_argument("--rf-classes", default="scratch, dead pixel, screen crack")
    args = parser.parse_args()

    if not os.environ.get("ROBOFLOW_API_KEY"):
        sys.exit("set ROBOFLOW_API_KEY first (export ROBOFLOW_API_KEY=...)")

    src = Path(args.source)
    images = sorted(p for p in src.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
    if not images:
        sys.exit(f"no images in {src}")

    model = YOLO(args.weights)
    names = model.names

    for p in images:
        local = run_local(model, names, p, args.conf)
        local_str = ", ".join(f"{n}@{c:.2f}" for n, c in local) or "clean"
        print(f"\n=== {p.name} ===")
        print(f"  local YOLO : {local_str}")
        try:
            rf = run_roboflow(p, args.rf_workspace, args.rf_workflow, args.rf_classes)
            print(f"  roboflow   : {rf}")
        except Exception as e:
            print(f"  roboflow   : ERROR {type(e).__name__}: {e}")


if __name__ == "__main__":
    main()
