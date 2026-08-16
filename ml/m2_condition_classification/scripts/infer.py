"""Run condition inference with a trained M2 model — NO dataset required.

Teammates download the published weights (GitHub Releases) and run this on any
images — mirrors scripts/infer.py in m1_defect_detection. Output matches the
condition block of the run-vision contract (docs/04-api-contract.md §run-vision)
and condition_predictions (docs/05-data-model.md §2.7).

Also aggregates several views with optional per-angle quality weights
(docs/01-prd.md §10.3: "Aggregate eight-view probabilities using image-quality weights").

Usage:
  ~/ml312/bin/python scripts/infer.py --model runs/baseline-m3small/model.keras --source <img|folder>
  ~/ml312/bin/python scripts/infer.py --model ... --source <8view_folder> \
      --quality-weights '{"front":0.9,"back":0.8,"left":0.7,"right":0.7,"top":0.6,"bottom":0.6,"front45":0.5,"back45":0.5}'
"""
import argparse
import json
import os
from pathlib import Path

import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("--model", required=True, help="path to model.keras (next to model_config.json)")
parser.add_argument("--source", required=True, help="image file or folder of images")
parser.add_argument("--quality-weights", default=None,
                    help='JSON map filename-stem -> weight, e.g. \'{"front":0.9,"back":0.8}\'')
parser.add_argument("--cpu", action="store_true", help="force CPU")
args = parser.parse_args()

if args.cpu:
    os.environ["CUDA_VISIBLE_DEVICES"] = "-1"

import keras  # noqa: E402
import tensorflow as tf  # noqa: E402

import common  # noqa: E402

IMG_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def predict_image(model, path: Path, image_size: int) -> np.ndarray:
    img = common._decode_image(tf.constant(str(path)), image_size)
    return np.asarray(model.predict(tf.expand_dims(img, 0), verbose=0))[0]


def main() -> None:
    model_path = Path(args.model)
    if not model_path.exists():
        raise SystemExit(f"model not found: {model_path} — download weights from GitHub Releases first")
    cfg_path = model_path.parent / "model_config.json"
    if not cfg_path.exists():
        raise SystemExit(f"model_config.json not found next to model at {model_path.parent}")
    with open(cfg_path) as f:
        cfg = json.load(f)

    classes = cfg["classes"]
    image_size = cfg["image_size"]
    model = keras.models.load_model(model_path)

    source = Path(args.source)
    images = sorted(source.iterdir()) if source.is_dir() else [source]
    images = [p for p in images if p.suffix.lower() in IMG_EXTS] if source.is_dir() else images
    if not images:
        raise SystemExit(f"no images found at {source}")

    weights = {}
    if args.quality_weights:
        weights = {k.lower(): float(v) for k, v in json.loads(args.quality_weights).items()}

    probs = np.zeros(len(classes))
    total_w = 0.0
    print("per-image:")
    for path in images:
        prob = predict_image(model, path, image_size)
        cls = classes[int(np.argmax(prob))]
        w = weights.get(path.stem.lower(), 1.0)
        probs += prob * w
        total_w += w
        out = {c: round(float(prob[i]), 4) for i, c in enumerate(classes)}
        print(f"  {path.name:<28} -> {cls:10s} {out}")
    probs = probs / max(total_w, 1e-9)

    agg = {"simulated": False,
           "condition": {"class": classes[int(np.argmax(probs))],
                         "probabilities": {c: round(float(probs[i]), 4) for i, c in enumerate(classes)}},
           "model_version": cfg["model_version"]}
    n_views = len(images)
    if n_views > 1:
        agg["aggregation"] = {"views": n_views,
                              "method": "quality-weighted mean" if weights else "mean",
                              "quality_weights": weights or None}
    print("\nresult:")
    print(json.dumps(agg, indent=2))


if __name__ == "__main__":
    main()
