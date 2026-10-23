"""AI Verification and Reconciliation Layer.

Evaluates evidence produced by each component in the SafeResale pipeline:
- M1 (Defect Detection: scratches, cracks, dents, water damage...)
- M2 (Condition Classification: Good, Moderate, Defective)
- M3 (Authenticity: AI-generated vs Human)
- M6 (Image Quality: Blur, exposure, glare)
- Device Diagnosis (Hardware bench from Diagnosis app)
- Seller declared metadata

Performs deterministic cross-validation:
- Filters low confidence detections (<0.35) or detections on blurry/low-quality photos.
- Cross-references visual screen damage against hardware touch/digitizer diagnostics.
- Detects contradictions (e.g. seller claims pristine vs M1 cracks; or diagnostic reports different device).
- Distinguishes cosmetic/visual damage from operational hardware health.
- NEVER invents hardware status if diagnostics were not run (explicitly states untested).
- Generates a complete, structured final assessment.
"""
from typing import Any, Dict, List, Optional
import time

from app.services.item_identification import identify_item


def reconcile_assessment(
    listing: Dict[str, Any],
    images: Optional[List[Dict[str, Any]]] = None,
    detections: Optional[List[Dict[str, Any]]] = None,
    condition: Optional[Dict[str, Any]] = None,
    authenticity: Optional[List[Dict[str, Any]]] = None,
    quality: Optional[List[Dict[str, Any]]] = None,
    diagnostic: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """Reconcile all visual, metadata, and hardware diagnostic evidence."""
    images = images or []
    detections = detections or []
    authenticity = authenticity or []
    quality = quality or []

    # 1. Product Identity through Evidence-Based Item Identification
    identity = identify_item(listing, images=images, detections=detections, diagnostic=diagnostic)

    # 2. Image Quality & Authenticity Gates
    unreliable_image_ids = set()
    for q in quality:
        if isinstance(q, dict) and q.get("passed") is False:
            img_id = str(q.get("image_id") or q.get("image") or "")
            if img_id:
                unreliable_image_ids.add(img_id)

    ai_generated_images = []
    for a in authenticity:
        if isinstance(a, dict) and (a.get("label") == "ai-generated" or float(a.get("ai_generated_prob", 0)) > 0.70):
            ai_generated_images.append(a.get("image") or "photo")

    # 3. Visual Defect Filtering & Verification (M1)
    # Thresholds:
    # >= 0.50 with good image quality -> verified
    # 0.35 - 0.49 -> uncertain
    # < 0.35 -> dismissed as noise
    verified_defects: List[Dict[str, Any]] = []
    uncertain_defects: List[Dict[str, Any]] = []
    dismissed_defects: List[Dict[str, Any]] = []

    flat_dets: List[Dict[str, Any]] = []
    for d in detections:
        if not isinstance(d, dict):
            continue
        if "class" in d:
            flat_dets.append(d)
        else:
            flat_dets.extend(d.get("detections", []) or [])

    for det in flat_dets:
        cls_name = str(det.get("class") or det.get("label") or "defect").lower()
        conf = float(det.get("confidence") or det.get("conf") or 0.0)
        img_ref = str(det.get("image_id") or det.get("image") or "")

        defect_record = {
            "label": cls_name,
            "confidence": round(conf, 3),
            "image": img_ref,
            "box": det.get("box") or det.get("bbox"),
        }

        if img_ref in unreliable_image_ids:
            defect_record["reason"] = "Detected on blurry or low-quality frame; requires confirmation."
            uncertain_defects.append(defect_record)
        elif conf >= 0.50:
            verified_defects.append(defect_record)
        elif conf >= 0.35:
            defect_record["reason"] = f"Marginal confidence ({round(conf*100)}%)."
            uncertain_defects.append(defect_record)
        else:
            defect_record["reason"] = f"Below confidence threshold ({round(conf*100)}%)."
            dismissed_defects.append(defect_record)

    # 4. Device Hardware Health & Reconciliation with Diagnostics
    device_health: Dict[str, Any] = {}
    discrepancies: List[str] = list(identity.get("discrepancies", []))
    hardware_tested = diagnostic is not None and not diagnostic.get("error") and diagnostic.get("score") is not None

    if hardware_tested:
        diag_score = int(diagnostic.get("score", 0))
        tests = diagnostic.get("tests") or []
        passed_tests = [t.get("id") or t.get("name") for t in tests if isinstance(t, dict) and t.get("status") in ("passed", "pass")]
        failed_tests = [t.get("id") or t.get("name") for t in tests if isinstance(t, dict) and t.get("status") in ("failed", "fail")]

        # Functional vs Cosmetic Screen Reconciliation
        screen_crack_visual = any(d["label"] in ("crack", "screen_damage", "broken_screen") for d in verified_defects)
        touch_test_passed = "touch" in passed_tests or "display" in passed_tests

        if screen_crack_visual and touch_test_passed:
            reconciliation_note = (
                "Visual surface damage (crack/scratch) detected on screen exterior, "
                "but internal display and touch digitizer passed hardware diagnostics (digitizer and touch display hardware are functionally intact)."
            )
        elif screen_crack_visual and not touch_test_passed:
            reconciliation_note = "Confirmed screen defect: visual crack corroborated by touch/display hardware failure."
        else:
            reconciliation_note = "Display visual condition and hardware diagnostics are consistent."

        device_health = {
            "diagnostics_performed": True,
            "score": diag_score,
            "passed_tests": passed_tests,
            "failed_tests": failed_tests,
            "hardware_status": "fully_functional" if diag_score >= 80 else ("functional_with_issues" if diag_score >= 50 else "defective"),
            "display_reconciliation": reconciliation_note,
            "device_telemetry": diagnostic.get("device") or {},
        }
    else:
        # Strictly explicit when diagnosis is missing (never fabricate)
        device_health = {
            "diagnostics_performed": False,
            "score": None,
            "passed_tests": [],
            "failed_tests": [],
            "status_message": "Device has not been tested. Hardware and internal components have not been independently verified.",
            "hardware_status": "untested",
        }

    # 5. Check Contradictions with Seller Claims
    seller_condition = (listing.get("seller_condition") or listing.get("condition") or "").lower()
    if seller_condition in ("brand_new", "like_new", "flawless"):
        if verified_defects:
            discrepancies.append(f"Seller claimed '{seller_condition}', but visual analysis verified {len(verified_defects)} defects.")
        if hardware_tested and device_health.get("hardware_status") == "defective":
            discrepancies.append(f"Seller claimed '{seller_condition}', but hardware diagnostics revealed failures.")

    if ai_generated_images:
        discrepancies.append(f"Authenticity alert: {len(ai_generated_images)} image(s) flagged as AI-generated.")

    # 6. Unverified Claims
    unverified_claims: List[str] = []
    if not hardware_tested:
        unverified_claims.append("Hardware functionality (battery, sensors, camera, wireless) is unverified.")
    if identity.get("verification_status") != "confirmed_by_diagnostics":
        unverified_claims.append(f"Model identity '{identity.get('model') or 'Unknown'}' is based on seller claim.")
    for note in (listing.get("notes") or "").split("\n"):
        if note.strip():
            unverified_claims.append(f"Seller note: \"{note.strip()}\"")

    # 7. Overall Assessment Synthesis
    brand_str = identity.get("brand") or "Device"
    model_str = identity.get("model") or "unspecified model"
    cat_str = identity.get("category") or "electronics"

    assessment_parts = []
    if identity.get("verification_status") == "confirmed_by_diagnostics":
        assessment_parts.append(f"Verified {brand_str} {model_str} ({cat_str}) confirmed by hardware diagnostic telemetry.")
    else:
        assessment_parts.append(f"{brand_str} {model_str} ({cat_str}) declared by seller.")

    if verified_defects:
        defect_summary = ", ".join(d["label"] for d in verified_defects[:3])
        assessment_parts.append(f"Visual inspection detected {len(verified_defects)} defect(s): {defect_summary}.")
    else:
        assessment_parts.append("Visual inspection found no significant surface defects.")

    if hardware_tested:
        score = device_health.get("score")
        status = device_health.get("hardware_status")
        assessment_parts.append(f"Device health bench scored {score}/100 ({status}).")
    else:
        assessment_parts.append("Hardware diagnostics were not executed; internal components remain untested.")

    if discrepancies:
        assessment_parts.append(f"Discrepancies noted: {'; '.join(discrepancies)}.")

    overall_assessment = " ".join(assessment_parts)

    # 8. Trust Badge Recommendation
    if ai_generated_images or (hardware_tested and device_health.get("hardware_status") == "defective"):
        trust_badge = "restricted"
    elif discrepancies or not hardware_tested or identity.get("needs_clarification"):
        trust_badge = "review_passed" if not verified_defects else "inspection_pending"
    else:
        trust_badge = "verified"

    return {
        "product_identity": identity,
        "visual_condition": {
            "status": "defective" if any(d["label"] in ("crack", "broken_screen") for d in verified_defects) else ("good" if not verified_defects else "moderate"),
            "verified_defects": verified_defects,
            "uncertain_defects": uncertain_defects,
            "dismissed_defects": dismissed_defects,
            "image_quality_passed": len(unreliable_image_ids) == 0,
            "authenticity_status": "ai_flagged" if ai_generated_images else "human_verified",
        },
        "device_health": device_health,
        "device_mismatch": bool(identity.get("device_mismatch")),
        "device_mismatch_detail": identity.get("device_mismatch_detail"),
        "unverified_claims": unverified_claims,
        "discrepancies": discrepancies,
        "needs_clarification": bool(identity.get("needs_clarification") or discrepancies),
        "clarification_prompt": identity.get("clarification_prompt") or (discrepancies[0] if discrepancies else None),
        "overall_assessment": overall_assessment,
        "trust_badge": trust_badge,
        "timestamp": time.time(),
    }
