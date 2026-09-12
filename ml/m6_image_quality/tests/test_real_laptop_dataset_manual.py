from pathlib import Path

from ml.m6_image_quality.scripts.common import analyze_image_quality


DATASET_DIR = Path(
    "ml/m6_image_quality/data/datasets/laptop_source/data/images"
)

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


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

    class_counts = {}

    for class_dir in sorted(DATASET_DIR.iterdir()):
        if not class_dir.is_dir():
            continue

        images = [
            image_path
            for image_path in class_dir.iterdir()
            if image_path.is_file()
            and image_path.suffix.lower() in IMAGE_EXTENSIONS
        ]

        class_total = 0

        print(f"\n===== {class_dir.name} =====")

        for image_path in sorted(images):
            result = analyze_image_quality(str(image_path))

            total_images += 1
            class_total += 1

            if result["passed"]:
                passed_images += 1
            else:
                failed_images += 1

            for issue in result["issues"]:
                if issue in issue_counts:
                    issue_counts[issue] += 1

        class_counts[class_dir.name] = class_total

        print(f"Images tested: {class_total}")

    print("\n" + "=" * 50)
    print("M6 REAL LAPTOP DATASET SUMMARY")
    print("=" * 50)

    print(f"Total images : {total_images}")
    print(f"Passed       : {passed_images}")
    print(f"Failed       : {failed_images}")

    print("\nIssue counts:")

    for issue, count in issue_counts.items():
        print(f"{issue:<15}: {count}")

    print("\nClass counts:")

    for class_name, count in class_counts.items():
        print(f"{class_name:<15}: {count}")


if __name__ == "__main__":
    main()