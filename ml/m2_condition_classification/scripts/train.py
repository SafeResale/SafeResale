"""Train M2 condition classifier: baseline vs improved backbone, same splits/aug/seed.

docs/08-ml-plan.md §5 — MobileNetV3 (lightweight) baseline, EfficientNetV2-S (stronger)
improved. Two-stage schedule: train the head on a frozen base, then fine-tune the whole
network at a lower LR. Class imbalance handled via inverse-frequency class weights
(no label fabrication).

Outputs per model tag (under $M2_WORK_ROOT/runs/<tag>/):
  model.keras          best checkpoint by val_accuracy
  model_config.json    classes / image_size / backbone / model_version (used by evaluate + infer)
  metrics.json         validation accuracy + macro P/R/F1 + confusion matrix
  history.csv          per-epoch metrics (CSVLogger)

Usage (WSL ~/ml312 venv):
  ~/ml312/bin/python scripts/train.py [--yaml configs/m2.yaml] [--only <tag>] [--epochs N] [--cpu]
"""
import argparse
import json
import os

import yaml

HERE = __import__("pathlib").Path(__file__).resolve().parent.parent

parser = argparse.ArgumentParser()
parser.add_argument("--yaml", default="configs/m2.yaml", help="config relative to m2_condition_classification/")
parser.add_argument("--only", default=None, help="train a single model tag from the config")
parser.add_argument("--epochs", type=int, default=None, help="override config epochs")
parser.add_argument("--cpu", action="store_true", help="force CPU (CUDA_VISIBLE_DEVICES=-1)")
parser.add_argument("--imagenet", dest="imagenet", action="store_true", default=True)
parser.add_argument("--no-imagenet", dest="imagenet", action="store_false", help="random init (debug only)")
args = parser.parse_args()

if args.cpu:
    os.environ["CUDA_VISIBLE_DEVICES"] = "-1"

import keras  # noqa: E402
import tensorflow as tf  # noqa: E402

import common  # noqa: E402


def train_one(backbone: str, tag: str, cfg: dict, manifest: dict) -> None:
    classes = manifest["classes"]
    image_size = cfg["image_size"]
    batch_size = cfg["batch_size"]
    epochs = args.epochs or cfg["epochs"]
    freeze_epochs = min(cfg.get("freeze_epochs", 2), epochs)
    version = cfg.get("version", "v0.1")
    model_version = f"m2-condition-{backbone}-{version}"

    cw = common.class_weights(manifest["train"], classes)
    train_ds = common.make_dataset(manifest["train"], classes, image_size, batch_size,
                                   augment=True, shuffle=True, seed=cfg.get("seed", 42), weights=cw)
    val_ds = common.make_dataset(manifest["val"], classes, image_size, batch_size,
                                 augment=False, shuffle=False)

    model, base = common.build_model(backbone, image_size, len(classes),
                                     imagenet=args.imagenet, trainable_base=False)

    run_dir = common.WORK_ROOT / "runs" / tag
    run_dir.mkdir(parents=True, exist_ok=True)
    ckpt = str(run_dir / "model.keras")
    callbacks = [
        keras.callbacks.ModelCheckpoint(ckpt, monitor="val_accuracy", save_best_only=True),
        keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=cfg.get("patience", 10),
                                      restore_best_weights=True),
        keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=4),
        keras.callbacks.CSVLogger(str(run_dir / "history.csv")),
    ]

    print(f"\n=== {tag} | {common.BACKBONE_ALIASES.get(backbone, backbone)} | {model_version} ===")
    print(f"train={len(manifest['train'])} val={len(manifest['val'])} "
          f"test={len(manifest['test'])} | classes={classes}")
    print(f"devices: {[d.name for d in tf.config.list_physical_devices()]}")

    if not args.imagenet:
        print("NOTE: training from random init (--no-imagenet) — debug only")

    model.compile(optimizer=keras.optimizers.Adam(cfg.get("learning_rate", 1e-3)),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    # Phase 1: head only (base frozen)
    if freeze_epochs > 0:
        model.fit(train_ds, validation_data=val_ds, epochs=freeze_epochs,
                  callbacks=callbacks, verbose=1)

    # Phase 2: fine-tune the whole network at 1/10 LR
    if epochs > freeze_epochs:
        base.trainable = True
        model.compile(optimizer=keras.optimizers.Adam(cfg.get("finetune_lr", 1e-4)),
                      loss="sparse_categorical_crossentropy", metrics=["accuracy"])
        model.fit(train_ds, validation_data=val_ds, initial_epoch=freeze_epochs,
                  epochs=epochs, callbacks=callbacks, verbose=1)

    val_loss, val_acc = model.evaluate(val_ds, verbose=0)
    y_true, y_pred, cm = common.predict_confusion(model, val_ds, len(classes))
    metrics = common.metrics_from_cm(cm, classes)
    metrics.update({"val_loss": float(val_loss), "val_accuracy": float(val_acc)})

    with open(run_dir / "model_config.json", "w") as f:
        json.dump({"model_version": model_version, "backbone": backbone,
                   "image_size": image_size, "classes": classes,
                   "dataset": manifest["dataset"]}, f, indent=2)
    with open(run_dir / "metrics.json", "w") as f:
        json.dump(metrics, f, indent=2)

    print(f"[{tag}] val_accuracy={metrics['val_accuracy']:.4f} "
          f"macro P={metrics['precision_macro']:.4f} R={metrics['recall_macro']:.4f} "
          f"F1={metrics['f1_macro']:.4f}")
    print(f"saved: {run_dir / 'model.keras'}")


def main() -> None:
    cfg_path = HERE / args.yaml
    if not cfg_path.exists():
        raise SystemExit(f"config not found: {cfg_path}")
    with open(cfg_path) as f:
        cfg = yaml.safe_load(f)

    manifest = common.load_manifest(cfg["dataset"])
    if manifest["classes"] != cfg.get("classes"):
        print(f"WARNING: config classes {cfg.get('classes')} != manifest classes {manifest['classes']} — "
              f"using manifest")

    models = cfg["models"]
    if args.only:
        models = [m for m in models if m["tag"] == args.only]
        if not models:
            raise SystemExit(f"no model with tag '{args.only}' in {cfg_path}")
    for m in models:
        train_one(m["backbone"], m["tag"], cfg, manifest)

    print("\nNext: scripts/evaluate.py --model runs/<tag>/model.keras to score the frozen test split.")


if __name__ == "__main__":
    main()
