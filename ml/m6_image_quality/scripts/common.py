"""Shared M6 implementation: image-quality checks + accept/reject metrics.

All M6 logic lives here (moved verbatim from the old `src/` package) so scripts
and tests import from one place. Thresholds come from ``configs/m6.yaml`` with
the historical defaults as a fallback.

Run from the repo root so absolute imports resolve:
  python ml/m6_image_quality/scripts/infer.py --image <path>
"""
import os
from pathlib import Path

import cv2
import imagehash
import yaml
from PIL import Image

HERE = Path(__file__).resolve().parent
DEFAULT_CONFIG_PATH = HERE.parent / "configs" / "m6.yaml"

_DEFAULT_QUALITY_CONFIG = {
    "blur_threshold": 250.0,
    "underexposed_luminance": 85,
    "overexposed_luminance": 160,
    "glare_ratio_threshold": 0.059,
    "duplicate_hash_distance_threshold": 8,
}


def load_quality_config() -> dict:
    """Read the quality thresholds, overriding defaults with configs/m6.yaml (if present)."""
    config_path = Path(os.environ.get("M6_CONFIG_PATH", DEFAULT_CONFIG_PATH))
    try:
        with open(config_path, "r", encoding="utf-8") as file:
            data = yaml.safe_load(file) or {}
        quality = data.get("quality") or {}
    except (OSError, yaml.YAMLError):
        quality = {}

    config = dict(_DEFAULT_QUALITY_CONFIG)
    config.update({key: value for key, value in quality.items() if value is not None})
    return config


_QUALITY_CONFIG = load_quality_config()

BLUR_THRESHOLD = _QUALITY_CONFIG["blur_threshold"]
UNDEREXPOSED_LUMINANCE = _QUALITY_CONFIG["underexposed_luminance"]
OVEREXPOSED_LUMINANCE = _QUALITY_CONFIG["overexposed_luminance"]
GLARE_RATIO_THRESHOLD = _QUALITY_CONFIG["glare_ratio_threshold"]
DUPLICATE_HASH_DISTANCE_THRESHOLD = _QUALITY_CONFIG["duplicate_hash_distance_threshold"]


def calculate_blur_score(image_path: str) -> float:
    """
    Calculate image sharpness using Laplacian variance.

    Higher score = sharper image.
    Lower score = blurrier image.
    """
    image = cv2.imread(image_path)

    if image is None:
        raise ValueError(f"Could not read image: {image_path}")

    grayscale = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    score = cv2.Laplacian(grayscale, cv2.CV_64F).var()

    return float(score)


def calculate_luminance(image_path: str) -> float:
    """
    Calculate the average brightness of an image.

    Returns a value from 0 to 255.
    Lower = darker image.
    Higher = brighter image.
    """
    image = cv2.imread(image_path)

    if image is None:
        raise ValueError(f"Could not read image: {image_path}")

    grayscale = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    return float(grayscale.mean())


def classify_exposure(
    luminance: float,
    underexposed_threshold: float = UNDEREXPOSED_LUMINANCE,
    overexposed_threshold: float = OVEREXPOSED_LUMINANCE,
) -> str:
    """
    Classify image exposure using average luminance.

    Returns:
        underexposed
        good
        overexposed
    """
    if luminance < underexposed_threshold:
        return "underexposed"

    if luminance > overexposed_threshold:
        return "overexposed"

    return "good"


def calculate_glare_ratio(
    image_path: str,
    value_threshold: int = 240,
    saturation_threshold: int = 60,
) -> float:
    """
    Estimate glare using HSV color space.

    Glare is treated as pixels that are:
    - very bright
    - low in saturation

    Returns a value from 0.0 to 1.0.
    """
    image = cv2.imread(image_path)

    if image is None:
        raise ValueError(f"Could not read image: {image_path}")

    hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)

    saturation = hsv[:, :, 1]
    value = hsv[:, :, 2]

    glare_pixels = (
        (value >= value_threshold)
        & (saturation <= saturation_threshold)
    )

    return float(glare_pixels.mean())


def calculate_image_hash(image_path: str) -> str:
    """
    Calculate a perceptual hash for an image.

    Similar images should have similar hash values.
    """
    try:
        image = Image.open(image_path)
    except Exception as exc:
        raise ValueError(f"Could not read image: {image_path}") from exc

    return str(imagehash.phash(image))


def compare_image_hashes(image_path_1: str, image_path_2: str) -> int:
    """
    Calculate the Hamming distance between two perceptual hashes.

    Lower distance = more visually similar.
    Higher distance = more visually different.
    """
    hash_1 = imagehash.hex_to_hash(
        calculate_image_hash(image_path_1)
    )

    hash_2 = imagehash.hex_to_hash(
        calculate_image_hash(image_path_2)
    )

    return int(hash_1 - hash_2)


def calculate_quality_score(
    blur_score: float,
    exposure: str,
    glare_ratio: float,
) -> float:
    """
    Calculate an M6 image quality score from 0 to 100.

    The score is reduced for blur, poor exposure, and glare.
    """
    score = 100.0

    if blur_score < BLUR_THRESHOLD:
        score -= 40.0

    if exposure == "underexposed":
        score -= 30.0

    if exposure == "overexposed":
        score -= 30.0

    if glare_ratio > GLARE_RATIO_THRESHOLD:
        score -= 20.0

    return max(0.0, min(100.0, score))


def analyze_image_quality(
    image_path: str,
    blur_threshold: float = BLUR_THRESHOLD,
) -> dict:
    """
    Run all M6 image-quality checks on one image.

    Returns a structured quality report.
    """
    blur_score = calculate_blur_score(image_path)

    luminance = calculate_luminance(image_path)

    exposure = classify_exposure(luminance)

    glare_ratio = calculate_glare_ratio(image_path)

    quality_score = calculate_quality_score(
    blur_score=blur_score,
    exposure=exposure,
    glare_ratio=glare_ratio,
)

    flags = []

    if blur_score < blur_threshold:
        flags.append("blurry")

    if exposure == "underexposed":
        flags.append("underexposed")

    if exposure == "overexposed":
        flags.append("overexposed")

    if glare_ratio > GLARE_RATIO_THRESHOLD:
        flags.append("glare")

    quality_pass = len(flags) == 0

    return {
        "quality_score": quality_score,
        "issues": flags,
        "passed": quality_pass,
        "details": {
            "blur_score": blur_score,
            "luminance": luminance,
            "exposure": exposure,
            "glare_ratio": glare_ratio,
        },
    }


def analyze_eight_angle_images(image_paths: list[str]) -> dict:
    """
    Analyze the 8 required SafeResale product images.

    Each image is analyzed individually.

    The system also compares the perceptual hashes of all images
    to detect duplicate or very similar image uploads.

    The overall quality score is the average of the 8
    individual quality scores.

    All 8 images must pass for the overall result to pass.
    """

    REQUIRED_IMAGE_COUNT = 8

    if len(image_paths) != REQUIRED_IMAGE_COUNT:
        raise ValueError(
            f"Exactly {REQUIRED_IMAGE_COUNT} images are required, "
            f"but received {len(image_paths)}"
        )

    image_results = []

    # Analyze each image individually.
    for image_path in image_paths:
        result = analyze_image_quality(image_path)

        image_results.append({
            "image_path": image_path,
            **result,
        })

    # Compare every image against every other image.
    duplicate_pairs = []

    for i in range(REQUIRED_IMAGE_COUNT):
        for j in range(i + 1, REQUIRED_IMAGE_COUNT):

            distance = compare_image_hashes(
                image_paths[i],
                image_paths[j],
            )

            if distance <= DUPLICATE_HASH_DISTANCE_THRESHOLD:
                duplicate_pairs.append({
                    "image_1": image_paths[i],
                    "image_2": image_paths[j],
                    "hash_distance": distance,
                })

                # Mark both images as duplicate/similar.
                if "duplicate_or_similar" not in image_results[i]["issues"]:
                    image_results[i]["issues"].append(
                        "duplicate_or_similar"
                    )

                if "duplicate_or_similar" not in image_results[j]["issues"]:
                    image_results[j]["issues"].append(
                        "duplicate_or_similar"
                    )

                image_results[i]["passed"] = False
                image_results[j]["passed"] = False

    scores = [
        result["quality_score"]
        for result in image_results
    ]

    passed_images = sum(
        result["passed"]
        for result in image_results
    )

    overall_score = sum(scores) / REQUIRED_IMAGE_COUNT

    failed_images = REQUIRED_IMAGE_COUNT - passed_images

    return {
        "overall_quality_score": round(overall_score, 2),
        "passed": passed_images == REQUIRED_IMAGE_COUNT,
        "total_images": REQUIRED_IMAGE_COUNT,
        "passed_images": passed_images,
        "failed_images": failed_images,
        "duplicate_or_similar_pairs": duplicate_pairs,
        "image_results": image_results,
    }


def calculate_accept_reject_metrics(
    labels_file: str,
) -> dict:
    """
    Compare manually labeled images with M6 predictions.

    good = expected to be accepted
    blurry / underexposed / overexposed / glare = expected to be rejected
    """

    import csv

    total = 0
    correct = 0
    results = []

    with open(labels_file, newline="", encoding="utf-8") as csv_file:
        reader = csv.DictReader(csv_file)

        for row in reader:
            image_path = row["image_path"]
            actual_label = row["actual_label"]

            result = analyze_image_quality(image_path)

            actual_pass = actual_label == "good"
            predicted_pass = result["passed"]

            is_correct = actual_pass == predicted_pass

            total += 1

            if is_correct:
                correct += 1

            results.append({
                "image_path": image_path,
                "actual_label": actual_label,
                "actual_pass": actual_pass,
                "predicted_pass": predicted_pass,
                "predicted_issues": result["issues"],
                "correct": is_correct,
            })

    accuracy = (correct / total * 100) if total > 0 else 0.0

    return {
        "total_images": total,
        "correct_predictions": correct,
        "incorrect_predictions": total - correct,
        "accept_reject_accuracy": round(accuracy, 2),
        "results": results,
    }