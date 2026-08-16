"""Smoke test: 1 epoch on a tiny synthetic 3-class set to verify the TF/GPU pipeline.

Runs entirely under ~/safresale-ml/m2 (clean path + fast I/O), not /mnt/c.
No dataset or pretrained weights needed (random-init backbone).

Usage (WSL ~/ml312 venv):
  ~/ml312/bin/python scripts/smoke_test.py
"""
import json
import os
from pathlib import Path

import numpy as np

import common

N_PER_CLASS = 12
IMAGE_SIZE = 64
BATCH_SIZE = 8


def write_synthetic(smoke_dir: Path, classes: list[str]) -> list[tuple[Path, str]]:
    rng = np.random.default_rng(1234)
    samples = []
    for cls in classes:
        cls_dir = smoke_dir / cls
        cls_dir.mkdir(parents=True, exist_ok=True)
        for i in range(N_PER_CLASS):
            arr = (rng.uniform(0, 1, (IMAGE_SIZE, IMAGE_SIZE, 3)) * 255).astype(np.uint8)
            path = cls_dir / f"{cls}_{i:02d}.jpg"
            import tensorflow as tf
            tf.io.write_file(str(path), tf.image.encode_jpeg(tf.convert_to_tensor(arr)))
            samples.append((path, cls))
    return samples


def main() -> None:
    import tensorflow as tf
    import keras

    common.WORK_ROOT.mkdir(parents=True, exist_ok=True)
    smoke_dir = common.WORK_ROOT / "data" / "smoke"
    classes = common.DEFAULT_CLASSES

    samples = write_synthetic(smoke_dir, classes)
    train, val, test = common.split_by_class(samples, classes, seed=42)

    manifest = {
        "dataset": "smoke",
        "source": str(smoke_dir),
        "classes": classes,
        "train": [[p.relative_to(common.WORK_ROOT).as_posix(), lbl] for p, lbl in train],
        "val": [[p.relative_to(common.WORK_ROOT).as_posix(), lbl] for p, lbl in val],
        "test": [[p.relative_to(common.WORK_ROOT).as_posix(), lbl] for p, lbl in test],
    }
    manifest_path = common.WORK_ROOT / "data" / "condition" / "smoke" / "splits.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    train_ds = common.make_dataset(manifest["train"], classes, IMAGE_SIZE, BATCH_SIZE,
                                   augment=True, shuffle=True, seed=42)
    val_ds = common.make_dataset(manifest["val"], classes, IMAGE_SIZE, BATCH_SIZE, augment=False)

    model, _ = common.build_model("mobilenetv3small", IMAGE_SIZE, len(classes), imagenet=False)
    model.compile(optimizer=keras.optimizers.Adam(1e-3), loss="sparse_categorical_crossentropy",
                  metrics=["accuracy"])
    print("devices:", [d.name for d in tf.config.list_physical_devices()])
    model.fit(train_ds, validation_data=val_ds, epochs=1, verbose=1)

    val_loss, val_acc = model.evaluate(val_ds, verbose=0)
    print(f"SMOKE OK | val_loss={val_loss:.4f} val_acc={val_acc:.4f}")
    print("(synthetic data only — not a real metric, per academic-integrity rule)")


if __name__ == "__main__":
    main()
