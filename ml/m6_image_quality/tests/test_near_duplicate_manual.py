from ml.m6_image_quality.scripts.common import compare_image_hashes


original = "ml/m6_image_quality/data/fixtures/sharp.jpg"
modified = "ml/m6_image_quality/data/fixtures/sharp_modified.jpg"

distance = compare_image_hashes(original, modified)

print("Near-duplicate distance:", distance)

if distance < 10:
    print("PASS: Near-duplicate images have a small perceptual distance.")
else:
    print("CHECK: Near-duplicate distance is relatively large.")