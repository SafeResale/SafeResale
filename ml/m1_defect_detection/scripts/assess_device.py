"""Assess a single device from a folder of photos (e.g. the 8 required angles).

Runs the trained M1 defect model on every image in the folder, verifies each image
decodes cleanly, aggregates detections into a condition score (100 = flawless,
higher is better) plus a grade band and a confirmation status.

Multi-image consistency (spec section 7-8):
  * Reflection-prone classes ("screen_damage", "glass_damage") need corroboration:
    either the same class appears in >= 2 views, or a single view fires with
    confidence >= --confirm-single-conf (default 0.90).
  * Unconfirmed single-view claims get a reduced score weight (--unconfirmed-weight)
    and put the device in UNCERTAIN_RETAKE status instead of a hard verdict.
  * Blurry photos also trigger a retake suggestion.

Grading (spec section 9): 100 Excellent | 85-99 Very Good | 70-84 Good |
50-69 Fair | 0-49 Poor. Severity weights are configurable via --severity-json.

Usage (in WSL):
  ~/safresale-ml/.venv/bin/python scripts/assess_device.py \
    --weights ~/safresale-ml/m1/runs/improved-yolo11n/weights/best.pt \
    --source ~/safresale-ml/m1/data/samples/<device_folder> [--json out.json]
"""
import argparse
import json
import os
import sys
from pathlib import Path

import cv2

from ultralytics import YOLO

WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))

# Severity weight per core class (0..1) used to turn detections into deductions.
# Higher = worse defect. Anything not listed falls back to DEFAULT_SEVERITY.
SEVERITY = {
    "scratch": 0.30,
    "crack": 0.70,
    "dent": 0.60,
    "screen_damage": 0.85,
    "glass_damage": 0.80,
    "camera_damage": 0.90,
    "port_damage": 0.55,
    "casing_damage": 0.50,
    "body_deformation": 0.75,
    "paint_damage": 0.35,
    "chip": 0.45,
    "rust": 0.60,
    "corrosion": 0.65,
    "water_damage": 0.95,
}
DEFAULT_SEVERITY = 0.5

# Classes prone to reflection/glare false positives: they require corroboration.
HIGH_RISK_CLASSES = {"screen_damage", "glass_damage"}

# Device-level aggregation weights (same spirit as before: worst view dominates).
MAX_WEIGHT = 0.6
MEAN_WEIGHT = 0.4


def grade_for(score: float) -> str:
    """Marketplace-style grade band; higher score = better condition."""
    if score >= 99.5:
        return "Excellent"
    if score >= 85:
        return "Very Good"
    if score >= 70:
        return "Good"
    if score >= 50:
        return "Fair"
    return "Poor"


def is_blurry(path: Path, threshold: float) -> bool:
    img = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
    if img is None:
        return False
    return cv2.Laplacian(img, cv2.CV_64F).var() < threshold


def load_severity_overrides(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--weights", required=True, help="Trained weights (best.pt)")
    parser.add_argument("--source", required=True, help="Folder of device photos")
    parser.add_argument("--conf", type=float, default=0.25)
    parser.add_argument("--json", help="Also write the report to this path")
    parser.add_argument("--blur-threshold", type=float, default=10.0,
                        help="Laplacian variance below this marks a photo blurry")
    parser.add_argument("--confirm-single-conf", type=float, default=0.90,
                        help="Single-view high-risk claim at/above this conf is confirmed")
    parser.add_argument("--unconfirmed-weight", type=float, default=0.3,
                        help="Score weight multiplier for unconfirmed claims")
    parser.add_argument("--severity-json", help="Optional JSON overriding SEVERITY weights")
    args = parser.parse_args()

    severity = dict(SEVERITY)
    if args.severity_json:
        severity.update(load_severity_overrides(args.severity_json))

    if not Path(args.weights).exists():
        sys.exit(f"weights not found: {args.weights}")
    src = Path(args.source)
    if not src.is_dir():
        sys.exit(f"source folder not found: {src}")

    model = YOLO(args.weights)
    class_names = model.names

    images = sorted(p for p in src.iterdir()
                    if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
    if not images:
        sys.exit(f"no .jpg/.jpeg/.png images in {src}")

    # ---- pass 1: detect + verify each image -------------------------------
    results = []
    for p in images:
        dets = []
        r = model.predict(source=str(p), conf=args.conf, verbose=False)[0]
        if r.boxes is not None:
            for box in r.boxes:
                cls = int(box.cls[0])
                name = class_names.get(cls, str(cls))
                conf = float(box.conf[0])
                dets.append({"class": name, "conf": conf})
        blurry = bool(is_blurry(p, args.blur_threshold))
        results.append({"image": p.name, "blurry": blurry, "detections": dets})

    # ---- pass 2: corroboration across views -------------------------------
    high_risk_views = {}  # class -> number of distinct views claiming it
    for r in results:
        for d in r["detections"]:
            if d["class"] in HIGH_RISK_CLASSES:
                high_risk_views[d["class"]] = high_risk_views.get(d["class"], 0) + 1

    for r in results:
        for d in r["detections"]:
            if d["class"] in HIGH_RISK_CLASSES:
                corroborated = high_risk_views[d["class"]] >= 2
                d["confirmed"] = corroborated or d["conf"] >= args.confirm_single_conf
            else:
                d["confirmed"] = True
            sev = severity.get(d["class"], DEFAULT_SEVERITY)
            weight = 1.0 if d["confirmed"] else args.unconfirmed_weight
            d["deduction"] = round(sev * d["conf"] * 100.0 * weight, 2)

    # ---- pass 3: score + aggregate ----------------------------------------
    for r in results:
        r["score"] = round(min(100.0, sum(d["deduction"] for d in r["detections"])), 1)

    n = len(results)
    damaged_views = sum(1 for r in results if r["detections"])
    blurry_count = sum(1 for r in results if r["blurry"])
    scores = [r["score"] for r in results]
    device_damage = MAX_WEIGHT * max(scores) + MEAN_WEIGHT * (sum(scores) / n)
    final_score = round(max(0.0, 100.0 - device_damage), 1)

    unconfirmed_claims = [
        (r, d) for r in results for d in r["detections"] if not d["confirmed"]
    ]
    confirmed_claims = [
        (r, d) for r in results for d in r["detections"] if d["confirmed"]
    ]

    if confirmed_claims:
        status = "CONFIRMED"
    elif unconfirmed_claims or blurry_count:
        status = "UNCERTAIN_RETAKE"
    else:
        status = "CONFIRMED"

    retake = sorted({r["image"] for r, _ in unconfirmed_claims} |
                    {r["image"] for r in results if r["blurry"]})

    # ---- human-readable reason --------------------------------------------
    def claim_str(r, d):
        flag = "" if d["confirmed"] else " [unconfirmed]"
        return f"{d['class']} on '{r['image']}' (conf {d['conf']:.2f}){flag}"

    parts = [claim_str(r, d) for r, d in
             sorted(confirmed_claims + unconfirmed_claims,
                    key=lambda rd: rd[1]["deduction"], reverse=True)]
    reason = "; ".join(parts) if parts else "no defects detected"
    if status == "UNCERTAIN_RETAKE":
        reason += f" | retake: {', '.join(retake)}"

    # ---- print -------------------------------------------------------------
    print(f"device folder : {src}")
    print(f"images        : {n}")
    print(f"damaged views : {damaged_views}/{n}")
    print(f"blurry/refused: {blurry_count}")
    print(f"condition     : {final_score}/100  ->  {grade_for(final_score)}"
          f"  [{status}]")
    print(f"reason        : {reason}")
    print("-" * 70)
    for r in sorted(results, key=lambda x: x["score"], reverse=True):
        tag = "BLURRY" if r["blurry"] else "ok"
        det = ", ".join(
            f"{d['class']}@{d['conf']:.2f}{'' if d['confirmed'] else '?'}"
            for d in r["detections"]) or "clean"
        print(f"  -{r['score']:5.1f}  [{tag}] {r['image']:<40} {det}")
    print("-" * 70)
    per_class = {}
    for r in results:
        for d in r["detections"]:
            key = d["class"] + ("" if d["confirmed"] else " (unconfirmed)")
            per_class[key] = per_class.get(key, 0) + 1
    print("defect totals:", ", ".join(f"{k} x{v}" for k, v in sorted(per_class.items()))
          or "none")

    # ---- json report --------------------------------------------------------
    if args.json:
        report = {
            "source": str(src),
            "image_count": n,
            "damaged_views": damaged_views,
            "blurry_count": blurry_count,
            "status": status,
            "retake_suggested": retake,
            "condition_score": final_score,
            "grade": grade_for(final_score),
            "reason": reason,
            "images": [
                {
                    "image": r["image"],
                    "verified": not r["blurry"],
                    "blurry": r["blurry"],
                    "deduction": r["score"],
                    "detections": [
                        {
                            "class": d["class"],
                            "conf": round(d["conf"], 3),
                            "confirmed": d["confirmed"],
                            "deduction": d["deduction"],
                        } for d in r["detections"]
                    ],
                } for r in results
            ],
        }
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)
        print(f"\nwrote: {args.json}")


if __name__ == "__main__":
    main()
