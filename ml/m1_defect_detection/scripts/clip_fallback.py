"""CLIP zero-shot fallback for M1 — local, deterministic, never decides alone.

- Loads openai/clip-vit-base-patch32 via transformers (fits 6 GB VRAM).
- Prompt bank covers the 8 trained classes + a clean class.
- Returns a second-opinion verdict: damaged vs clean + per-class scores.
- Intended as a *fallback verifier* when YOLO is silent/low-conf, not a primary detector.
  The caller (assess_device.py / VisionProvider) decides how to surface it.

Usage:
  from clip_fallback import CLIPFallback
  fb = CLIPFallback()               # auto device cuda:0 -> cpu
  r = fb.predict("path/to/img.jpg")
  # r = {"verdict": "damaged", "score": 0.73, "per_class": {"scratch": 0.31, ...}, "clean_prob": 0.12}

Requires: transformers, torch, pillow
Model cache: HF hub default (~350 MB for vit-b/32).
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import Dict

import torch
from PIL import Image
from transformers import CLIPModel, CLIPProcessor

DEFAULT_MODEL = "openai/clip-vit-base-patch32"

# Prompt bank — 2-3 phrasings per class, averaged at encode time.
# Keep phrasing photographic / observational (CLIP was trained on captions).
PROMPT_BANK: Dict[str, list[str]] = {
    "clean": [
        "a photo of a clean undamaged smartphone with no defects, pristine surface",
        "a flawless device with no damage, perfect condition",
        "a close-up photo of a pristine phone with no scratches or cracks",
        "a clean phone with only minor fingerprints or smudges from handling, no damage",
        "a phone with light sweat stains or hand marks on the surface, otherwise intact",
        "a hand holding a clean phone, dark screen turned off showing reflections, fingerprints and sweat smudges but no damage, intact device",
        "a phone screen turned off showing glare, reflections and oily hand marks, no cracks or dents, undamaged",
        "a close-up of a phone held in a hand, skin and fingers visible, glossy black screen with light reflections and smudges, undamaged",
    ],
    "scratch": [
        "a close-up photo of a phone with thin hairline scratches on the glass",
        "a device with visible surface scratches, fine lines on the body",
    ],
    "crack": [
        "a photo of a phone screen with a sharp crack line across the glass",
        "a device with a clearly visible crack, fractured glass with a line",
    ],
    "dent": [
        "a phone body with a deep dent where the metal or plastic is visibly pushed inward, structural deformation",
        "a device with a dented casing, clear physical indentation and deformed metal",
        "a close-up of a dented phone edge, obvious dent damage with deformed shape",
    ],
    "screen_damage": [
        "a phone screen visibly cracked and shattered with lines across the display, broken display",
        "a broken phone display with shattered glass and cracked screen, display damage",
        "a phone with a clearly broken screen, cracks and shattered display with visible damage",
    ],
    "glass_damage": [
        "a photo of a phone with shattered glass back panel, spiderweb cracks on glass",
        "a device with broken glass, shattered and cracked glass surface",
    ],
    "rust": [
        "a close-up photo of rust on metal",
        "a rusty device surface with reddish rust",
    ],
    "corrosion": [
        "a photo of corroded metal surface",
        "a device with corrosion and oxidation",
    ],
    "water_damage": [
        "a phone that was submerged in water, liquid pooled inside the screen",
        "heavy water damage with moisture and cracking from liquid ingress",
    ],
}

# Coarse families for reporting (maps fine class -> family if needed elsewhere)
COARSE_FAMILY = {
    "scratch": "surface",
    "crack": "surface",
    "dent": "surface",
    "screen_damage": "display",
    "glass_damage": "display",
    "rust": "chemical",
    "corrosion": "chemical",
    "water_damage": "chemical",
    "clean": "clean",
}


class CLIPFallback:
    def __init__(self, model_name: str = DEFAULT_MODEL, device: str | None = None):
        if device is None:
            device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = device
        self.model_name = model_name
        self.processor: CLIPProcessor | None = None
        self.model: CLIPModel | None = None
        self._text_embeds: torch.Tensor | None = None
        self._labels: list[str] = []

    def load(self) -> None:
        if self.model is not None:
            return
        self.processor = CLIPProcessor.from_pretrained(self.model_name)
        self.model = CLIPModel.from_pretrained(self.model_name).to(self.device).eval()
        # pre-encode prompt bank (mean per class)
        self._labels = list(PROMPT_BANK.keys())  # clean first, then 8 classes
        def _to_tensor(x):
            # transformers 4/5 returns Tensor directly; newer returns BaseModelOutputWithPooling
            if isinstance(x, torch.Tensor):
                return x
            if hasattr(x, "pooler_output"):
                return x.pooler_output
            if hasattr(x, "image_embeds"):
                return x.image_embeds
            if hasattr(x, "text_embeds"):
                return x.text_embeds
            # fallback for tuple/list
            try:
                return x[0]
            except Exception:
                return x  # type: ignore

        embs = []
        with torch.no_grad():
            for label in self._labels:
                prompts = PROMPT_BANK[label]
                inputs = self.processor(text=prompts, return_tensors="pt", padding=True, truncation=True).to(self.device)
                tf = self.model.get_text_features(**inputs)
                tf = _to_tensor(tf)
                tf = tf / tf.norm(dim=-1, keepdim=True)
                mean = tf.mean(dim=0)
                mean = mean / mean.norm()
                embs.append(mean)
        self._text_embeds = torch.stack(embs)  # [num_classes, dim]

    def predict(self, image_path: str | Path, temperature: float | None = None) -> Dict:
        """Run zero-shot on a single image path."""
        self.load()
        assert self.model is not None and self.processor is not None and self._text_embeds is not None
        img = Image.open(image_path).convert("RGB")
        inputs = self.processor(images=img, return_tensors="pt").to(self.device)
        with torch.no_grad():
            img_feat = self.model.get_image_features(**inputs)
            if not isinstance(img_feat, torch.Tensor):
                if hasattr(img_feat, "pooler_output"):
                    img_feat = img_feat.pooler_output
                elif hasattr(img_feat, "image_embeds"):
                    img_feat = img_feat.image_embeds
                else:
                    try:
                        img_feat = img_feat[0]
                    except Exception:
                        pass
            img_feat = img_feat / img_feat.norm(dim=-1, keepdim=True)  # [1, dim]
            # logit_scale is learned; use it if temperature not forced
            logit_scale = self.model.logit_scale.exp() if temperature is None else torch.tensor(1.0 / temperature, device=self.device)
            logits = (img_feat @ self._text_embeds.T) * logit_scale  # [1, num_classes]
            probs = logits.softmax(dim=-1)[0]  # [num_classes]
        per_class = {label: float(probs[i].item()) for i, label in enumerate(self._labels)}
        clean_prob = per_class.get("clean", 0.0)
        # verdict: damaged only when a damage class clearly beats clean (conservative; CLIP overflags)
        best_damage_label = max((l for l in self._labels if l != "clean"), key=lambda l: per_class[l])
        best_damage_prob = per_class[best_damage_label]
        # conservative: require best damage > 0.30 AND > 1.8 * clean (avoids 60% FP seen in NIM pilot)
        verdict = "damaged" if best_damage_prob > 0.30 and best_damage_prob > 1.8 * clean_prob else "clean"
        # score = confidence in the verdict
        score = best_damage_prob if verdict == "damaged" else clean_prob
        return {
            "verdict": verdict,
            "score": round(score, 4),
            "clean_prob": round(clean_prob, 4),
            "best_damage_class": best_damage_label,
            "best_damage_prob": round(best_damage_prob, 4),
            "per_class": {k: round(v, 4) for k, v in per_class.items()},
        }

    def predict_batch(self, image_paths: list[str | Path]) -> list[Dict]:
        return [self.predict(p) for p in image_paths]


if __name__ == "__main__":
    import argparse
    import json

    ap = argparse.ArgumentParser(description="CLIP fallback zero-shot")
    ap.add_argument("--image", required=True, help="image path or folder")
    ap.add_argument("--model", default=DEFAULT_MODEL)
    args = ap.parse_args()
    fb = CLIPFallback(model_name=args.model)
    p = Path(args.image)
    paths = [p] if p.is_file() else sorted([x for x in p.iterdir() if x.suffix.lower() in (".jpg", ".jpeg", ".png")])
    for pp in paths:
        r = fb.predict(pp)
        print(f"{pp.name}: {r['verdict']} ({r['score']:.3f}) best={r['best_damage_class']} clean={r['clean_prob']:.3f}")
        print("  per_class:", json.dumps(r["per_class"], indent=2))
