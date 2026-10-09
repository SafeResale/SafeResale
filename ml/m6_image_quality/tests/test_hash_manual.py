from ml.m6_image_quality.scripts.common import calculate_image_hash
import imagehash
from PIL import Image


original = "ml/m6_image_quality/data/fixtures/sharp.jpg"
similar = "ml/m6_image_quality/data/fixtures/sharp.jpg"
different = "ml/m6_image_quality/data/fixtures/dark.jpg"

original_hash = calculate_image_hash(original)
similar_hash = calculate_image_hash(similar)
different_hash = calculate_image_hash(different)

print("Original hash :", original_hash)
print("Similar hash  :", similar_hash)
print("Different hash:", different_hash)

original_hash_obj = imagehash.hex_to_hash(original_hash)
similar_hash_obj = imagehash.hex_to_hash(similar_hash)
different_hash_obj = imagehash.hex_to_hash(different_hash)

similar_distance = original_hash_obj - similar_hash_obj
different_distance = original_hash_obj - different_hash_obj

print("Similar image distance  :", similar_distance)
print("Different image distance:", different_distance)

if similar_distance == 0:
    print("PASS: Identical images have identical perceptual hashes.")
else:
    print("FAIL: Identical images produced different hashes.")

if different_distance > similar_distance:
    print("PASS: Different image has a larger hash distance.")
else:
    print("FAIL: Hash distance is not behaving as expected.")