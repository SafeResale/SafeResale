"""Smoke test: end-to-end M6 8-angle pipeline on the tracked manual_test photos.

Uses only the manual_test/*.jpeg images shipped in the repo — no extra data needed.
Verifies the full code path (per-image checks + duplicate/hash detection + aggregate).

Usage:
  python ml/m6_image_quality/scripts/smoke_test.py
"""
from pathlib import Path

from ml.m6_image_quality.scripts.common import (
    analyze_image_quality,
    analyze_eight_angle_images,
)

HERE = Path(__file__).resolve().parent.parent
MANUAL_TEST_DIR = HERE / "manual_test"

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def main() -> None:
    image_paths = sorted(
        path
        for path in MANUAL_TEST_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    )

    if len(image_paths) != 8:
        raise SystemExit(
            f"Expected 8 manual_test images, found {len(image_paths)} in {MANUAL_TEST_DIR}"
        )

    print("===== M6 SMOKE TEST (8-angle manual_test photos) =====")

    for image_path in image_paths:
        result = analyze_image_quality(str(image_path))
        print(
            f"{image_path.name:<14} passed={result['passed']} "
            f"score={result['quality_score']:>3.0f} issues={result['issues']}"
        )

    aggregate = analyze_eight_angle_images([str(path) for path in image_paths])

    print("----- 8-angle aggregate -----")
    print(f"overall_quality_score: {aggregate['overall_quality_score']}")
    print(f"passed_images: {aggregate['passed_images']}/{aggregate['total_images']}")
    print(f"duplicate_or_similar_pairs: {len(aggregate['duplicate_or_similar_pairs'])}")

    print("SMOKE OK")


if __name__ == "__main__":
    main()