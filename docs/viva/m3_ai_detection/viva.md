# Viva — M3: AI-Generated-Image Detection

**Owner:** rahulpandiyan + veereshkp · `docs/08-ml-plan.md` §2/M3, §3 · Module: `ml/m3_authenticity/`
**Role of M3 (01-prd.md:77):** "Authenticity gate — images flagged as AI-generated or unverifiable are
sent to review and are never shown to buyers without human clearance." Live-capture-only listing photos
(01-prd.md:69) mean a genuine listing should never contain AI-generated imagery — this model catches it.

This file is your viva talking-track: what the model does, how it works, what we built, what we
measured, and the problems we hit. Keep it accurate — only report numbers we actually produced
(academic-integrity rule, docs/08-ml-plan.md §1.4).

---

## 1. What is M3, in one sentence

M3 is a binary image classifier that looks at a single listing photo and decides whether that photo
was **AI-generated** or a **real/human-captured** photo, so the backend can block fake imagery before
a listing is published.

## 2. Why we need it (the problem)

The biggest fraud vector on classifieds is **fake/non-existent items** (docs/11-competitor-analysis.md F2):
scammers lift stock photos, download images from the internet, or generate convincing product shots with
AI tools, then list items that don't exist. Offline buyers can't reverse-image-search or verify. So before
a listing reaches buyers, we run every photo through an authenticity gate. If a photo is AI-generated or
unverifiable, it goes to manual review and is never shown without human clearance.

> Why binary "AI vs human" and not "which AI made it"? For the gate we only need one decision:
> "was this taken live by the seller (trust it) or is it synthetic/downloaded (review it)". That's a
> 2-class problem and the data/public models for it are strong (~98% accuracy). Detecting the specific
> generator is a different, harder, faster-drifting problem — out of scope for v1.

## 3. How the model works (the core)

M3 is a **Vision Transformer (ViT)** image classifier — specifically
`google/vit-base-patch16-224-in21k` fine-tuned on an AI-vs-human dataset. We use the public checkpoint
`dima806/ai_vs_human_generated_image_detection` (Apache-2.0, 85.8M parameters).

Why ViT for this task:
- **Attention instead of convolution.** ViT splits the image into fixed patches (16×16) and runs a
  Transformer encoder over them. It can attend to *global* relationships — texture consistency across the
  whole frame, uniform lighting, anatomical/factual oddities — which is exactly where classical and
  GAN-image detectors tend to look. Many synthetic images have small detached artifacts (weird hands,
  text, seams) that benefit from long-range attention.
- **Pre-trained + fine-tuned.** It starts from a model pre-trained on 21k ImageNet classes, then is
  fine-tuned on millions of AI-vs-human labeled images. Transfer learning means we get high accuracy
  without training from scratch (we do not have ourselves a large AI-vs-human dataset).

The 3 steps at inference:
1. **Preprocess** — resize centre-crop to **224×224**, normalize to ViT's expected pixel stats.
2. **Tokenize visually** — image becomes 196 patches (14×14 grid); each patch projected to a vector,
   plus a special `[CLS]` token.
3. **Classify** — the Transformer stack processes the patch sequence; the `[CLS]` head outputs two
   logits → softmax → `P(ai-generated)` and `P(human)`.

### 3.1 Input / output contract

```
input : one or more image paths  (single photo per call, batched by caller)
output: per image →
        label          human | ai-generated | ambiguous
        ai_generated_prob    float 0..1
        human_prob           float 0..1
        confidence           max(p_ai, p_human)
        threshold            decision threshold used
        model_version
```

We added an **`ambiguous` band** (01-prd.md:77 "or unverifiable"): if `P(ai)` lands within
`threshold - 0.02` of the threshold we refuse a hard call and report "ambiguous", which the backend
sends to review rather than trusting it as human.

## 4. What we built

New module `ml/m3_authenticity/provider.py`:

| Component | Purpose |
|---|---|
| `AuthenticityProvider` (Protocol) | Interface so the backend can swap stub ↔ real via config, exactly parallel to `VisionProvider` (docs/03-architecture.md §5) |
| `StubAuthenticityProvider` | Deterministic, seeded, `simulated()==True`. **Default** so the pipeline/CI stays green until real weights are set. |
| `RealAuthenticityProvider` | Loads the ViT checkpoint lazily; `simulated()==False`. |
| `provider_for(simulated, ...)` | Factory: `AUTHENTICITY_PROVIDER=stub|real` |
| CLI (`provider.py <img|folder>` ) | Run the model over files / a folder, print table, optional `--json` report |

Design decisions consistent with the repo:
- **Mirrors the vision provider pattern** (`VISION_PROVIDER=stub|real`) so integration into
  `services/authenticity.py` is a config change, no code change (docs/03-architecture.md:188).
- **Lazy load**: the model is downloaded (343MB) and loaded on first use, so importing the module or
  using the stub costs nothing — important for tests/CI and the 7.6GB WSL RAM cap.
- **Not a new training run**: we did not have our own large labeled AI-vs-human set, so v1 reuses a
  maintained public fine-tuned checkpoint (documented + licensed). M2/M3 "fine-tuned classifier"
  per docs/08-ml-plan.md §2 — this IS the fine-tuned classifier.

## 5. What we measured (verified, not claimed)

Ran the **real** provider on images we had ground truth for:

| Image | Ground truth | Prediction | Confidence |
|---|---|---|---|
| `samsung/frontside.jpg` | real phone photo | **human** | 0.99 |
| `motto/frontscreen.jpg` | real phone photo | **human** | 0.99 |
| `vivo/back.jpg` | real phone photo | **human** | 0.99 |
| `samsung/Calculated SGPA vs Final Marks Card.png` | AI-generated | **ai-generated** | 0.87 |

So on our verification set the model correctly accepted real live-captured photos (≈99% human
confidence) and correctly flagged a generated image (87% AI). The model also reports ~98% accuracy on
its own large public benchmark.

> Honest limitation — **concept drift** (from the model card): the checkpoint was trained on data
> collected ~1 year ago. AI image generators improve fast, so on brand-new generation styles the model
> may degrade. The card suggests either retraining on fresh labeled data or lowering the detection
> threshold (0.5 → 0.1). Our `--threshold` flag already makes this a one-line change. We do not claim
> current-world-field accuracy until we re-measure.

## 6. Problems we faced (and how we solved them)

1. **No backend code in the repo to "drop it into."** The repo contained only M1 + docs; the FastAPI
   `services/...` layer from docs/03-architecture.md isn't present here. → We built the provider as a
   self-contained, interface-compliant module + CLI so it drops into the backend later as a config swap.
2. **Model download size.** `model.safetensors` is **343MB**. → Cached under `M1_WORK_ROOT/.hf`
   (`HF_HOME`), lazy-loaded, and only the real provider pulls it. RAM stayed well under the 7.6GB WSL cap.
3. **`transformers` not installed.** The M1 venv had torch but no HF transformers. → `pip install
   transformers safetensors` (tf 5.16.1); verified import + inference.
4. **`device_map="auto"` broke loading.** In the provider I first used `device_map="auto"`, which
   requires the `accelerate` package and threw at load. The earlier direct pipeline test worked without
   it. → Dropped `device_map`, letting the pipeline auto-place the model. (Symptom: the module loaded in
   earlier verification but failed through my provider — a good reminder to always test the *integrated*
   code path, not just the ad-hoc script.)
5. **Unlabelled data test in M1 is not M3.** We must not confuse M3's AI-detection with M1's defect
   detection — M3 judges whether the photo is *synthetic*, totally orthogonal to whether the phone has
   a scratch.

## 7. Where it fits in the pipeline

```
capture (live-only) → upload → run-vision (M1 defects) → authenticity (M3) → diagnostics (M2/M4..M8)
                                                    ↓
                              AI / ambiguous → manual review (never shown to buyers)
```
- `AUTHENTICITY_PROVIDER=stub`            → deterministic, `simulated:true`  (CI/tests, default)
- `AUTHENTICITY_PROVIDER=real`            → ViT classifier, `simulated:false` (production)

## 8. Quick answers for a panel

- **Q: Why does a classifieds app need AI-detection?** → Stolen/AI product photos are the #1 enabler of
  fake listings (no real item exists). Live-capture already reduces it; M3 is the automated gate for the
  photos that do arrive (01-prd.md:69,77).
- **Q: How do you know a ViT is better than a CNN here?** → We used the established ViT fine-tune that is
  pre-trained + 98%-accurate on a large benchmark; its global attention matches the kind of artifact cues
  synthetic images leave. (We did not run our own CNN-vs-ViT training ablation for M3 — that was an M1
  deliverable. Be honest about that.)
- **Q: What are the failure modes?** → Concept drift on newer generators; a very convincing real photo
  misread as AI (sends to review — safe failure, just friction); a very convincing AI photo missed
  (bad — hence drift mitigation + human review for `ambiguous`).
- **Q: `simulated()` and the stub?** → Same pattern as M1: stub is deterministic + labeled so the whole
  pipeline works offline and in CI; we swap to `real` via env with zero code change.

## 9. Definition of done (M3)

- [x] Model downloaded + cached; real inference verified on human + AI images (measured above).
- [x] Provider module with `AuthenticityProvider` Protocol, stub + real, factory by env, CLI, `--json`.
- [ ] Backend integration: `services/authenticity.py` + `AUTHENTICITY_PROVIDER=` env wiring (blocked:
      backend code not present in this repo yet).
- [ ] Re-measure on a fresh labeled set / document drift threshold before production.
