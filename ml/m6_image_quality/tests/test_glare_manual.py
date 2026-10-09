from ml.m6_image_quality.scripts.common import calculate_glare_ratio

normal = "ml/m6_image_quality/data/fixtures/normal.jpg"
glare = "ml/m6_image_quality/data/fixtures/glare.jpg"

normal_ratio = calculate_glare_ratio(normal)
glare_ratio = calculate_glare_ratio(glare)

print("Normal image glare ratio:", normal_ratio)
print("Glare image glare ratio :", glare_ratio)

if glare_ratio > normal_ratio:
    print("PASS: Glare image has a higher glare ratio.")
else:
    print("FAIL: Glare detection is not behaving as expected.")