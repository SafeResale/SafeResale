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
# Covers full 29-class vocabulary: 14 core + 15 extensions (docs/08-ml-plan.md §3.1)
# Prompts are category-aware: vehicle/furniture/appliance variants refer to the
# actual product so CLIP works beyond phones (Inspektlabs, furniture-inspection standards).
PROMPT_BANK: Dict[str, list[str]] = {
    "clean": [
        "a photo of a clean undamaged product with no defects, pristine surface",
        "a flawless device with no damage, perfect condition",
        "a close-up photo of a pristine product with no scratches or cracks",
        "a clean product with only minor fingerprints or smudges from handling, no damage",
        "a product with light handling marks on the surface, otherwise intact",
        "a hand holding a clean device, dark surface turned off showing reflections, fingerprints and smudges but no damage, intact product",
        "a product surface showing glare, reflections and oily marks, no cracks or dents, undamaged",
        "a close-up of a product held in a hand, glossy surface with light reflections and smudges, undamaged",
    ],
    # core
    "scratch": [
        "a close-up photo of a product with thin hairline scratches on the surface",
        "a car door with visible surface scratches, fine lines on the paint",
        "a device with visible surface scratches, fine lines on the body",
    ],
    "crack": [
        "a photo of a surface with a sharp crack line across it",
        "a device with a clearly visible crack, fractured surface with a line",
        "a car panel with a crack in the bodywork",
    ],
    "dent": [
        "a product body with a deep dent where the metal or plastic is visibly pushed inward, structural deformation",
        "a car door with a dented panel, clear physical indentation and deformed metal",
        "a close-up of a dented edge, obvious dent damage with deformed shape",
    ],
    "screen_damage": [
        "a phone screen visibly cracked and shattered with lines across the display, broken display",
        "a broken display with shattered glass and cracked screen, display damage",
        "a product with a clearly broken screen, cracks and shattered display with visible damage",
    ],
    "glass_damage": [
        "a photo of a product with shattered glass panel, spiderweb cracks on glass",
        "a car windshield with shattered glass, spiderweb cracks on glass",
        "a device with broken glass, shattered and cracked glass surface",
    ],
    "rust": [
        "a close-up photo of rust on metal, reddish brown rust patches",
        "a car body with rust spots on the wheel arch and door edge",
        "a rusty metal surface with reddish rust, bicycle frame with rust",
    ],
    "corrosion": [
        "a photo of corroded metal surface, oxidized flaking",
        "a car underbody with corrosion and oxidation, pitted metal",
        "a device with corrosion and oxidation on metal contacts",
    ],
    "water_damage": [
        "a product that was submerged in water, liquid pooled inside",
        "heavy water damage with moisture and cracking from liquid ingress",
        "a wooden furniture surface with water rings, swelling and discoloration from water damage",
        "a car interior with water damage, flooded footwell and stained upholstery",
    ],
    "camera_damage": [
        "a camera lens with visible damage, scratched and cracked lens element",
        "a phone camera module with damaged lens, blurry cracked camera glass",
    ],
    "port_damage": [
        "a close-up of a damaged charging port, bent pins and debris in the connector",
        "a device port with visible damage, broken USB port",
    ],
    "casing_damage": [
        "a device casing with visible damage, cracked and chipped housing",
        "a product housing with deformed casing, broken outer shell",
    ],
    "body_deformation": [
        "a car body with visible deformation, crumpled panel and misaligned frame",
        "a bicycle frame with bent tube, body deformation from impact",
        "a product body visibly deformed, warped and misshapen from damage",
    ],
    "paint_damage": [
        "a car panel with paint damage, peeling and chipped paint exposing primer",
        "a product with chipped paint, flaking coating and exposed surface",
    ],
    "chip": [
        "a close-up of a paint chip on a surface, small flake missing",
        "a car edge with stone chips, small paint chips on the hood",
        "a device with a chip on the corner, small piece broken off",
    ],
    # extensions — category-specific
    "stain": [
        "a furniture upholstery with visible stain, dark blotch on fabric",
        "a car seat with stained upholstery, spill stain on the surface",
        "a product surface with a stain, discolored blotch from spill",
    ],
    "discoloration": [
        "a product surface with discoloration, faded and yellowed patches",
        "a car paint with discoloration, sun-faded and uneven color",
    ],
    "wear": [
        "a product surface with wear, worn and faded from heavy use",
        "a furniture armrest with worn fabric, threadbare and faded",
        "a car seat with worn leather, cracked and faded from use",
    ],
    "broken_part": [
        "a product with a broken part, snapped and detached component",
        "a furniture chair with a broken leg, snapped wooden part",
        "a car with a broken bumper, detached and hanging part",
    ],
    "missing_part": [
        "a product with a missing part, empty hole where component should be",
        "a car with a missing trim piece, gap where part should be",
        "a furniture drawer with missing handle, empty screw holes",
    ],
    "button_damage": [
        "a gaming controller with damaged buttons, stuck and cracked buttons",
        "a device with broken buttons, missing and jammed keys",
    ],
    "keyboard_damage": [
        "a laptop keyboard with damaged keys, missing and broken keycaps",
        "a keyboard with visible damage, cracked and stuck keys",
    ],
    "hinge_damage": [
        "a laptop hinge with visible damage, broken and loose hinge joint",
        "a furniture cabinet door with broken hinge, sagging and misaligned",
    ],
    "cable_damage": [
        "a cable with visible damage, frayed and exposed wires",
        "a bicycle brake cable with frayed and kinked cable",
        "a device cable with damaged insulation, exposed copper wires",
    ],
    "connector_damage": [
        "a connector with visible damage, bent pins and corrosion",
        "a cable connector with damaged plug, broken connector housing",
    ],
    "tire_damage": [
        "a car tire with visible damage, flat and cracked sidewall, worn tread",
        "a bicycle tire with damage, flat tire and cracked rubber",
        "a tire with deep cuts and bulges, damaged sidewall",
    ],
    "wheel_damage": [
        "a car wheel with damage, bent rim and curb rash, scratched alloy",
        "a bicycle wheel with bent rim, buckled and wobbling wheel",
    ],
    "mirror_damage": [
        "a car side mirror with damage, cracked and hanging mirror housing",
        "a mirror with shattered glass and broken housing",
    ],
    "light_damage": [
        "a car headlight with damage, cracked and foggy lens, broken light housing",
        "a tail light with cracked lens and moisture inside",
    ],
    "bumper_damage": [
        "a car bumper with damage, dented and cracked bumper, scratched and deformed",
        "a bumper with visible deformation, hanging and misaligned",
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
    "paint_damage": "surface",
    "chip": "surface",
    "body_deformation": "structural",
    "camera_damage": "optical",
    "port_damage": "functional",
    "casing_damage": "structural",
    "stain": "cosmetic",
    "discoloration": "cosmetic",
    "wear": "cosmetic",
    "broken_part": "structural",
    "missing_part": "structural",
    "button_damage": "functional",
    "keyboard_damage": "functional",
    "hinge_damage": "structural",
    "cable_damage": "functional",
    "connector_damage": "functional",
    "tire_damage": "mechanical",
    "wheel_damage": "mechanical",
    "mirror_damage": "exterior",
    "light_damage": "exterior",
    "bumper_damage": "structural",
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
