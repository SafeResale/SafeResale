"""Shared helpers for M2 condition-classification scripts.

Kept dependency-light (tensorflow + keras only — no sklearn). Reused by
download_dataset, prepare_dataset, train, evaluate, infer and smoke_test.
TensorFlow is imported lazily so data-prep scripts run even without TF.

Work root: $M2_WORK_ROOT or ~/safresale-ml/m2 (WSL home, fast I/O — never /mnt/c).
Data and runs live there; the repo keeps only scripts + configs (see .gitignore).
"""
from __future__ import annotations

import json
import os
from pathlib import Path

import numpy as np

try:
    import tensorflow as tf
except ImportError:  # pragma: no cover - only matters for data-prep scripts
    tf = None

HERE = Path(__file__).resolve().parent.parent
WORK_ROOT = Path(os.environ.get("M2_WORK_ROOT", Path.home() / "safresale-ml" / "m2"))

DEFAULT_CLASSES = ["good", "moderate", "defective"]


def _require_tf():
    if tf is None:
        raise SystemExit("tensorflow not importable — run this in the ~/ml312 venv (docs/10-setup-guide.md §7)")


def resolve(path: str | Path) -> Path:
    """Absolute path from a manifest-relative path (relative to WORK_ROOT)."""
    p = Path(path)
    return p if p.is_absolute() else WORK_ROOT / p


def load_manifest(name: str) -> dict:
    path = WORK_ROOT / "data" / "condition" / name / "splits.json"
    if not path.exists():
        raise SystemExit(f"manifest not found: {path} — run scripts/prepare_dataset.py first")
    with open(path) as f:
        return json.load(f)


def split_by_class(samples: list[tuple[Path, str]], classes: list[str],
                   ratios: tuple[float, float, float] = (0.7, 0.15, 0.15),
                   seed: int = 42):
    """Stratified 70/15/15 split. `samples` = [(path, label), ...]."""
    import random

    rng = random.Random(seed)
    train, val, test = [], [], []
    for cls in classes:
        group = [s for s in samples if s[1] == cls]
        rng.shuffle(group)
        n = len(group)
        n_tr = int(n * ratios[0])
        n_va = int(n * ratios[1])
        train += group[:n_tr]
        val += group[n_tr:n_tr + n_va]
        test += group[n_tr + n_va:]
    return train, val, test


def _decode_image(path: tf.Tensor, image_size: int) -> tf.Tensor:
    raw = tf.io.read_file(path)
    img = tf.image.decode_image(raw, channels=3, expand_animations=False)
    img = tf.image.resize(img, (image_size, image_size))
    return tf.cast(img, tf.float32) / 255.0


def gaussian_blur(image: tf.Tensor, kernel: int = 5, sigma: float = 1.0) -> tf.Tensor:
    """Depthwise Gaussian blur on a [H, W, C] float image in [0, 1]."""
    ax = tf.range(kernel, dtype=tf.float32) - kernel // 2
    g = tf.exp(-tf.square(ax) / (2.0 * sigma * sigma))
    g = g / tf.reduce_sum(g)
    k = tf.tensordot(g, g, 0)[:, :, tf.newaxis, tf.newaxis]  # [k, k, 1, 1]
    k = tf.tile(k, [1, 1, 3, 1])
    out = tf.nn.depthwise_conv2d(image[tf.newaxis, ...], k, strides=[1, 1, 1, 1], padding="SAME")
    return out[0]


def jpeg_noise(image: tf.Tensor, min_q: int = 55, max_q: int = 95) -> tf.Tensor:
    """Random-JPEG-quality compression noise (augmentation, no label change)."""
    q = tf.random.uniform((), minval=min_q, maxval=max_q, dtype=tf.int32)
    u8 = tf.cast(tf.clip_by_value(image, 0.0, 1.0) * 255.0, tf.uint8)
    return tf.cast(tf.image.adjust_jpeg_quality(u8, q), tf.float32) / 255.0


def _augment(image: tf.Tensor) -> tf.Tensor:
    """docs/08-ml-plan.md §3.2: flip, rotate, brightness/contrast, blur, JPEG noise."""
    if tf.random.uniform(()) < 0.5:
        image = tf.image.random_flip_left_right(image)
    k = tf.random.uniform((), maxval=4, dtype=tf.int32)
    image = tf.image.rot90(image, k)
    image = tf.image.random_brightness(image, 0.15)
    image = tf.image.random_contrast(image, 0.8, 1.25)
    if tf.random.uniform(()) < 0.5:
        image = gaussian_blur(image)
    if tf.random.uniform(()) < 0.5:
        image = jpeg_noise(image)
    return image


def make_dataset(items: list[list[str]], classes: list[str], image_size: int,
                 batch_size: int, augment: bool = False, shuffle: bool = False,
                 seed: int = 42, weights: dict[str, float] | None = None) -> tf.data.Dataset:
    """Labeled tf.data pipeline. `weights` = per-class sample weights (class balancing).

    When weights are given the dataset yields (x, y, sample_weight) triples, which
    Keras applies directly — avoids the unreliable fit(class_weight=...) path for
    tf.data inputs.
    """
    _require_tf()
    paths = [str(resolve(p)) for p, _ in items]
    labels = [classes.index(lbl) for _, lbl in items]
    if weights is not None:
        sample_w = [weights[label] for _, label in items]
        ds = tf.data.Dataset.from_tensor_slices((paths, labels, sample_w))
    else:
        ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if shuffle:
        ds = ds.shuffle(len(items), seed=seed)

    def _map(path: tf.Tensor, label: tf.Tensor, weight: tf.Tensor | None = None):
        img = _decode_image(path, image_size)
        if augment:
            img = _augment(img)
        if weight is not None:
            return img, label, weight
        return img, label

    ds = ds.map(_map, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)


BACKBONE_ALIASES = {
    "mobilenetv3small": "MobileNetV3-Small",
    "mobilenetv3large": "MobileNetV3-Large",
    "efficientnetv2s": "EfficientNetV2-S",
}


def build_model(backbone: str, image_size: int, num_classes: int,
                imagenet: bool = True, trainable_base: bool = False):
    """Transfer-learning classifier (Keras 3). Returns (model, base).

    Each family's baked-in preprocessing layer (include_preprocessing=True, the
    default) rescales [0, 1] inputs to its own expected range, so train and
    inference share the exact same graph — no external preprocessing to drift.
    """
    import keras

    input_shape = (image_size, image_size, 3)
    weights = "imagenet" if imagenet else None
    if backbone == "mobilenetv3small":
        base = keras.applications.MobileNetV3Small(
            weights=weights, include_top=False, input_shape=input_shape, pooling="avg")
    elif backbone == "mobilenetv3large":
        base = keras.applications.MobileNetV3Large(
            weights=weights, include_top=False, input_shape=input_shape, pooling="avg")
    elif backbone == "efficientnetv2s":
        base = keras.applications.EfficientNetV2S(
            weights=weights, include_top=False, input_shape=input_shape, pooling="avg")
    else:
        raise SystemExit(f"unknown backbone: {backbone} (choose from {sorted(BACKBONE_ALIASES)})")

    base.trainable = trainable_base
    inputs = keras.Input(shape=input_shape)
    x = base(inputs)
    x = keras.layers.Dropout(0.25)(x)
    outputs = keras.layers.Dense(num_classes, activation="softmax")(x)
    return keras.Model(inputs, outputs), base


def class_weights(items: list[list[str]], classes: list[str]) -> dict[str, float]:
    """Inverse-frequency class weights so no label dominates (balance without fabrication)."""
    counts = {c: 0 for c in classes}
    for _, lbl in items:
        counts[lbl] += 1
    total = sum(counts.values()) or 1
    n = len(classes)
    return {c: total / (n * counts[c]) for c in classes if counts[c] > 0}


def predict_confusion(model, ds: tf.data.Dataset, num_classes: int):
    """Returns (y_true, y_pred, confusion_matrix) on a labeled dataset."""
    _require_tf()
    preds = np.asarray(model.predict(ds, verbose=0)).argmax(axis=-1)
    labels = np.concatenate([y.numpy() for _, y in ds])
    cm = tf.math.confusion_matrix(labels, preds, num_classes=num_classes).numpy()
    return labels, preds, cm


def metrics_from_cm(cm: np.ndarray, classes: list[str]) -> dict:
    """accuracy + macro precision/recall/F1 + per-class (docs/01-prd.md §10.3)."""
    n = cm.shape[0]
    tp = np.diag(cm)
    fp = cm.sum(axis=0) - tp
    fn = cm.sum(axis=1) - tp
    prec = tp / np.maximum(tp + fp, 1)
    rec = tp / np.maximum(tp + fn, 1)
    f1 = 2 * prec * rec / np.maximum(prec + rec, 1e-9)
    per_class = {cls: {
        "precision": float(prec[i]),
        "recall": float(rec[i]),
        "f1": float(f1[i]),
    } for i, cls in enumerate(classes)}
    return {
        "accuracy": float(tp.sum() / max(cm.sum(), 1)),
        "precision_macro": float(prec.mean()),
        "recall_macro": float(rec.mean()),
        "f1_macro": float(f1.mean()),
        "per_class": per_class,
        "confusion_matrix": cm.tolist(),
    }
