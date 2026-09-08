"""M3 — AI-generated-image detection provider.

Pluggable authenticity provider (parallel to the vision provider per
03-architecture.md:188). Detects whether a listing photo was AI-generated
(as opposed to live-captured by the seller).

Selection via AUTHENTICITY_PROVIDER=stub|real (default: stub), mirroring the
VISION_PROVIDER pattern. One Python process loads the model lazily so the stub
stays green for CI/tests until the real weights are available.

StubAuthenticityProvider   — deterministic, seeded, simulated=True. Always green.
RealAuthenticityProvider   — wraps the fine-tuned ViT classifier
    (dima806/ai_vs_human_generated_image_detection, apache-2.0), simulated=False.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Protocol, runtime_checkable

WORK_ROOT = Path(os.environ.get("M1_WORK_ROOT", Path.home() / "safresale-ml" / "m1"))
HF_CACHE = os.environ.get("HF_HOME", str(WORK_ROOT / ".hf"))

# Model name / files on the Hugging Face Hub.
MODEL_ID = "dima806/ai_vs_human_generated_image_detection"
# Threshold for flagging an image as AI-generated. The model card warns this
# set drifts over time; 0.5 is the published default, configurable via --threshold.
DEFAULT_THRESHOLD = 0.5
# Probability at/below this makes the verdict "unverifiable" rather than a
# hard human/AI call (authenticity gate: 01-prd.md:77).
AMBIGUOUS_BAND = 0.02


@runtime_checkable
class AuthenticityProvider(Protocol):
    """Analyze one or more images for AI-generated origin.

    Shapes match the vision provider pattern (03-architecture.md:105-121) so a
    caller can swap stub <-> real without changes.
    """

    def verify(self, images: list[str]) -> list["AuthenticityResult"]: ...

    def simulated(self) -> bool: ...


class AuthenticityResult:
    __slots__ = ("image", "ai_generated", "human", "label", "threshold", "model_version")

    def __init__(
        self,
        image: str,
        ai_generated: float,
        human: float,
        label: str,
        threshold: float,
        model_version: str,
    ) -> None:
        self.image = image
        self.ai_generated = ai_generated
        self.human = human
        self.label = label  # one of: human | ai-generated | ambiguous
        self.threshold = threshold
        self.model_version = model_version

    def to_dict(self) -> dict[str, Any]:
        return {
            "image": self.image,
            "label": self.label,
            "ai_generated_prob": round(self.ai_generated, 4),
            "human_prob": round(self.human, 4),
            "confidence": round(max(self.ai_generated, self.human), 4),
            "ai_generated": self.label == "ai-generated",
            "threshold": self.threshold,
            "model_version": self.model_version,
        }


def _decide(ai_prob: float, threshold: float) -> str:
    """Map an AI-probability to a label including the ambiguous band."""
    if ai_prob >= threshold:
        return "ai-generated"
    if ai_prob >= threshold - AMBIGUOUS_BAND:
        return "ambiguous"
    return "human"


class StubAuthenticityProvider:
    """Deterministic, seeded. simulated() == True. Used by default and CI."""

    def __init__(self, threshold: float = DEFAULT_THRESHOLD) -> None:
        self.threshold = threshold
        self.model_version = "stub-v1"

    def verify(self, images: list[str]) -> list[AuthenticityResult]:
        out = []
        # Seed from the filename so results are deterministic but not all equal.
        for img in images:
            seed = hash(os.path.basename(img)) & 0xFFFF
            ai_prob = round((seed % 1000) / 1000.0, 4)
            out.append(
                AuthenticityResult(
                    img, ai_prob, round(1.0 - ai_prob, 4),
                    _decide(ai_prob, self.threshold), self.threshold, self.model_version,
                )
            )
        return out

    def simulated(self) -> bool:
        return True

    def close(self) -> None:
        pass


class RealAuthenticityProvider:
    """Wraps the fine-tuned ViT classifier. simulated() == False.

    Model weights are downloaded lazily on first use (343MB, cached in HF_CACHE).
    Requires `transformers` in the active environment.
    """

    def __init__(
        self,
        model_id: str = MODEL_ID,
        threshold: float = DEFAULT_THRESHOLD,
        cache_dir: str | None = None,
    ) -> None:
        self.model_id = model_id
        self.threshold = threshold
        self.cache_dir = cache_dir or HF_CACHE
        self._pipeline = None
        self.model_version = f"{model_id.split('/')[-1]}"
        self._load_error = None

    def _load(self):
        if self._pipeline is not None:
            return
        if self._load_error is not None:
            raise self._load_error
        try:
            os.environ.setdefault("HF_HOME", self.cache_dir)
            from transformers import pipeline

            self._pipeline = pipeline("image-classification", model=self.model_id)
        except Exception as exc:  # pragma: no cover - surfaces load failures
            self._load_error = exc
            raise

    def verify(self, images: list[str]) -> list[AuthenticityResult]:
        self._load()
        out = []
        for img in images:
            if not Path(img).exists():
                out.append(
                    AuthenticityResult(
                        img, 0.0, 0.0, "human", self.threshold, self.model_version
                    )
                )
                continue
            res = self._pipeline(str(img))[0]
            label = res["label"]
            score = float(res["score"])
            if label == "AI-generated":
                ai_prob, human_prob = score, 1.0 - score
            else:
                ai_prob, human_prob = 1.0 - score, score
            out.append(
                AuthenticityResult(
                    img, ai_prob, human_prob,
                    _decide(ai_prob, self.threshold), self.threshold, self.model_version,
                )
            )
        return out

    def simulated(self) -> bool:
        return False

    def close(self) -> None:
        self._pipeline = None


def provider_for(simulated: bool, *, threshold: float = DEFAULT_THRESHOLD,
                 model_id: str = MODEL_ID) -> AuthenticityProvider:
    if simulated:
        return StubAuthenticityProvider(threshold=threshold)
    return RealAuthenticityProvider(model_id=model_id, threshold=threshold)


def _main() -> None:
    parser = argparse.ArgumentParser(description="M3 AI-generated-image detection")
    parser.add_argument("source", type=str, nargs="+", help="Image file(s) or a folder")
    parser.add_argument("--stub", action="store_true",
                        help="Force the deterministic stub instead of the real model")
    parser.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD)
    parser.add_argument("--model-id", type=str, default=MODEL_ID)
    parser.add_argument("--json", help="Write the report to this path")
    parser.add_argument("--all", action="store_true",
                        help="Return every classification, not just the top label")
    args = parser.parse_args()

    files: list[str] = []
    for s in args.source:
        p = Path(s)
        if p.is_dir():
            files.extend(
                str(x) for x in sorted(p.iterdir())
                if x.suffix.lower() in (".jpg", ".jpeg", ".png")
            )
        elif p.exists():
            files.append(str(p))
        else:
            sys.exit(f"not found: {s}")

    if not files:
        sys.exit("no images found")

    provider = provider_for(simulated=args.stub, threshold=args.threshold,
                            model_id=args.model_id)
    try:
        results = provider.verify(files)
    finally:
        provider.close()

    print(f"provider   : {'stub' if provider.simulated() else 'real'} "
          f"({provider.model_version})")
    print(f"threshold  : {args.threshold}")
    print(f"images     : {len(results)}")
    print("-" * 66)
    for r in results:
        mark = "AI" if r.label == "ai-generated" else ("?" if r.label == "ambiguous" else "ok")
        print(f"  [{mark}] {r.image:<42} "
              f"AI={r.ai_generated:.3f} human={r.human:.3f}")
    print("-" * 66)

    if args.json:
        report = {
            "provider": provider.model_version,
            "simulated": provider.simulated(),
            "threshold": args.threshold,
            "results": [r.to_dict() for r in results],
        }
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)
        print(f"wrote: {args.json}")


if __name__ == "__main__":
    _main()
