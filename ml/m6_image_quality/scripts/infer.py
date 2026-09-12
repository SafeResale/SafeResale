"""Run M6 image-quality checks on one image or a set of 8 product images.

Usage (from repo root):
  python ml/m6_image_quality/scripts/infer.py --image <path>
  python ml/m6_image_quality/scripts/infer.py --images <p1> <p2> ... <p8>
"""
import argparse
import sys

from ml.m6_image_quality.scripts.common import (
    analyze_image_quality,
    analyze_eight_angle_images,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="M6 image-quality analysis")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--image", help="Path to a single image")
    group.add_argument(
        "--images", nargs="+",
        help="Exactly 8 image paths (the required SafeResale angles)",
    )
    args = parser.parse_args()

    if args.image:
        result = analyze_image_quality(args.image)

        print(f"image: {args.image}")
        print(f"passed: {result['passed']}")
        print(f"quality_score: {result['quality_score']}")
        print(f"issues: {result['issues']}")
        print(f"details: {result['details']}")
        return

    if len(args.images) != 8:
        sys.exit("--images requires exactly 8 image paths (one per required angle)")

    result = analyze_eight_angle_images(args.images)

    print(f"overall_quality_score: {result['overall_quality_score']}")
    print(f"passed: {result['passed']}")
    print(f"passed_images: {result['passed_images']}/{result['total_images']}")
    print(f"failed_images: {result['failed_images']}")
    print(f"duplicate_or_similar_pairs: {len(result['duplicate_or_similar_pairs'])}")

    for image_result in result["image_results"]:
        print(
            f"  {image_result['image_path']} | score={image_result['quality_score']} "
            f"| passed={image_result['passed']} | issues={image_result['issues']}"
        )


if __name__ == "__main__":
    main()