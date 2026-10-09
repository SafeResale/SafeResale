"""Pluggable vision provider — stub default, real via YoloClipNimProvider when available.

Selection via VISION_PROVIDER:
  stub  -> always StubProvider (default; also fallback so the app never crashes)
  real  -> YoloClipNimProvider in this process if ultralytics/torch can import
           (e.g. backend running inside WSL), otherwise WslVisionProvider which
           runs the trained YOLO in the WSL ML venv through ml/bridge/analyze.py.
"""
from typing import Protocol, List

class VisionProvider(Protocol):
    def detect_defects(self, images: List[dict]) -> List[dict]: ...
    def classify_condition(self, images: List[dict]) -> dict: ...
    def simulated(self) -> bool: ...

class StubProvider:
    def simulated(self) -> bool:
        return True
    def detect_defects(self, images: List[dict]):
        return [{"image": im.get("image",""), "detections": [], "simulated": True} for im in images]
    def classify_condition(self, images: List[dict]):
        return {"label": "Good", "probabilities": {"Good": 1.0}, "simulated": True, "model_version": "stub-1.0"}


class WslVisionProvider:
    """Runs the trained YOLO (M1) + condition classifier (M2) inside WSL.

    The backend (Windows) sends the stored image paths to ml/bridge/analyze.py
    in the WSL ML venv, which loads the real checkpoint and returns detections.
    Both methods share one subprocess call per listing (cached).
    """
    def __init__(self, weights: str = ""):
        self.weights = weights
        self._cache = None

    def simulated(self) -> bool:
        return False

    def _run(self, images: List[dict]) -> dict:
        if self._cache is not None:
            return self._cache
        from app.services import wsl
        from app.core.storage import storage
        payload_images = []
        for im in images:
            key = im.get("image") or im.get("path") or im.get("source") or ""
            payload_images.append({
                "image": wsl.to_mnt((storage.base / key).resolve()),
                "image_id": im.get("image_id") or key,
            })
        res = wsl.run_bridge("analyze.py", {"images": payload_images, "weights": self.weights}, timeout=300)
        if not res.get("ok"):
            raise RuntimeError(res.get("error") or "WSL vision failed")
        self._cache = res
        return res

    def detect_defects(self, images: List[dict]) -> List[dict]:
        res = self._run(images)
        out = []
        for entry in res.get("detections", []):
            e = dict(entry)
            e["image"] = e.get("image_id") or entry.get("image", "")
            out.append(e)
        return out

    def classify_condition(self, images: List[dict]) -> dict:
        res = self._run(images)
        return res.get("condition") or {"label": "Good", "probabilities": {"Good": 1.0}, "simulated": False}

    def model_versions(self):
        return (self._cache or {}).get("model_versions") or {}


def get_provider():
    import os
    from pathlib import Path
    mode = os.environ.get("VISION_PROVIDER", "stub").lower()
    if mode != "real":
        return StubProvider()
    weights = os.environ.get("ML_WEIGHTS_DIR") or os.environ.get("M1_WEIGHTS") or ""
    if not weights:
        cand = Path.home() / "safresale-ml" / "m1" / "runs" / "improved-yolo11n-4" / "weights" / "best.pt"
        if cand.exists():
            weights = str(cand)
    # real provider in this process (backend running inside WSL)
    try:
        import sys
        sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m1_defect_detection" / "scripts"))
        from vision_provider import YoloClipNimProvider  # type: ignore
        if weights and Path(weights).exists():
            return YoloClipNimProvider(weights=weights)
    except Exception:
        pass
    # real provider via the WSL ML venv (backend on Windows, models in WSL)
    try:
        import subprocess
        from app.services import wsl
        if not weights and not Path(weights).exists():
            pass  # shim resolves the default WSL weights itself when empty
        return WslVisionProvider(weights=weights)
    except Exception:
        return StubProvider()