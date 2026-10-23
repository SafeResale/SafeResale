"""Item identification and validation pipeline.

Identifies product category, brand, and model using strict evidence-based rules:
- Category from image cues, user declarations, and hardware telemetry.
- Brand and model ONLY when verified by diagnostic telemetry or supported evidence.
- Distinguishes visually similar models (e.g. iPhone 13 vs 14) and avoids guessing.
- Separates confirmed facts from estimates and unknown information.
- Requests clarification when confidence is low.
- NEVER hallucinates brands, models, specifications, age, or defects.
"""
from typing import Any, Dict, List, Optional
import re

# Known brand catalogs for canonical validation
SUPPORTED_BRANDS = {
    "mobile": {
        "apple": ["iphone 11", "iphone 12", "iphone 13", "iphone 14", "iphone 15", "iphone se", "iphone x", "iphone xr", "iphone xs"],
        "samsung": ["galaxy s21", "galaxy s22", "galaxy s23", "galaxy s24", "galaxy a54", "galaxy z flip", "galaxy z fold", "galaxy note 20"],
        "google": ["pixel 6", "pixel 7", "pixel 8", "pixel 7a", "pixel 8a", "pixel pro"],
        "oneplus": ["oneplus 9", "oneplus 10", "oneplus 11", "oneplus 12", "nord"],
        "xiaomi": ["redmi note", "xiaomi 12", "xiaomi 13", "poco"],
        "motorola": ["moto g", "edge", "razr"],
    },
    "laptop": {
        "apple": ["macbook air", "macbook pro"],
        "dell": ["xps 13", "xps 15", "inspiron", "latitude"],
        "hp": ["spectre", "envy", "pavilion", "elitebook"],
        "lenovo": ["thinkpad", "ideapad", "yoga", "legion"],
        "asus": ["zenbook", "rog", "vivobook", "tuf"],
    },
    "tablet": {
        "apple": ["ipad", "ipad air", "ipad pro", "ipad mini"],
        "samsung": ["galaxy tab s8", "galaxy tab s9", "galaxy tab a"],
        "lenovo": ["tab p11", "tab m10"],
    },
}

# Mapping common model identifiers / codenames to consumer marketing names
KNOWN_MODEL_LOOKUP = {
    # Apple
    "iphone14,5": ("Apple", "iPhone 13"),
    "iphone14,2": ("Apple", "iPhone 13 Pro"),
    "iphone14,3": ("Apple", "iPhone 13 Pro Max"),
    "iphone14,4": ("Apple", "iPhone 13 mini"),
    "iphone14,7": ("Apple", "iPhone 14"),
    "iphone14,8": ("Apple", "iPhone 14 Plus"),
    "iphone15,2": ("Apple", "iPhone 14 Pro"),
    "iphone15,3": ("Apple", "iPhone 14 Pro Max"),
    "iphone15,4": ("Apple", "iPhone 15"),
    "iphone15,5": ("Apple", "iPhone 15 Plus"),
    "iphone16,1": ("Apple", "iPhone 15 Pro"),
    "iphone16,2": ("Apple", "iPhone 15 Pro Max"),
    # Samsung S-Series
    "sm-s901": ("Samsung", "Galaxy S22"),
    "sm-s906": ("Samsung", "Galaxy S22+"),
    "sm-s908": ("Samsung", "Galaxy S22 Ultra"),
    "sm-s911": ("Samsung", "Galaxy S23"),
    "sm-s916": ("Samsung", "Galaxy S23+"),
    "sm-s918": ("Samsung", "Galaxy S23 Ultra"),
    "sm-s921": ("Samsung", "Galaxy S24"),
    "sm-s926": ("Samsung", "Galaxy S24+"),
    "sm-s928": ("Samsung", "Galaxy S24 Ultra"),
    "sm-s711": ("Samsung", "Galaxy S23 FE"),
    "sm-g990": ("Samsung", "Galaxy S21 FE"),
    # Samsung A-Series
    "sm-a356": ("Samsung", "Galaxy A35 5G"),
    "sm-a556": ("Samsung", "Galaxy A55 5G"),
    "sm-a156": ("Samsung", "Galaxy A15 5G"),
    "sm-a546": ("Samsung", "Galaxy A54 5G"),
    "sm-a346": ("Samsung", "Galaxy A34 5G"),
    "sm-a256": ("Samsung", "Galaxy A25 5G"),
    "sm-a146": ("Samsung", "Galaxy A14 5G"),
    "sm-a145": ("Samsung", "Galaxy A14"),
    # Samsung F-Series & M-Series
    "sm-e156": ("Samsung", "Galaxy F15 5G"),
    "sm-f156": ("Samsung", "Galaxy F15 5G"),
    "sm-f546": ("Samsung", "Galaxy F54 5G"),
    "sm-m156": ("Samsung", "Galaxy M15 5G"),
    "sm-m346": ("Samsung", "Galaxy M34 5G"),
    "sm-m546": ("Samsung", "Galaxy M54 5G"),
    # Google
    "pixel 7": ("Google", "Pixel 7"),
    "pixel 7 pro": ("Google", "Pixel 7 Pro"),
    "pixel 8": ("Google", "Pixel 8"),
    "pixel 8 pro": ("Google", "Pixel 8 Pro"),
    "pixel 8a": ("Google", "Pixel 8a"),
}

# Visually ambiguous pairs/groups that cannot be distinguished purely by front/back camera shots
VISUALLY_AMBIGUOUS_GROUPS = [
    {"models": ["iPhone 13", "iPhone 14"], "reason": "Identical physical chassis and diagonal dual-camera bump; requires chipset or diagnostic telemetry to differentiate."},
    {"models": ["Galaxy S22", "Galaxy S23"], "reason": "Very similar front screen and camera island layout; requires processor / model code to differentiate."},
    {"models": ["Pixel 7", "Pixel 8"], "reason": "Visually similar camera visor; requires dimensions/diagnostics to confirm."},
]


def _match_model_identifier(raw: str) -> Optional[tuple[str, str]]:
    if not raw:
        return None
    cleaned = raw.strip().lower()
    for prefix, (brand, model) in sorted(KNOWN_MODEL_LOOKUP.items(), key=lambda kv: len(kv[0]), reverse=True):
        if prefix in cleaned or cleaned in prefix:
            return brand, model
    # Brand extraction fallback if not in KNOWN_MODEL_LOOKUP
    for b in ["samsung", "apple", "google", "oneplus", "xiaomi", "motorola", "oppo", "vivo", "realme"]:
        if b in cleaned:
            clean_m = cleaned.replace(b, "").strip(" -_")
            return b.capitalize(), clean_m.upper() if clean_m.startswith("sm-") else clean_m.title()
    return None


def _normalize_model_name(name: str) -> str:
    """Normalize model string to core letters & digits for fuzzy comparison."""
    if not name:
        return ""
    n = re.sub(r"(?i)\b(samsung|apple|google|oneplus|xiaomi|motorola|galaxy|phone|5g|4g|lte)\b", "", name)
    return re.sub(r"[^a-zA-Z0-9]", "", n).lower()


def identify_item(
    listing: Dict[str, Any],
    images: Optional[List[Dict[str, Any]]] = None,
    detections: Optional[List[Dict[str, Any]]] = None,
    diagnostic: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """Execute evidence-based identification for a listing."""
    images = images or []
    detections = detections or []
    category = (listing.get("category") or "mobile").lower()
    declared_brand = (listing.get("brand") or "").strip()
    declared_model = (listing.get("model") or "").strip()
    title = (listing.get("title") or "").strip()

    # Infer declared brand & model from listing title if not explicitly set in fields
    if not declared_brand or not declared_model:
        t_clean = title.strip()
        for b in ["samsung", "apple", "google", "oneplus", "xiaomi", "redmi", "poco", "motorola", "oppo", "vivo", "realme"]:
            if re.search(r"(?i)\b" + b + r"\b", t_clean):
                if not declared_brand:
                    declared_brand = "Apple" if b in ("apple", "iphone") else ("Google" if b in ("google", "pixel") else ("Motorola" if b in ("motorola", "moto") else ("Xiaomi" if b in ("xiaomi", "redmi", "poco") else b.capitalize())))
                if not declared_model:
                    rem = re.sub(r"(?i)\b" + b + r"\b", "", t_clean).strip(" -_")
                    if rem:
                        declared_model = rem
                break

    confirmed_facts: List[str] = []
    estimates: List[str] = []
    unknown_info: List[str] = []
    discrepancies: List[str] = []

    # 1. Category validation
    category_confirmed = False
    device_info = (diagnostic or {}).get("device") or {}
    if diagnostic and device_info:
        diag_cat = diagnostic.get("category") or ("mobile" if "sdk" in device_info or "os_version" in device_info else None)
        if diag_cat and diag_cat == category:
            category_confirmed = True
            confirmed_facts.append(f"Category '{category}' verified by device diagnostic telemetry.")
        elif diag_cat and diag_cat != category:
            discrepancies.append(f"Category mismatch: listing categorized as '{category}', but diagnostic reports '{diag_cat}'.")

    if not category_confirmed:
        estimates.append(f"Category '{category}' based on seller declaration and image inputs.")

    # 2. Brand & Model from Diagnostic Telemetry (High Confidence Evidence)
    verified_brand: Optional[str] = None
    verified_model: Optional[str] = None
    verification_status = "unverified"
    confidence = 0.50
    similar_models_warning: Optional[str] = None

    if device_info:
        raw_model = str(device_info.get("model") or "")
        raw_mfr = str(device_info.get("manufacturer") or device_info.get("brand") or "")
        os_ver = device_info.get("os_version") or device_info.get("os")
        sdk = device_info.get("sdk")

        matched = _match_model_identifier(raw_model)
        if matched:
            verified_brand, verified_model = matched
        elif raw_mfr:
            verified_brand = raw_mfr.capitalize()
            verified_model = raw_model

        if verified_brand and verified_model:
            verification_status = "confirmed_by_diagnostics"
            confidence = 0.98
            confirmed_facts.append(f"Brand confirmed: {verified_brand}")
            confirmed_facts.append(f"Model confirmed: {verified_model} (hardware ID: {raw_model})")
            if os_ver:
                confirmed_facts.append(f"Operating System: {os_ver} (SDK {sdk or 'n/a'})")

    # 3. If no diagnostic telemetry, evaluate seller declarations & visual evidence
    if not verified_brand or not verified_model:
        if declared_brand:
            verified_brand = declared_brand
            # Check if declared brand is plausible for category
            cat_brands = SUPPORTED_BRANDS.get(category, {})
            brand_key = declared_brand.lower()
            if brand_key in cat_brands:
                estimates.append(f"Brand '{declared_brand}' declared by seller (supported manufacturer).")
                confidence = 0.70
                verification_status = "seller_declared"
            else:
                estimates.append(f"Brand '{declared_brand}' declared by seller (unverified).")
                confidence = 0.55
                verification_status = "seller_declared_unverified"
        else:
            unknown_info.append("Brand not specified by seller and no diagnostic telemetry available.")

        if declared_model:
            verified_model = declared_model
            estimates.append(f"Model '{declared_model}' declared by seller.")
            # Check for visually ambiguous model families
            for group in VISUALLY_AMBIGUOUS_GROUPS:
                matched_names = [m for m in group["models"] if m.lower() in declared_model.lower()]
                if matched_names:
                    similar_models_warning = f"Visually ambiguous model ({', '.join(group['models'])}): {group['reason']}"
                    unknown_info.append(similar_models_warning)
                    break
        else:
            unknown_info.append("Model not specified; cannot be reliably determined from exterior photos alone without hallucination.")

    # 4. Check for Discrepancy between declared vs diagnostic
    device_mismatch = False
    device_mismatch_detail: Optional[Dict[str, Any]] = None

    if (declared_brand or declared_model) and (verified_brand or verified_model) and verification_status == "confirmed_by_diagnostics":
        brand_conflict = False
        model_conflict = False
        if declared_brand and verified_brand and declared_brand.lower() != verified_brand.lower():
            brand_conflict = True

        if declared_model and verified_model:
            norm_declared = _normalize_model_name(declared_model)
            norm_verified = _normalize_model_name(verified_model)
            raw_norm = _normalize_model_name(str((device_info or {}).get("model") or ""))
            if norm_declared and norm_verified and norm_declared != norm_verified and norm_declared not in norm_verified and norm_verified not in norm_declared and norm_declared not in raw_norm:
                model_conflict = True

        if brand_conflict or model_conflict:
            device_mismatch = True
            decl_name = f"{declared_brand or ''} {declared_model or ''}".strip() or title or "Declared Device"
            diag_name = f"{verified_brand or ''} {verified_model or ''}".strip()
            raw_id = str((device_info or {}).get("model") or "")
            if raw_id and raw_id not in diag_name:
                diag_name = f"{diag_name} ({raw_id})"
            mismatch_msg = (
                f"Device mismatch: Listing specifies '{decl_name}', but diagnostic test results were submitted from "
                f"'{diag_name}'. Hardware diagnostics must be run on the actual device being sold."
            )
            discrepancies.append(mismatch_msg)
            device_mismatch_detail = {
                "declared_device": decl_name,
                "diagnosed_device": diag_name,
                "hardware_id": raw_id,
                "message": mismatch_msg,
            }
    elif declared_brand and verified_brand and declared_brand.lower() != verified_brand.lower():
        discrepancies.append(f"Brand mismatch: seller declared '{declared_brand}', but verified device is '{verified_brand}'.")
    elif declared_model and verified_model and declared_model.lower() not in verified_model.lower() and verified_model.lower() not in declared_model.lower():
        discrepancies.append(f"Model mismatch: seller declared '{declared_model}', but verified hardware is '{verified_model}'.")

    # 5. Clarification needs
    needs_clarification = False
    clarification_prompt: Optional[str] = None

    if not verified_brand or not verified_model or verification_status in ("unverified", "seller_declared_unverified"):
        needs_clarification = True
        clarification_prompt = (
            "We could not independently confirm the exact brand and model from exterior photos. "
            "Please run the SafeResale Diagnosis app on this device, or confirm model details from device settings."
        )
    elif discrepancies:
        needs_clarification = True
        clarification_prompt = f"Conflicting information detected: {'; '.join(discrepancies)}. Please verify before proceeding."

    # 6. Specifications & Missing Info Guard
    # Note: We NEVER invent storage, battery health, RAM, or age if not verified.
    storage = listing.get("storage") or listing.get("attributes", {}).get("storage")
    if storage:
        estimates.append(f"Storage capacity: {storage} (seller declared)")
    else:
        unknown_info.append("Storage capacity not confirmed.")

    battery = listing.get("battery_health") or listing.get("attributes", {}).get("battery_health")
    if battery:
        estimates.append(f"Battery health: {battery} (seller declared)")
    else:
        unknown_info.append("Battery health not confirmed.")

    return {
        "category": category,
        "brand": verified_brand or declared_brand or None,
        "model": verified_model or declared_model or None,
        "declared_brand": declared_brand or None,
        "declared_model": declared_model or None,
        "confidence": confidence,
        "verification_status": verification_status,
        "confirmed_facts": confirmed_facts,
        "estimates": estimates,
        "unknown_info": unknown_info,
        "discrepancies": discrepancies,
        "device_mismatch": device_mismatch,
        "device_mismatch_detail": device_mismatch_detail,
        "similar_models_warning": similar_models_warning,
        "needs_clarification": needs_clarification,
        "clarification_prompt": clarification_prompt,
    }
