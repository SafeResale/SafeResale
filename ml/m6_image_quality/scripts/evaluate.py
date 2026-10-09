"""Evaluate M6 image-quality checks against labeled images.

Two modes:

1) Accept/reject accuracy from a labels CSV (columns: image_path, actual_label):
     python ml/m6_image_quality/scripts/evaluate.py --labels <labels.csv>
   actual_label values: good (accept) | blurry / underexposed / overexposed / glare (reject).

2) Metrics CSV from a folder of labeled subfolders (sharp/good/blurry/underexposed/...):
     python ml/m6_image_quality/scripts/evaluate.py --data-dir <dir> [--output <file.csv>]
   Columns: LABEL, FILE, BLUR, LUMINANCE, GLARE (same as the old generate_metrics.py).
"""
import argparse
import csv
from pathlib import Path

from ml.m6_image_quality.scripts.common import (
    calculate_accept_reject_metrics,
    calculate_blur_score,
    calculate_glare_ratio,
    calculate_luminance,
)

LABEL_TO_ACTUAL = {
    "sharp": "good",
    "good": "good",
    "blurry": "blurry",
    "underexposed": "underexposed",
    "overexposed": "overexposed",
    "glare": "glare",
}


def print_metrics(result: dict) -> None:
    print(f"total_images: {result['total_images']}")
    print(f"correct_predictions: {result['correct_predictions']}")
    print(f"incorrect_predictions: {result['incorrect_predictions']}")
    print(f"accept_reject_accuracy: {result['accept_reject_accuracy']}%")


def write_metrics_csv(root: Path, output: Path) -> None:
    rows = []

    for folder_name, label in LABEL_TO_ACTUAL.items():
        folder = root / folder_name

        if not folder.exists():
            continue

        for image_path in sorted(folder.iterdir()):
            if (
                image_path.is_file()
                and image_path.suffix.lower() in {".jpg", ".jpeg", ".png"}
            ):
                rows.append({
                    "LABEL": label,
                    "FILE": str(image_path.relative_to(root)).replace("\\", "/"),
                    "BLUR": round(calculate_blur_score(str(image_path)), 2),
                    "LUMINANCE": round(calculate_luminance(str(image_path)), 2),
                    "GLARE": round(calculate_glare_ratio(str(image_path)), 4),
                })

    with output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(
            file,
            fieldnames=["LABEL", "FILE", "BLUR", "LUMINANCE", "GLARE"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"Saved {len(rows)} rows")
    print(f"Output: {output}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate M6 image quality")
    parser.add_argument("--labels", help="Labels CSV (image_path,actual_label)")
    parser.add_argument("--data-dir", help="Folder of labeled subfolders")
    parser.add_argument("--output", help="Output metrics CSV path (only with --data-dir)")
    args = parser.parse_args()

    if not (args.labels or args.data_dir):
        parser.error("provide one of --labels or --data-dir")

    if args.labels:
        result = calculate_accept_reject_metrics(args.labels)
        print_metrics(result)
        return

    root = Path(args.data_dir)
    if not root.exists():
        parser.error(f"data dir not found: {root}")

    output = Path(args.output) if args.output else root.parent / f"{root.name}_metrics.csv"
    write_metrics_csv(root, output)


if __name__ == "__main__":
    main()