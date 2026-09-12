from ml.m6_image_quality.scripts.common import compare_image_hashes


original = "ml/m6_image_quality/data/fixtures/sharp.jpg"
same = "ml/m6_image_quality/data/fixtures/sharp.jpg"
different = "ml/m6_image_quality/data/fixtures/dark.jpg"

same_distance = compare_image_hashes(original, same)
different_distance = compare_image_hashes(original, different)

print("Same image distance     :", same_distance)
print("Different image distance:", different_distance)

if same_distance == 0:
    print("PASS: Same image distance is 0.")
else:
    print("FAIL: Same image should have distance 0.")

if different_distance > same_distance:
    print("PASS: Different image has a larger distance.")
else:
    print("FAIL: Different image does not have a larger distance.")