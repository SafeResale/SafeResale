from ml.m6_image_quality.scripts.common import (
    calculate_luminance,
    classify_exposure,
)

dark = "ml/m6_image_quality/data/fixtures/dark.jpg"
bright = "ml/m6_image_quality/data/fixtures/bright.jpg"

dark_luminance = calculate_luminance(dark)
bright_luminance = calculate_luminance(bright)

dark_result = classify_exposure(dark_luminance)
bright_result = classify_exposure(bright_luminance)

print("Dark luminance  :", dark_luminance)
print("Dark result     :", dark_result)

print("Bright luminance:", bright_luminance)
print("Bright result   :", bright_result)

if dark_result == "underexposed":
    print("PASS: Dark image correctly classified.")
else:
    print("FAIL: Dark image classification.")

if bright_result == "overexposed":
    print("PASS: Bright image correctly classified.")
else:
    print("FAIL: Bright image classification.")