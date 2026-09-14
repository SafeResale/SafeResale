"""Risk engine: 0.4*physical + 0.4*diagnostic + 0.2*behavioral per 01-prd.md:13."""
from typing import Dict, Any

CONFIG_VERSION = "risk-v1"

# Physical: map M1 detections + condition into 0-100
SEVERITY = {
    "scratch": 30, "crack": 70, "dent": 60, "screen_damage": 85,
    "glass_damage": 80, "camera_damage": 90, "port_damage": 55,
    "casing_damage": 50, "body_deformation": 75, "paint_damage": 35,
    "chip": 45, "rust": 60, "corrosion": 65, "water_damage": 95,
}

def physical_risk(detections: list[dict], condition: dict | None) -> Dict[str, Any]:
    if not detections:
        score = 5  # no defects = low risk
        basis = ["no_defects"]
        conf = 0.9 if condition and condition.get("class") == "good" else 0.6
        return {"score": score, "confidence": conf, "basis": basis}
    # worst + mean
    sev_scores = [SEVERITY.get(d.get("class",""), 50) * float(d.get("confidence",0.5)) for d in detections]
    score = min(100, int(max(sev_scores) * 0.6 + (sum(sev_scores)/len(sev_scores)) * 0.4))
    # condition bumps
    if condition and condition.get("class") == "defective":
        score = min(100, score + 15)
    basis = [d.get("class","") for d in detections[:3]]
    return {"score": score, "confidence": 0.8, "basis": basis}

def diagnostic_risk(diagnostics: dict | None) -> Dict[str, Any]:
    if not diagnostics or diagnostics.get("skipped"):
        return {"score": 30, "confidence": 0.5, "basis": ["skipped"], "missing_penalty": 10}
    tests = diagnostics.get("tests", [])
    if not tests:
        return {"score": 25, "confidence": 0.6, "basis": ["no_tests"]}
    failed = [t for t in tests if not t.get("passed")]
    score = min(100, len(failed) * 22)
    # battery health special
    for t in tests:
        if t.get("id") in ("battery_health", "battery_level") and not t.get("passed"):
            score = min(100, score + 10)
    return {"score": score, "confidence": 0.7 if len(tests) >= 6 else 0.5, "basis": [t["id"] for t in failed[:3]]}

def behavioral_risk(signals: list[dict] | None, price_anomaly: dict | None = None) -> Dict[str, Any]:
    if not signals:
        return {"score": 10, "confidence": 0.6, "basis": ["no_signals"]}
    # signals: [{signal, severity}]
    score = min(100, sum(int(s.get("severity", 30)) for s in signals) // max(1, len(signals)) )
    if price_anomaly and price_anomaly.get("deviation_ratio", 0) > 0.5:
        score = min(100, score + 15)
    basis = [s.get("signal","") for s in signals[:3]]
    return {"score": score, "confidence": 0.6, "basis": basis}

def compute_risk(physical: dict, diagnostic: dict, behavioral: dict) -> Dict[str, Any]:
    raw = round(0.4 * physical["score"] + 0.4 * diagnostic["score"] + 0.2 * behavioral["score"], 1)
    # confidence adjustment: penalize low confidence
    min_conf = min(physical.get("confidence",0.5), diagnostic.get("confidence",0.5), behavioral.get("confidence",0.5))
    adjusted = raw + (1 - min_conf) * 5
    adjusted = round(min(100, adjusted), 1)
    return {
        "physical_risk": physical, "diagnostic_risk": diagnostic, "behavioral_risk": behavioral,
        "raw_score": raw, "adjusted_score": adjusted, "config_version": CONFIG_VERSION,
    }

def decide(risk: Dict[str, Any]) -> Dict[str, Any]:
    s = risk["adjusted_score"]
    if s <= 30:
        return {"status": "approved", "reason_code": "APPROVED_LOW_RISK", "reasons": [{"code": "low_risk", "level": "info", "message": f"Risk {s} within approved band"}]}
    if s <= 60:
        return {"status": "review", "reason_code": "REVIEW_MODERATE_RISK", "reasons": [{"code": "moderate_risk", "level": "warn", "message": f"Risk {s} requires manual review"}]}
    return {"status": "blocked", "reason_code": "BLOCKED_HIGH_RISK", "reasons": [{"code": "high_risk", "level": "danger", "message": f"Risk {s} blocked"}]}
