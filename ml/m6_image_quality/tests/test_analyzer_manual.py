from pprint import pprint

from ml.m6_image_quality.scripts.common import analyze_image_quality


sharp = "ml/m6_image_quality/data/fixtures/sharp.jpg"
blurry = "ml/m6_image_quality/data/fixtures/blurry.jpg"
dark = "ml/m6_image_quality/data/fixtures/dark.jpg"
bright = "ml/m6_image_quality/data/fixtures/bright.jpg"
glare = "ml/m6_image_quality/data/fixtures/glare.jpg"


print("\n===== SHARP IMAGE =====")
sharp_result = analyze_image_quality(sharp)
pprint(sharp_result)


print("\n===== BLURRY IMAGE =====")
blurry_result = analyze_image_quality(blurry)
pprint(blurry_result)


print("\n===== DARK IMAGE =====")
dark_result = analyze_image_quality(dark)
pprint(dark_result)


print("\n===== BRIGHT IMAGE =====")
bright_result = analyze_image_quality(bright)
pprint(bright_result)


print("\n===== GLARE IMAGE =====")
glare_result = analyze_image_quality(glare)
pprint(glare_result)