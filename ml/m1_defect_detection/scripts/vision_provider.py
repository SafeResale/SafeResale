"""VisionProvider for SafeResale — YOLO11 primary + CLIP fallback + NIM description.

Implements the interface from docs/03-architecture.md §5 in-process (no ai-worker needed
on this machine). The provider is *pluggable*: stub keeps the system green when weights
or deps are missing.

Selection via env:
  VISION_PROVIDER=stub  -> always StubProvider (default)
  VISION_PROVIDER=real  -> YoloClipNimProvider (requires weights + deps)

Env keys (put in ml/m1_defect_detection/.env, never commit):
  ML_WEIGHTS_DIR=/home/irahu/safresale-ml/m1/runs/improved-yolo11n-4/weights/best.pt
  NVAPI_KEY=nvapi-...          # build.nvidia.com, for NIM description only

Design decisions (locked):
- YOLO decides. CLIP is a second-opinion fallback (never a hard verdict).
- NIM writes a natural-language sentence only (best-effort, template fallback).
- Free-form VLM text is never used for a verdict (academic-integrity rule).
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import List, Dict, Any

try:
    from ultralytics import YOLO  # type: ignore
except Exception:  # pragma: no cover
    YOLO = None  # type: ignore

try:
    from clip_fallback import CLIPFallback  # type: ignore
except Exception:
    CLIPFallback = None  # type: ignore

try:
    from describe import Describer  # type: ignore
except Exception:
    Describer = None  # type: ignore

# Reuse severity from assess_device so grading stays consistent
try:
    from assess_device import SEVERITY, DEFAULT_SEVERITY  # type: ignore
except Exception:
    SEVERITY: Dict[str, float] = {}
    DEFAULT_SEVERITY = 0.5


class StubProvider:
    def simulated(self) -> bool:
        return True

    def detect_defects(self, images: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        # deterministic stub: no defects
        return [{"image": im.get("image", ""), "detections": [], "simulated": True} for im in images]

    def classify_condition(self, images: List[Dict[str, Any]]) -> Dict[str, Any]:
        return {"label": "Good", "probabilities": {"Good": 1.0}, "simulated": True, "model_version": "stub"}


class YoloClipNimProvider:
    """Real provider: YOLO primary, CLIP verifier, NIM describer."""

    def __init__(
        self,
        weights: str | Path,
        enable_clip: bool = True,
        enable_describe: bool = True,
        conf: float = 0.25,
        device: str = "0",
    ):
        if YOLO is None:
            raise RuntimeError("ultralytics not installed")
        p = Path(weights)
        if not p.exists():
            raise FileNotFoundError(f"weights not found: {p}")
        self.weights = str(p)
        self.conf = conf
        self.device = device
        self.model = YOLO(self.weights)
        self.names: Dict[int, str] = self.model.names  # type: ignore
        self.clip = None
        if enable_clip and CLIPFallback is not None:
            try:
                self.clip = CLIPFallback(device="cuda:0" if device != "cpu" else "cpu")
                # lazy load on first predict to avoid cold-start cost if unused
            except Exception:
                self.clip = None
        self.describer = None
        if enable_describe and Describer is not None:
            try:
                self.describer = Describer()
            except Exception:
                self.describer = None

    def simulated(self) -> bool:
        return False

    def detect_defects(self, images: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """images: list of {image: path, image_id?: str}. Returns per-image detections."""
        out: List[Dict[str, Any]] = []
        for im in images:
            path = im.get("image") or im.get("path") or im.get("source") or ""
            r = self.model.predict(source=str(path), conf=self.conf, verbose=False)[0]
            dets: List[Dict[str, Any]] = []
            if r.boxes is not None:
                for box in r.boxes:
                    cls = int(box.cls[0])
                    name = self.names.get(cls, str(cls))
                    dets.append(
                        {
                            "class": name,
                            "conf": float(box.conf[0]),
                            "bbox": [float(v) for v in box.xyxy[0].tolist()],
                        }
                    )
            # CLIP fallback — only when YOLO is silent or very weak
            clip_info = None
            if self.clip is not None:
                try:
                    # trigger only if no confirmed-strong detection
                    max_conf = max((d["conf"] for d in dets), default=0.0)
                    if not dets or max_conf < 0.35:
                        # lazy load
                        if self.clip.model is None:
                            self.clip.load()
                        clip_info = self.clip.predict(str(path))
                        # never promote CLIP to a hard detection; surface as advisory
                except Exception:
                    clip_info = None

            # description (best-effort, never decides)
            desc_text, desc_source = None, None
            if self.describer is not None:
                try:
                    desc_text, desc_source = self.describer.describe(str(path), detections=dets)
                except Exception:
                    desc_text, desc_source = None, None

            entry: Dict[str, Any] = {
                "image": str(path),
                "detections": dets,
                "simulated": False,
                "model_version": Path(self.weights).parent.name,
            }
            if clip_info is not None:
                entry["clip"] = clip_info
            if desc_text is not None:
                entry["description"] = {"text": desc_text, "source": desc_source}
            out.append(entry)
        return out

    def classify_condition(self, images: List[Dict[str, Any]]) -> Dict[str, Any]:
        dets = self.detect_defects(images)
        # reuse the same deduction/grade logic as assess_device (lightweight)
        # here we do a simple max-deduction grade for the provider response
        max_ded = 0.0
        for im in dets:
            for d in im["detections"]:
                sev = SEVERITY.get(d["class"], DEFAULT_SEVERITY)
                max_ded = max(max_ded, sev * d["conf"] * 100.0)
        score = max(0.0, 100.0 - max_ded)
        if score >= 85:
            label = "Good"
        elif score >= 50:
            label = "Moderate"
        else:
            label = "Defective"
        return {
            "label": label,
            "score": round(score, 1),
            "probabilities": {label: 1.0},
            "simulated": False,
            "model_version": Path(self.weights).parent.name,
        }


def get_provider() -> Any:
    """Factory honoring VISION_PROVIDER env (stub default)."""
    mode = os.environ.get("VISION_PROVIDER", "stub").lower()
    if mode != "real":
        return StubProvider()
    weights = os.environ.get("ML_WEIGHTS_DIR") or os.environ.get("M1_WEIGHTS") or ""
    # also try the well-known 8-class checkpoint if env not set
    if not weights:
        cand = Path.home() / "safresale-ml" / "m1" / "runs" / "improved-yolo11n-4" / "weights" / "best.pt"
        if cand.exists():
            weights = str(cand)
    if not weights or not Path(weights).exists():
        # fall back to stub if weights missing — never crash the app
        return StubProvider()
    try:
        return YoloClipNimProvider(weights=weights)
    except Exception:
        return StubProvider()
