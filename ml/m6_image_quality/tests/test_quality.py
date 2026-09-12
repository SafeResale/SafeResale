
from ml.m6_image_quality.scripts.common import (
    analyze_image_quality,
    analyze_eight_angle_images,
    calculate_blur_score,
    calculate_image_hash,
    calculate_luminance,
    calculate_glare_ratio,
    classify_exposure,
    compare_image_hashes,
)


FIXTURES = "ml/m6_image_quality/data/fixtures"


def test_sharp_image_has_high_blur_score():
    image = f"{FIXTURES}/sharp.jpg"

    score = calculate_blur_score(image)

    assert score > 100


def test_blurry_image_has_low_blur_score():
    image = f"{FIXTURES}/blurry.jpg"

    score = calculate_blur_score(image)

    assert score < 100


def test_dark_image_is_underexposed():
    image = f"{FIXTURES}/dark.jpg"

    luminance = calculate_luminance(image)
    result = classify_exposure(luminance)

    assert result == "underexposed"


def test_bright_image_is_overexposed():
    image = f"{FIXTURES}/bright.jpg"

    luminance = calculate_luminance(image)
    result = classify_exposure(luminance)

    assert result == "overexposed"


def test_normal_image_has_lower_glare_ratio_than_glare_image():
    normal = f"{FIXTURES}/normal.jpg"
    glare = f"{FIXTURES}/glare.jpg"

    normal_ratio = calculate_glare_ratio(normal)
    glare_ratio = calculate_glare_ratio(glare)

    assert glare_ratio > normal_ratio


def test_identical_images_have_zero_hash_distance():
    image = f"{FIXTURES}/sharp.jpg"

    distance = compare_image_hashes(image, image)

    assert distance == 0


def test_different_images_have_larger_hash_distance():
    sharp = f"{FIXTURES}/sharp.jpg"
    dark = f"{FIXTURES}/dark.jpg"

    distance = compare_image_hashes(sharp, dark)

    assert distance > 0


def test_image_hash_is_generated():
    image = f"{FIXTURES}/sharp.jpg"

    image_hash = calculate_image_hash(image)

    assert isinstance(image_hash, str)
    assert len(image_hash) > 0


def test_sharp_image_passes_quality_check():
    image = f"{FIXTURES}/sharp.jpg"

    result = analyze_image_quality(image)

    assert "blurry" not in result["issues"]
    assert "underexposed" not in result["issues"]
    assert "overexposed" not in result["issues"]


def test_blurry_image_fails_quality_check():
    image = f"{FIXTURES}/blurry.jpg"

    result = analyze_image_quality(image)

    assert result["passed"] is False
    assert "blurry" in result["issues"]


def test_dark_image_fails_quality_check():
    image = f"{FIXTURES}/dark.jpg"

    result = analyze_image_quality(image)

    assert result["passed"] is False
    assert "underexposed" in result["issues"]


def test_bright_image_fails_quality_check():
    image = f"{FIXTURES}/bright.jpg"

    result = analyze_image_quality(image)

    assert result["passed"] is False
    assert "overexposed" in result["issues"]

def test_sharp_image_has_quality_score_of_100():
    image = f"{FIXTURES}/sharp.jpg"

    result = analyze_image_quality(image)

    assert result["quality_score"] == 80.0


def test_blurry_image_has_lower_quality_score():
    image = f"{FIXTURES}/blurry.jpg"

    result = analyze_image_quality(image)

    assert result["quality_score"] < 100.0
    assert result["quality_score"] == 40.0


def test_dark_image_has_lower_quality_score():
    image = f"{FIXTURES}/dark.jpg"

    result = analyze_image_quality(image)

    assert result["quality_score"] == 30.0


def test_quality_analyzer_has_required_output_structure():
    image = f"{FIXTURES}/sharp.jpg"

    result = analyze_image_quality(image)

    assert "quality_score" in result
    assert "issues" in result
    assert "passed" in result
    assert "details" in result

    assert "blur_score" in result["details"]
    assert "luminance" in result["details"]
    assert "exposure" in result["details"]
    assert "glare_ratio" in result["details"]

def test_eight_angle_analysis_detects_duplicate_images():
    image = "ml/m6_image_quality/data/fixtures/sharp.jpg"

    images = [
        image,
        image,
        image,
        image,
        image,
        image,
        image,
        image,
    ]

    result = analyze_eight_angle_images(images)

    assert result["passed"] is False
    assert len(result["duplicate_or_similar_pairs"]) > 0

    for image_result in result["image_results"]:
        assert "duplicate_or_similar" in image_result["issues"]
        assert image_result["passed"] is False

def test_glare_image_detects_glare():
    image = "ml/m6_image_quality/data/evaluation/laptop/glare/glare_3.jpg"

    result = analyze_image_quality(image)

    assert "glare" in result["issues"]
    assert result["passed"] is False