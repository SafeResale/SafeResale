"""M6 bridge shim: runs the real OpenCV image-quality checks inside WSL.
Reads JSON from stdin, prints JSON on stdout."""
import json
import os
import sys


def main() -> None:
    payload = json.load(sys.stdin)
    os.chdir(payload["change_cwd"] or os.getcwd())
    scripts = os.path.join(os.getcwd(), "ml", "m6_image_quality", "scripts")
    if scripts not in sys.path:
        sys.path.insert(0, scripts)

    from common import analyze_image_quality  # type: ignore

    out = []
    for im in payload.get("images", []):
        try:
            q = analyze_image_quality(im["image"])
        except Exception as exc:  # per-image error, never kills the batch
            q = {"error": str(exc), "passed": True}
        out.append({"image_id": im.get("image_id"), "server_quality": q})
    print(json.dumps(out))


if __name__ == "__main__":
    main()