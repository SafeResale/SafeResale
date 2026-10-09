"""Unified per-listing summary of what each SafeResale ML model says.

Four photo models feed the verification pipeline and can all be surfaced to the
buyer on the product page:

* M1 — Defect Detection      (dent, scratch, crack, …)
* M2 — Condition Classification (Good / Moderate / Defective)
* M3 — Authenticity          (AI-generated vs human-captured photo)
* M6 — Image Quality         (blur / exposure / glare, server OpenCV check)

Each entry is a small, human-readable verdict plus a couple of concrete items
(label/value) so the app renders it without any ML logic of its own.
"""
from bson import ObjectId

from app.core.db import get_db


def _pct(x: float) -> str:
    return f"{round(float(x) * 100)}%"


def _simulated(rows: list, default: bool = True) -> bool:
    if not rows:
        return default
    return bool(rows[0].get("simulated", default))


def _model(rows: list, default: str = "stub-1.0") -> str:
    if not rows:
        return default
    return str(rows[0].get("model_version") or default)


async def models_report(listing_id: str) -> dict:
    """Return {m1, m2, m3, m6} verdicts from persisted pipeline data.

    Read-only: never recomputes or writes anything, so it is safe on any
    detail/list path. Empty listing -> all models report "no data yet".
    """
    db = get_db()
    try:
        oid = ObjectId(listing_id)
    except Exception:
        return {}
    images = await db.listing_images.find({"listing_id": oid}).to_list(100)
    dets = await db.detections.find({"listing_id": oid}).to_list(100)
    cond = await db.condition_predictions.find_one({"listing_id": oid}, sort=[("created_at", -1)])
    auths = await db.authenticity.find({"listing_id": oid}).to_list(100)

    flat: list[dict] = []
    for d in dets:
        if not isinstance(d, dict):
            continue
        if "class" in d:
            flat.append(d)
        else:
            flat.extend(d.get("detections", []) or [])

    # ---- M1 defect detection ----
    findings = sorted(
        ({"label": str(d.get("class", "")), "confidence": float(d.get("confidence") or d.get("conf") or 0)} for d in flat),
        key=lambda f: f["confidence"],
        reverse=True,
    )
    if findings:
        total = len(findings)
        top = ", ".join(f["label"] for f in findings[:4])
        m1_verdict = f"{total} defect{'s' if total != 1 else ''} found — {top}"
    else:
        m1_verdict = "No defects detected"
    m1 = {
        "id": "m1",
        "name": "Defect Detection",
        "model": _model(flat),
        "simulated": _simulated(flat),
        "verdict": m1_verdict,
        "ok": not findings,
        "items": [{"label": f["label"], "value": _pct(f["confidence"])} for f in findings[:8]],
    }

    # ---- M2 condition class ----
    if not cond:
        m2 = {
            "id": "m2",
            "name": "Condition Classification",
            "model": "stub-1.0",
            "simulated": True,
            "verdict": "No condition score yet",
            "ok": True,
            "items": [],
        }
    else:
        label = str(cond.get("label") or cond.get("class") or "Good")
        probs = cond.get("probabilities") or cond.get("class_probabilities") or {}
        probs = {str(k): float(v or 0) for k, v in probs.items() if isinstance(v, (int, float))}
        top_label, top_prob = (max(probs, key=probs.get), probs[max(probs, key=probs.get)]) if probs else (label, 1.0)
        m2 = {
            "id": "m2",
            "name": "Condition Classification",
            "model": str(cond.get("model_version") or "stub-1.0"),
            "simulated": bool(cond.get("simulated", True)),
            "verdict": f"{label} ({_pct(top_prob)})",
            "ok": str(label).lower() in ("good", "good_condition"),
            "items": [{"label": str(k), "value": _pct(v)} for k, v in sorted(probs.items(), key=lambda kv: -kv[1])[:3]],
        }

    # ---- M3 authenticity ----
    human = sum(1 for a in auths if a.get("label") == "human")
    ai_gen = sum(1 for a in auths if a.get("label") == "ai-generated")
    amb = sum(1 for a in auths if a.get("label") == "ambiguous")
    if not auths:
        m3 = {
            "id": "m3",
            "name": "Authenticity (AI vs Human)",
            "model": "stub-v1",
            "simulated": True,
            "verdict": "Not analysed yet",
            "ok": True,
            "items": [],
        }
    else:
        max_ai = max((float(a.get("ai_generated_prob", 0) or 0) for a in auths), default=0.0)
        at = auths[0]
        m3 = {
            "id": "m3",
            "name": "Authenticity (AI vs Human)",
            "model": str(at.get("model_version") or "stub-v1"),
            "simulated": _simulated(auths),
            "verdict": f"{human} human · {ai_gen} AI · {amb} ambiguous",
            "ok": ai_gen == 0,
            "items": [{"label": "highest AI probability", "value": _pct(max_ai)}],
        }

    # ---- M6 image quality ----
    raw_qas = [img.get("server_quality") or {} for img in images if img.get("server_quality")]
    qas = [q for q in raw_qas if "error" not in q and isinstance(q.get("quality_score"), (int, float))]
    passed = sum(1 for q in qas if q.get("passed"))
    if not qas:
        if raw_qas:
            m6 = {
                "id": "m6",
                "name": "Image Quality",
                "model": "opencv-m6",
                "simulated": False,
                "verdict": "Quality check unavailable",
                "ok": True,
                "items": [],
            }
        else:
            m6 = {
                "id": "m6",
                "name": "Image Quality",
                "model": "opencv-m6",
                "simulated": False,
                "verdict": "No photos analysed yet",
                "ok": True,
                "items": [],
            }
    else:
        scores = [int(q.get("quality_score", 0) or 0) for q in qas]
        blurs = [float(q.get("details", {}).get("blur_score", 0) or 0) for q in qas]
        m6 = {
            "id": "m6",
            "name": "Image Quality",
            "model": "opencv-m6",
            "simulated": False,
            "verdict": f"{passed}/{len(qas)} photos passed quality check",
            "ok": passed == len(qas),
            "items": [
                {"label": "avg quality", "value": f"{sum(scores) // max(1, len(scores))}/100"},
                {"label": "avg sharpness", "value": str(round(sum(blurs) / len(blurs)))},
            ],
        }

    return {"m1": m1, "m2": m2, "m3": m3, "m6": m6}