from pathlib import Path
from ml.m6_image_quality.scripts.common import analyze_image_quality


DATASET_PATHS = [
    Path("ml/m6_image_quality/data/datasets/mobile_source/Image_phones"),
    Path("ml/m6_image_quality/data/datasets/mobile_source/Image_brokenphones"),
]

image_extensions = {".jpg", ".jpeg", ".png", ".webp"}


def main():
    total_images = 0
    passed_images = 0
    failed_images = 0

    issue_counts = {
        "blurry": 0,
        "underexposed": 0,
        "overexposed": 0,
        "glare": 0,
    }

    for dataset_path in DATASET_PATHS:
        print(f"\n===== {dataset_path.name} =====")

        image_files = [
            path
            for path in sorted(dataset_path.iterdir())
            if path.is_file() and path.suffix.lower() in image_extensions
        ]

        for image_path in image_files:
            result = analyze_image_quality(str(image_path))

            total_images += 1

            if result["passed"]:
                passed_images += 1
            else:
                failed_images += 1

            for issue in result["issues"]:
                issue_counts[issue] = issue_counts.get(issue, 0) + 1

            print(
                f"{image_path.name} | "
                f"score={result['quality_score']} | "
                f"passed={result['passed']} | "
                f"issues={result['issues']}"
            )

    print("\n" + "=" * 50)
    print("M6 REAL MOBILE DATASET SUMMARY")
    print("=" * 50)

    print(f"Total images : {total_images}")
    print(f"Passed       : {passed_images}")
    print(f"Failed       : {failed_images}")

    print("\nIssue counts:")
    for issue, count in issue_counts.items():
        print(f"{issue:15}: {count}")


if __name__ == "__main__":
    main()