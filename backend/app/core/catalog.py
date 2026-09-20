"""Canonical product catalog — single source of truth for category slugs.

The verification/trust pipeline keys off `slug`, so slugs must stay stable after
a category is created. This module centralizes the defaults so the admin API,
the reference seeder and the seller-facing `/listings/categories` route never
drift. The set follows the PRD product scope (docs/08-ml-plan.md §3.1):
mobile/smartphone, laptop/computer, consumer electronics, cars, bikes,
home appliances, gaming devices, cameras — plus furniture and accessories.
"""

CATEGORIES = [
    {"name": "Mobile", "slug": "mobile", "description": "Smartphones, tablets and cellular devices", "icon": "smartphone",
     "fields": ["price", "year", "brand", "model", "storage", "battery_health", "condition", "notes"], "active": True, "sort": 1},
    {"name": "Laptop", "slug": "laptop", "description": "Laptops, notebooks and computers", "icon": "laptop",
     "fields": ["price", "year", "brand", "model", "storage", "battery_health", "condition", "notes"], "active": True, "sort": 2},
    {"name": "Consumer Electronics", "slug": "electronics", "description": "TVs, monitors and audio equipment", "icon": "tv",
     "fields": ["price", "year", "brand", "model", "condition", "notes"], "active": True, "sort": 3},
    {"name": "Camera", "slug": "camera", "description": "Cameras, lenses and photography gear", "icon": "camera",
     "fields": ["price", "year", "brand", "model", "condition", "notes"], "active": True, "sort": 4},
    {"name": "Gaming", "slug": "gaming", "description": "Consoles, handhelds and gaming devices", "icon": "gamepad",
     "fields": ["price", "year", "brand", "model", "condition", "notes"], "active": True, "sort": 5},
    {"name": "Home Appliance", "slug": "appliance", "description": "Washing machines, fridges and kitchen appliances", "icon": "refrigerator",
     "fields": ["price", "year", "brand", "model", "condition", "notes"], "active": True, "sort": 6},
    {"name": "Car", "slug": "car", "description": "Cars and automobiles", "icon": "car",
     "fields": ["price", "year", "brand", "model", "odometer", "condition", "notes"], "active": True, "sort": 7},
    {"name": "Bike", "slug": "bike", "description": "Motorcycles, scooters and bicycles", "icon": "bike",
     "fields": ["price", "year", "brand", "model", "odometer", "condition", "notes"], "active": True, "sort": 8},
    {"name": "Furniture", "slug": "furniture", "description": "Sofas, tables and home furnishings", "icon": "sofa",
     "fields": ["price", "year", "brand", "material", "condition", "notes"], "active": True, "sort": 9},
    {"name": "Accessory", "slug": "accessory", "description": "Cases, chargers, parts and add-ons", "icon": "package",
     "fields": ["price", "brand", "type", "condition", "notes"], "active": True, "sort": 10},
]

# Legacy slugs that old drafts/listings may still carry. Kept valid so the
# trust/pricing pipeline and existing documents never orphan; not seeded new.
LEGACY_SLUGS = {"vehicle", "tablet"}

# Seller-facing form schemas per canonical slug (served by `GET /listings/categories`).
SCHEMAS = {c["slug"]: c["fields"] for c in CATEGORIES}

# Human-readable labels for the 29 defect classes (core 14 + 15 extensions)
DEFECT_LABELS: dict[str, str] = {
    "scratch": "Scratch", "crack": "Crack", "dent": "Dent",
    "screen_damage": "Screen damage", "glass_damage": "Glass damage",
    "camera_damage": "Camera damage", "port_damage": "Port damage",
    "casing_damage": "Casing damage", "body_deformation": "Body deformation",
    "paint_damage": "Paint damage", "chip": "Chip", "rust": "Rust",
    "corrosion": "Corrosion", "water_damage": "Water damage",
    "stain": "Stain", "discoloration": "Discoloration", "wear": "Wear",
    "broken_part": "Broken part", "missing_part": "Missing part",
    "button_damage": "Button damage", "keyboard_damage": "Keyboard damage",
    "hinge_damage": "Hinge damage", "cable_damage": "Cable damage",
    "connector_damage": "Connector damage", "tire_damage": "Tire damage",
    "wheel_damage": "Wheel damage", "mirror_damage": "Mirror damage",
    "light_damage": "Light damage", "bumper_damage": "Bumper damage",
}

# Category-aware defect vocabulary — which damages are relevant per product.
# Based on Inspektlabs vehicle checklist (dents/glass/lights/bumpers/tires),
# furniture inspection standards (stain/water/rust/structural), and
# appliance failure data (Archimede — water/leak/corrosion).
# Used for UI filtering and for weighting; not a hard filter on detections.
DEFECTS_BY_CATEGORY: dict[str, list[str]] = {
    "mobile": ["scratch", "crack", "dent", "screen_damage", "glass_damage", "camera_damage", "port_damage", "casing_damage", "chip", "paint_damage", "stain", "discoloration", "wear", "water_damage", "corrosion"],
    "laptop": ["scratch", "crack", "dent", "screen_damage", "glass_damage", "keyboard_damage", "hinge_damage", "port_damage", "casing_damage", "paint_damage", "stain", "discoloration", "wear", "chip"],
    "electronics": ["scratch", "dent", "screen_damage", "glass_damage", "port_damage", "cable_damage", "connector_damage", "stain", "discoloration", "wear", "chip", "crack"],
    "camera": ["scratch", "crack", "dent", "glass_damage", "paint_damage", "discoloration", "wear", "chip", "body_deformation", "stain"],
    "gaming": ["scratch", "crack", "dent", "button_damage", "port_damage", "casing_damage", "stain", "discoloration", "wear", "chip"],
    "appliance": ["scratch", "dent", "rust", "corrosion", "water_damage", "stain", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "cable_damage"],
    "furniture": ["scratch", "dent", "stain", "water_damage", "discoloration", "wear", "chip", "crack", "broken_part", "missing_part", "hinge_damage", "rust"],
    "car": ["scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"],
    "bike": ["scratch", "dent", "paint_damage", "rust", "corrosion", "crack", "tire_damage", "wheel_damage", "body_deformation", "chip", "stain", "discoloration", "wear", "cable_damage", "light_damage"],
    "accessory": ["scratch", "crack", "stain", "discoloration", "wear", "broken_part", "missing_part", "cable_damage", "connector_damage", "chip"],
    "vehicle": ["scratch", "dent", "paint_damage", "body_deformation", "chip", "rust", "corrosion", "glass_damage", "light_damage", "bumper_damage", "tire_damage", "wheel_damage", "mirror_damage", "water_damage", "stain", "discoloration", "wear", "crack"],
    "tablet": ["scratch", "crack", "dent", "screen_damage", "glass_damage", "port_damage", "casing_damage", "chip", "stain", "discoloration", "wear"],
}


def fields_for(category_slug: str | None) -> list[str]:
    if not category_slug:
        return SCHEMAS["mobile"]
    return list(SCHEMAS.get(category_slug, SCHEMAS["mobile"]))


def defects_for(category_slug: str | None) -> list[str]:
    """Relevant defect classes for a category (docs/08-ml-plan.md §3.1)."""
    if not category_slug:
        return list(DEFECT_LABELS.keys())
    return DEFECTS_BY_CATEGORY.get(category_slug, list(DEFECT_LABELS.keys()))


def defect_label(defect_class: str) -> str:
    return DEFECT_LABELS.get(defect_class, defect_class.replace("_", " ").title())