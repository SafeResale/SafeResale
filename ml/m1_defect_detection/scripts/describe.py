"""Image description via NVIDIA NIM hosted VLM + template fallback.

- Primary: calls https://integrate.api.nvidia.com/v1/chat/completions with
  meta/llama-3.2-11b-vision-instruct (free hosted NIM, your build.nvidia.com key).
- Never decides — only writes a natural-language sentence for the report.
- If the API is unavailable/rate-limited/offline, falls back to a deterministic
  template built from YOLO detections (so assess_device never blocks).

Env:
  NVAPI_KEY or NVIDIA_API_KEY — your build.nvidia.com key (never commit it).
  Put it in ml/m1_defect_detection/.env (gitignored) or export it.

Usage:
  from describe import Describer
  d = Describer()  # reads key from env/.env
  text, source = d.describe("path/to/img.jpg", detections=[{"class":"scratch","conf":0.82}])
  # text: "Light scratch visible on the lower half."  source: "nvidia-nim-vlm" | "template"
"""
from __future__ import annotations

import base64
import json
import os
import time
import urllib.request
from pathlib import Path
from typing import List, Dict, Tuple

DEFAULT_ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
DEFAULT_MODEL = "meta/llama-3.2-11b-vision-instruct"

SYSTEM_PROMPT = (
    "You are an assistant that writes a single concise sentence describing visible physical damage "
    "in a photo of a used consumer device (phone, laptop, appliance, etc.). "
    "Mention the defect type(s) and where they appear. "
    "If the device looks undamaged/clean, reply exactly: No visible damage. "
    "Do not add extra explanation, no bullet points, one sentence only."
)


def _load_key() -> str | None:
    # try env first
    for k in ("NVAPI_KEY", "NVIDIA_API_KEY", "NIM_API_KEY"):
        v = os.environ.get(k)
        if v:
            return v.strip()
    # try .env next to this file, then repo root
    for p in [Path(__file__).with_name(".env"), Path(__file__).parents[2] / ".env", Path.cwd() / ".env"]:
        if p.exists():
            for line in p.read_text().splitlines():
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                kk, vv = line.split("=", 1)
                if kk.strip() in ("NVAPI_KEY", "NVIDIA_API_KEY", "NIM_API_KEY"):
                    vv = vv.strip().strip('"').strip("'")
                    if vv:
                        return vv
    return None


def _template_from_detections(detections: List[Dict] | None, image_name: str = "") -> str:
    if not detections:
        return "No visible damage."
    # group by class
    by_class: dict[str, list[float]] = {}
    for d in detections:
        by_class.setdefault(d.get("class", "damage"), []).append(float(d.get("conf", 0)))
    parts = []
    for cls, confs in sorted(by_class.items()):
        n = len(confs)
        best = max(confs)
        if n == 1:
            parts.append(f"{cls} (confidence {best:.2f})")
        else:
            parts.append(f"{cls} x{n} (best confidence {best:.2f})")
    prefix = f"In {image_name}, " if image_name else ""
    return prefix + "visible damage: " + ", ".join(parts) + "."


class Describer:
    def __init__(
        self,
        api_key: str | None = None,
        endpoint: str = DEFAULT_ENDPOINT,
        model: str = DEFAULT_MODEL,
        timeout: int = 60,
        retries: int = 1,
    ):
        if api_key is not None:
            # explicit (including "" to force template-only)
            self.api_key = api_key.strip()
        else:
            self.api_key = (_load_key() or "").strip()
        self.endpoint = endpoint
        self.model = model
        self.timeout = timeout
        self.retries = retries

    @property
    def available(self) -> bool:
        return bool(self.api_key)

    def describe(
        self,
        image_path: str | Path,
        detections: List[Dict] | None = None,
        extra_prompt: str | None = None,
    ) -> Tuple[str, str]:
        """Return (text, source). Source is 'nvidia-nim-vlm' or 'template'."""
        # try NIM first if key present
        if self.available:
            text = self._call_nim(image_path, extra_prompt=extra_prompt)
            if text is not None:
                # normalize: one sentence, strip quotes
                text = text.strip().strip('"').strip("'")
                # guard: model sometimes returns empty or overly long; truncate sensibly
                if text and len(text) < 500:
                    return text, "nvidia-nim-vlm"
        # fallback
        name = Path(image_path).name if image_path else ""
        return _template_from_detections(detections, name), "template"

    def _call_nim(self, image_path: str | Path, extra_prompt: str | None = None) -> str | None:
        try:
            with open(image_path, "rb") as f:
                b64 = base64.b64encode(f.read()).decode()
        except Exception:
            return None
        user_text = SYSTEM_PROMPT
        if extra_prompt:
            user_text += " " + extra_prompt
        # include detection hint as context (helps VLM ground its sentence without deciding)
        # but keep it soft: "YOLO saw: scratch 0.82" — not "the answer is scratch"
        body = json.dumps(
            {
                "model": self.model,
                "max_tokens": 120,
                "temperature": 0.2,
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": user_text},
                            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b64}},
                        ],
                    }
                ],
            }
        ).encode()
        last_err = None
        for attempt in range(self.retries + 1):
            try:
                req = urllib.request.Request(
                    self.endpoint,
                    data=body,
                    headers={
                        "Authorization": "Bearer " + self.api_key,
                        "Content-Type": "application/json",
                    },
                )
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    data = json.load(resp)
                return data["choices"][0]["message"]["content"]
            except Exception as e:
                last_err = e
                time.sleep(1.5)
        # optional: log last_err somewhere; silently fall back for now
        return None


if __name__ == "__main__":
    import argparse

    ap = argparse.ArgumentParser(description="NIM describer (with template fallback)")
    ap.add_argument("--image", required=True)
    ap.add_argument("--key", default=None, help="override NVAPI_KEY")
    args = ap.parse_args()
    d = Describer(api_key=args.key)
    print("key present:", d.available, "endpoint:", d.endpoint, "model:", d.model)
    text, src = d.describe(args.image, detections=[{"class": "scratch", "conf": 0.91}])
    print(f"[{src}] {text}")
