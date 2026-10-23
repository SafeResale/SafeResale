"""Catalog Search and Lookup API for SafeResale.

Powers the auto-complete dropdown and verified device selection in the mobile and web apps,
and provides ground-truth catalog metadata for ML identification and price checking.
"""
from fastapi import APIRouter, Query, HTTPException
from typing import Optional, List, Dict, Any

from app.core.device_catalog import (
    search_catalog,
    get_brands_for_category,
    get_models_for_brand,
    find_by_hardware_id,
    get_category_summary,
    CATALOG_ITEMS,
)

router = APIRouter(prefix="/catalog", tags=["catalog"])


@router.get("/search")
async def search(
    q: str = Query("", description="Search query for brand, model, or variant"),
    category: Optional[str] = Query(None, description="Optional category filter (mobile, laptop, car, bike, etc.)"),
    limit: int = Query(15, ge=1, le=50, description="Max results to return"),
):
    """Search products across categories with fuzzy prefix and alias matching."""
    items = search_catalog(query=q, category=category, limit=limit)
    return {
        "query": q,
        "category": category,
        "total": len(items),
        "items": items,
    }


@router.get("/categories")
async def categories():
    """List supported catalog categories with model counts and verified brands."""
    return {"categories": get_category_summary()}


@router.get("/{category}/brands")
async def brands_for_category(category: str):
    """List distinct verified brands available for a given category."""
    brands = get_brands_for_category(category)
    return {"category": category, "brands": brands, "total": len(brands)}


@router.get("/{category}/brands/{brand}/models")
async def models_for_brand(category: str, brand: str):
    """List all verified models and variants for a specific brand in a category."""
    models = get_models_for_brand(category, brand)
    return {"category": category, "brand": brand, "models": models, "total": len(models)}


@router.get("/lookup")
async def lookup_hardware(hardware_id: str = Query(..., description="Hardware identifier e.g. sm-a356e or iphone14,5")):
    """Reverse lookup product metadata from hardware telemetry identifier."""
    item = find_by_hardware_id(hardware_id)
    if not item:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": f"No catalog item found for hardware ID '{hardware_id}'"})
    return {"hardware_id": hardware_id, "item": item}
