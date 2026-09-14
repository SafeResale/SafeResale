from fastapi import APIRouter

router = APIRouter(prefix="/listings", tags=["reports"])

@router.get("/{listing_id}/report")
async def report(listing_id: str):
    return {"badge": "verified", "verified_at": None, "visual_summary": {}, "diagnostic_summary": {}, "seller_trust_tier": "new", "risk_band": "low", "breakdown": {}, "reasons": [], "annotated_images": []}
