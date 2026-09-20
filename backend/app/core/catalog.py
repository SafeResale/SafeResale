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


def fields_for(category_slug: str | None) -> list[str]:
    if not category_slug:
        return SCHEMAS["mobile"]
    return list(SCHEMAS.get(category_slug, SCHEMAS["mobile"]))