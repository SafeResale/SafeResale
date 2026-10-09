from pathlib import Path
import random

import cv2
import numpy as np


SOURCE_DIR = Path(
    "ml/m6_image_quality/data/datasets/laptop_source/data/images"
)

OUTPUT_DIR = Path(
    "ml/m6_image_quality/data/evaluation/laptop"
)

IMAGES_PER_LABEL = 10


def get_source_images():
    extensions = {".jpg", ".jpeg", ".png", ".webp"}

    images = [
        path
        for path in SOURCE_DIR.rglob("*")
        if path.is_file() and path.suffix.lower() in extensions
    ]

    random.seed(42)
    random.shuffle(images)

    return images


def save_image(image, output_path):
    output_path.parent.mkdir(parents=True, exist_ok=True)

    success = cv2.imwrite(str(output_path), image)

    if not success:
        raise ValueError(f"Could not save image: {output_path}")


def create_blurry(image):
    return cv2.GaussianBlur(image, (21, 21), 0)


def create_underexposed(image):
    return cv2.convertScaleAbs(image, alpha=0.5, beta=0)


def create_overexposed(image):
    return cv2.convertScaleAbs(image, alpha=1.5, beta=80)


def create_glare(image):
    result = image.copy()

    height, width = result.shape[:2]

    center = (
        random.randint(width // 4, 3 * width // 4),
        random.randint(height // 4, 3 * height // 4),
    )

    radius = min(height, width) // 6

    overlay = result.copy()

    cv2.circle(
        overlay,
        center,
        radius,
        (255, 255, 255),
        -1,
    )

    result = cv2.addWeighted(
        overlay,
        0.6,
        result,
        0.4,
        0,
    )

    return result


def main():
    source_images = get_source_images()

    required = IMAGES_PER_LABEL * 5

    if len(source_images) < required:
        raise ValueError(
            f"Need at least {required} source images, "
            f"but found only {len(source_images)}"
        )

    labels = [
        "sharp",
        "blurry",
        "underexposed",
        "overexposed",
        "glare",
    ]

    image_index = 0

    for label in labels:
        output_folder = OUTPUT_DIR / label

        for old_file in output_folder.glob("*"):
            if old_file.is_file():
                old_file.unlink()

        for i in range(IMAGES_PER_LABEL):
            source_path = source_images[image_index]
            image_index += 1

            image = cv2.imread(str(source_path))

            if image is None:
                print(f"Skipping unreadable image: {source_path}")
                continue

            if label == "sharp":
                processed_image = image

            elif label == "blurry":
                processed_image = create_blurry(image)

            elif label == "underexposed":
                processed_image = create_underexposed(image)

            elif label == "overexposed":
                processed_image = create_overexposed(image)

            elif label == "glare":
                processed_image = create_glare(image)

            output_path = output_folder / f"{label}_{i + 1}.jpg"

            save_image(processed_image, output_path)

    print("Evaluation dataset created successfully.")


if __name__ == "__main__":
    main()