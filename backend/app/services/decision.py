"""Decision engine — hard stops + bands + badge per 02-requirements.md R-DECISION."""

def hard_stops(listing: dict, images: list[dict], detections: list[dict]) -> list[dict]:
    stops = []
    # missing required angles (need 8)
    angles = {img.get("angle") for img in images}
    if len(angles) < 8:
        stops.append({"code": "HARD_MISSING_ANGLES", "level": "danger", "message": f"Only {len(angles)}/8 angles captured"})
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

def decide_with_stops(risk: dict, listing: dict, images: list[dict], detections: list[dict]) -> dict:
    from app.services.risk import decide as risk_decide
    stops = hard_stops(listing, images, detections)
    if stops:
        return {"status": "blocked", "reason_code": stops[0]["code"], "reasons": stops, "hard_stops": stops}
    base = risk_decide(risk)
    base["hard_stops"] = stops
    return base
