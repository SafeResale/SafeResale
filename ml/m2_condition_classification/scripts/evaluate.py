"""Evaluate a trained M2 model on the frozen test split + measure latency.

Reports the metrics required by docs/01-prd.md §10.3: accuracy, macro
precision/recall/F1, per-class scores, confusion matrix (PNG + CSV) and
per-image latency mean/p50/p95 (GPU or CPU). Only measured numbers are written
to test_metrics.json (academic-integrity rule).

Usage:
  ~/ml312/bin/python scripts/evaluate.py --model runs/baseline-m3small/model.keras [--cpu]
"""
import argparse
import json
import os
import time
from pathlib import Path

import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("--model", required=True, help="path to model.keras (next to model_config.json)")
parser.add_argument("--cpu", action="store_true", help="force CPU for the latency measurement")
args = parser.parse_args()

if args.cpu:
    os.environ["CUDA_VISIBLE_DEVICES"] = "-1"

import keras  # noqa: E402
import tensorflow as tf  # noqa: E402

import common  # noqa: E402


def measure_latency(model, items, image_size, device: str) -> dict:
    dev = "/CPU:0" if device == "cpu" else "/GPU:0"
    times = []
    for p, _ in items:
        img = common._decode_image(tf.constant(str(common.resolve(p))), image_size)
        x = tf.expand_dims(img, 0)
        t0 = time.perf_counter()
        with tf.device(dev):
            model.predict(x, verbose=0)
        times.append((time.perf_counter() - t0) * 1000.0)
    arr = np.asarray(times)
    return {"device": device, "n": int(arr.size),
            "mean_ms": float(arr.mean()),
            "p50_ms": float(np.percentile(arr, 50)),
            "p95_ms": float(np.percentile(arr, 95))}


def main() -> None:
    model_path = Path(args.model)
    if not model_path.exists():
        raise SystemExit(f"model not found: {model_path}")
    model_dir = model_path.parent
    cfg_path = model_dir / "model_config.json"
    if not cfg_path.exists():
        raise SystemExit(f"model_config.json not found next to model at {model_dir}")
    with open(cfg_path) as f:
        cfg = json.load(f)

    manifest = common.load_manifest(cfg["dataset"])
    classes = cfg["classes"]
    image_size = cfg["image_size"]

    model = keras.models.load_model(model_path)
    test_ds = common.make_dataset(manifest["test"], classes, image_size, batch_size=1, augment=False)

    y_true, y_pred, cm = common.predict_confusion(model, test_ds, len(classes))
    metrics = common.metrics_from_cm(cm, classes)
    latency = measure_latency(model, manifest["test"], image_size, "cpu" if args.cpu else "gpu")
    metrics["latency_per_image_ms"] = latency

    out = model_dir / "test_metrics.json"
    with open(out, "w") as f:
        json.dump(metrics, f, indent=2)

    print(f"model: {cfg['model_version']}")
    print(f"test split: {len(manifest['test'])} images")
    print(f"accuracy={metrics['accuracy']:.4f} | macro P={metrics['precision_macro']:.4f} "
          f"R={metrics['recall_macro']:.4f} F1={metrics['f1_macro']:.4f}")
    print(f"latency (per image, {latency['device']}): mean={latency['mean_ms']:.2f}ms "
          f"p50={latency['p50_ms']:.2f}ms p95={latency['p95_ms']:.2f}ms")
    print("\nper-class:")
    for cls, m in metrics["per_class"].items():
        print(f"  {cls:12s} P={m['precision']:.4f} R={m['recall']:.4f} F1={m['f1']:.4f}")

    cm_np = np.asarray(cm)
    np.savetxt(model_dir / "test_confusion_matrix.csv", cm_np, delimiter=",", fmt="%d",
               header=",".join(classes), comments="")
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(figsize=(5.5, 5))
        im = ax.imshow(cm_np, interpolation="nearest", cmap="Blues")
        ax.set_xticks(range(len(classes)), labels=classes)
        ax.set_yticks(range(len(classes)), labels=classes)
        for i in range(cm_np.shape[0]):
            for j in range(cm_np.shape[1]):
                ax.text(j, i, str(int(cm_np[i, j])), ha="center", va="center")
        ax.set_xlabel("Predicted")
        ax.set_ylabel("True")
        ax.set_title(f"Condition — {cfg['backbone']}")
        fig.colorbar(im)
        fig.tight_layout()
        fig.savefig(model_dir / "test_confusion_matrix.png", dpi=150)
        print(f"\nsaved: {out}, test_confusion_matrix.png, test_confusion_matrix.csv")
    except ImportError:
        print(f"\nmatplotlib not available — skipped PNG; saved {out} and CSV")
    print("Only measured numbers were written (academic-integrity rule).")


if __name__ == "__main__":
    main()
