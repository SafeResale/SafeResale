"""M1+M2 bridge shim: runs the real YOLO defect detector + condition classifier
inside WSL (where the model was trained). Reads JSON from stdin, prints JSON on
stdout. Invoked by the Windows backend via app/services/wsl.py."""
import json
import os
import sys
import tempfile
from pathlib import Path


SUPPORTED_SUFFIXES = {"jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "avif", "heif", "heic", "jp2", "dng", "mpo"}


def _legible(path: str) -> str:
    """ultralytics refuses extension-less files, but stored uploads may have none.
    Decode with cv2 and re-encode to a temp .jpg so YOLO can read them."""
    if Path(path).suffix.lower().lstrip(".") in SUPPORTED_SUFFIXES:
        return path
    import cv2
    img = cv2.imread(path)
    if img is None:
        raise RuntimeError(f"cv2 could not read: {path}")
    tmp = tempfile.NamedTemporaryFile(suffix=".jpg", prefix="sr_", delete=False)
    ok, buf = cv2.imencode(".jpg", img)
    if not ok:
        tmp.close()
        raise RuntimeError(f"could not re-encode: {path}")
    tmp.write(buf.tobytes())
    tmp.close()
    return tmp.name


def main() -> None:
    payload = json.load(sys.stdin)
    os.chdir(payload["change_cwd"] or os.getcwd())
    scripts = os.path.join(os.getcwd(), "ml", "m1_defect_detection", "scripts")
    if scripts not in sys.path:
        sys.path.insert(0, scripts)

    from vision_provider import YoloClipNimProvider  # type: ignore

    weights = None
    for k in ("weights", "ML_WEIGHTS_DIR", "M1_WEIGHTS"):
        v = payload.get(k)
        if v:
            weights = str(v)
            break
    if not weights:
        weights = os.environ.get("ML_WEIGHTS_DIR") or os.environ.get("M1_WEIGHTS")
    if not weights:
        cand = Path.home() / "safresale-ml" / "m1" / "runs" / "improved-yolo11n-4" / "weights" / "best.pt"
        if cand.exists():
            weights = str(cand)
    if not weights or not Path(weights).exists():
        print(json.dumps({
            "ok": False,
            "error": f"weights not found: {weights}",
            "detections": [],
            "condition": None,
            "model_versions": {},
        }))
        return

    provider = YoloClipNimProvider(
        weights=weights, device=payload.get("device", "0")
    )
    temps: list[str] = []
    try:
        image_inputs = []
        seen = {}
        for im in payload.get("images", []):
            legible = _legible(im["image"])
            if legible != im["image"]:
                temps.append(legible)
                seen[legible] = im["image"]
            image_inputs.append({"image": legible, "image_id": im.get("image_id")})
        detections = provider.detect_defects(image_inputs)
        condition = provider.classify_condition(image_inputs)
    finally:
        if hasattr(provider, "close"):
            provider.close()
        for t in temps:
            try:
                os.unlink(t)
            except OSError:
                pass
    for entry in detections:
        entry["image"] = seen.get(entry.get("image"), entry.get("image"))
    print(json.dumps({
        "ok": True,
        "simulated": provider.simulated(),
        "detections": detections,
        "condition": condition,
        "model_versions": {
            "detector": Path(weights).resolve().parents[1].name,
            "classifier": Path(weights).resolve().parents[1].name,
        },
    }))


if __name__ == "__main__":
    main()