from ml.m6_image_quality.scripts.common import calculate_blur_score

sharp = "ml/m6_image_quality/data/fixtures/sharp.jpg"
blurry = "ml/m6_image_quality/data/fixtures/blurry.jpg"

sharp_score = calculate_blur_score(sharp)
blurry_score = calculate_blur_score(blurry)

print("Sharp image score :", sharp_score)
print("Blurry image score:", blurry_score)

if sharp_score > blurry_score:
    print("PASS: Sharp image has a higher score than blurry image.")
else:
    print("FAIL: Blur detection is not behaving as expected.")