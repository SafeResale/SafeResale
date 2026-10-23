"""Decision engine — hard stops + bands + badge per 02-requirements.md R-DECISION."""

def hard_stops(listing: dict, images: list[dict], detections: list[dict]) -> list[dict]:
    stops = []
    # missing required angles: the six canonical faces are mandatory, the two
    # 45° angles are optional bonus coverage (front_45 / back_45 do not count
    # towards the requirement, so a 6-angle capture is complete).
    required_angles = {"front", "back", "left", "right", "top", "bottom"}
    angles = {img.get("angle") for img in images}
    covered = angles & required_angles
    if len(covered) < len(required_angles):
        stops.append({"code": "HARD_MISSING_ANGLES", "level": "danger", "message": f"Only {len(covered)}/6 required angles captured (45° optional)"})
    # duplicate photos across angles: only byte-identical frames (same sha256
    # content hash) are real duplicates. Different sides/angles never collide,
    # so this cannot false-positive on a full 8-angle capture set.
    seen = set()
    for img in images:
        h = img.get("sha256")
        if not h:
            continue
        if h in seen:
            stops.append({"code": "HARD_DUPLICATE_IMAGES", "level": "danger", "message": "Duplicate images detected"})
            break
        seen.add(h)
    # critical defect (water_damage high confidence)
    for d in detections:
        if d.get("class") == "water_damage" and float(d.get("confidence",0)) > 0.7:
            stops.append({"code": "HARD_CRITICAL_DEFECT", "level": "danger", "message": "Critical water damage detected"})
            break
    # invalid metadata: price 0 or year out of range
    price = listing.get("price")
    if price is not None and (float(price) <= 0 or float(price) > 10_000_000):
        stops.append({"code": "HARD_INVALID_PRICE", "level": "danger", "message": "Price out of sane bounds"})
    year = listing.get("attributes", {}).get("year") or listing.get("year")
    if year is not None:
        try:
            y = int(year)
            if y < 1990 or y > 2030:
                stops.append({"code": "HARD_INVALID_YEAR", "level": "danger", "message": f"Year {y} invalid"})
        except: pass
    return stops

def badge_for(decision_status: str, listing_status: str) -> str:
    if decision_status == "approved" and listing_status == "published":
        return "verified"
    if decision_status == "review":
        return "review_passed"
    if listing_status == "inspection_pending":
        return "inspection_pending"
    return "restricted"

def decide_with_stops(risk: dict, listing: dict, images: list[dict], detections: list[dict], diagnostic: dict | None = None) -> dict:
    from app.services.risk import decide as risk_decide
    from app.services.item_identification import identify_item
    stops = hard_stops(listing, images, detections)
    if stops:
        stop_msgs = [s.get("message") for s in stops if s.get("message")]
        return {
            "status": "blocked",
            "reason_code": stops[0]["code"],
            "reasons": stops,
            "hard_stops": stops,
            "review_reasons": stop_msgs or ["Listing blocked due to safety or quality hard stops."],
            "actionable_tips": ["Please resolve the flagged hard stops to proceed."],
        }
    base = dict(risk_decide(risk))
    base["hard_stops"] = stops

    # Generate transparent human-readable explanations of why the listing is in review
    review_reasons = []
    actionable_tips = []

    phys = risk.get("physical_risk") or {}
    diag = risk.get("diagnostic_risk") or {}
    behav = risk.get("behavioral_risk") or {}

    # 1. Device Hardware Mismatch check (e.g. user listed Samsung F15, but diagnosis ran on Samsung Galaxy A35)
    has_device_mismatch = False
    if diagnostic and isinstance(diagnostic, dict) and diagnostic.get("device"):
        try:
            ident = identify_item(listing, images=images, detections=detections, diagnostic=diagnostic)
            if ident.get("device_mismatch"):
                has_device_mismatch = True
                mismatch_info = ident.get("device_mismatch_detail") or {}
                decl = mismatch_info.get("declared_device") or listing.get("title") or "declared model"
                diag_dev = mismatch_info.get("diagnosed_device") or "different model"
                msg = mismatch_info.get("message") or f"Device mismatch: Listing specifies '{decl}', but diagnostic test results were submitted from a '{diag_dev}'."
                review_reasons.append(msg)
                actionable_tips.append(f"Run the SafeResale Diagnosis app on the actual {decl} to submit authentic test results, or update your listing title and model to {diag_dev}.")
        except Exception:
            pass

    # 2. Price anomaly check
    if behav.get("score", 0) >= 35 or "price_anomaly" in behav.get("basis", []):
        price = listing.get("price")
        title = listing.get("title") or "this device"
        if price:
            review_reasons.append(f"Listed price (₹{price:,.0f}) is significantly higher than typical market rates for {title}.")
        else:
            review_reasons.append("Listed price deviates significantly from typical market rates.")
        actionable_tips.append("Adjust price closer to market value to lower behavioral risk.")

    # 3. Diagnostic checks
    if diag.get("score", 0) >= 25 or diag.get("basis"):
        diag_items = []
        for b in diag.get("basis", []):
            if b == "touch":
                diag_items.append("Touch screen test was skipped (0/9 cells touched)")
            elif b == "wifi":
                diag_items.append("Wi-Fi was disconnected during hardware testing")
            elif b in ("battery", "battery_health"):
                diag_items.append("Battery health check failed")
            elif b == "camera":
                diag_items.append("Camera test failed")
            elif b == "skipped":
                diag_items.append("Hardware diagnostics were skipped")
            elif b != "no_tests":
                diag_items.append(f"{b} check failed")
        if diag_items:
            review_reasons.append(f"Hardware diagnostics incomplete: {'; '.join(diag_items)}.")
            actionable_tips.append("Re-running hardware diagnostics on Wi-Fi and completing the touch grid can resolve hardware flags.")

    # 4. Physical / visual defects
    if phys.get("score", 0) >= 35:
        defects = [d for d in phys.get("basis", []) if d != "no_defects"]
        if defects:
            review_reasons.append(f"Visual analysis identified potential cosmetic defects: {', '.join(defects)}.")

    # Device mismatch forces review even if composite risk was somehow low
    if has_device_mismatch:
        base["status"] = "review"
        base["reason_code"] = "REVIEW_DEVICE_MISMATCH"

    if base.get("status") == "review":
        if not review_reasons:
            s = risk.get("adjusted_score", 0)
            review_reasons.append(f"Composite risk score ({s}/100) requires verification review before public listing.")
        actionable_tips.append("A SafeResale moderator will review and approve your listing shortly.")
        base["review_reasons"] = review_reasons
        base["actionable_tips"] = actionable_tips
        base["reasons"] = [{"code": "review_factor", "level": "warn", "message": r} for r in review_reasons]
    elif base.get("status") == "approved":
        base["review_reasons"] = []
        base["actionable_tips"] = ["Listing verified and ready for publication."]

    return base
