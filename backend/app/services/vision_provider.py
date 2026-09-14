"""Pluggable vision provider — stub default, real via YoloClipNimProvider when available."""
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

def get_provider():
    import os
    from pathlib import Path
    mode = os.environ.get("VISION_PROVIDER", "stub").lower()
    if mode != "real":
        return StubProvider()
    # try real provider from ml
    try:
        import sys
        sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ml" / "m1_defect_detection" / "scripts"))
        from vision_provider import YoloClipNimProvider  # type: ignore
        weights = os.environ.get("ML_WEIGHTS_DIR") or os.environ.get("M1_WEIGHTS") or ""
        if not weights:
            cand = Path.home() / "safresale-ml" / "m1" / "runs" / "improved-yolo11n-4" / "weights" / "best.pt"
            if cand.exists():
                weights = str(cand)
        if weights and Path(weights).exists():
            return YoloClipNimProvider(weights=weights)
    except Exception:
        pass
    return StubProvider()
