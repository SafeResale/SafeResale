from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from bson import ObjectId
from app.core.db import get_db
from app.core.security import get_current_user
from app.core.audit import log as audit_log

router = APIRouter(prefix="/listings", tags=["listings"])

class DraftIn(BaseModel):
    category: str
    title: str
    price: float
    brand: str | None = None
    model: str | None = None
    storage: str | None = None
    battery_health: str | None = None
    odometer: int | None = None
    condition: str | None = None  # seller-declared, never AI-written (R-LIST-04)
    notes: str | None = None

    def validate(self):
        if self.price <= 0 or self.price > 10_000_000:
            raise ValueError("Price must be >0 and ≤10M")
        if self.category == "mobile" and self.battery_health and self.battery_health not in ("good","moderate","poor","unknown"):
            raise ValueError("battery_health must be good|moderate|poor|unknown")
        if self.category == "vehicle" and self.odometer is not None and (self.odometer < 0 or self.odometer > 2_000_000):
            raise ValueError("odometer out of range")

@router.post("/create-draft", status_code=201)
async def create_draft(request: Request, body: DraftIn, user=Depends(get_current_user)):
    body.validate()
    from app.core.rate_limit import check_rate_limit
    await check_rate_limit(request, "general", key=user["sub"])
    db = get_db()
    doc = body.model_dump()
    seller_condition = doc.pop("condition", None)
    doc.update({"seller_id": ObjectId(user["sub"]), "status": "draft", "seller_condition": seller_condition, "created_at": __import__("time").time(), "updated_at": __import__("time").time()})
    res = await db.listings.insert_one(doc)
    try:
        key = f"{doc.get('category','')}:{doc.get('brand','')}:{doc.get('model','')}".lower()
        if doc.get("price"):
            existing = await db.price_stats.find_one({"key": key})
            if existing:
                prices = [p["price"] async for p in db.listings.find({"category": doc.get("category"), "brand": doc.get("brand"), "model": doc.get("model")}, {"price": 1})]
                prices = sorted([float(p["price"]) for p in prices if p.get("price")])
                median = prices[len(prices)//2] if prices else float(doc["price"])
                await db.price_stats.update_one({"key": key}, {"$set": {"median_price": median, "count": len(prices), "updated_at": __import__("time").time()}})
            else:
                await db.price_stats.insert_one({"key": key, "median_price": float(doc["price"]), "count": 1, "updated_at": __import__("time").time()})
    except Exception:
        pass
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="listing.create", target_type="listing", target_id=res.inserted_id, detail={"category": doc.get("category")}, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc["_id"] = str(res.inserted_id)
    doc["seller_id"] = str(doc["seller_id"])
    return {"listing": doc}

@router.get("/categories")
async def categories():
    return [
        {"category": "mobile", "schema": ["price","year","brand","model","storage","battery_health","condition","notes"]},
        {"category": "vehicle", "schema": ["price","year","brand","model","odometer","condition","notes"]},
        {"category": "accessory", "schema": ["price","brand","type","condition","notes"]},
    ]

@router.get("/{listing_id}")
async def get_listing(listing_id: str, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.listings.find_one({"_id": ObjectId(listing_id)})
    if not doc:
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"Listing not found"})
    # ownership or admin/inspector can view; buyer can view published only (R-SEC T9)
    if doc.get("status") not in ("published","approved") and str(doc.get("seller_id")) != user["sub"] and user.get("role") not in ("admin","inspector"):
        raise HTTPException(status_code=403, detail={"code":"forbidden","message":"Not authorized"})
    doc["_id"] = str(doc["_id"])
    doc["seller_id"] = str(doc["seller_id"])
    return {"listing": doc}

@router.get("")
async def list_listings(category: str | None = None, page: int = 1, page_size: int = 20, user=Depends(get_current_user)):
    db = get_db()
    q: dict = {}
    if category:
        q["category"] = category
    # buyers see only published; sellers see own
    if user.get("role") not in ("admin","inspector"):
        q["status"] = "published"
    total = await db.listings.count_documents(q)
    items = await db.listings.find(q).skip((page-1)*page_size).limit(page_size).to_list(page_size)
    for it in items:
        it["_id"] = str(it["_id"])
        it["seller_id"] = str(it["seller_id"])
    return {"items": items, "total": total, "page": page, "page_size": page_size}

@router.patch("/{listing_id}")
async def update_listing(listing_id: str, body: dict, request: Request, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.listings.find_one({"_id": ObjectId(listing_id), "seller_id": ObjectId(user["sub"])})
    if not doc:
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"Not found or not owner"})
    if doc.get("status") in ("published","approved") and "price" in body:
        # editing published requires re-verification (R-LIST-07)
        body["status"] = "draft"
    await db.listings.update_one({"_id": ObjectId(listing_id)}, {"$set": {**body, "updated_at": __import__("time").time()}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="listing.update", target_type="listing", target_id=ObjectId(listing_id), detail=body, ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    doc.update(body)
    doc["_id"] = str(doc["_id"])
    doc["seller_id"] = str(doc["seller_id"])
    return {"listing": doc}

@router.post("/{listing_id}/submit")
async def submit(listing_id: str, request: Request, user=Depends(get_current_user)):
    db = get_db()
    doc = await db.listings.find_one({"_id": ObjectId(listing_id), "seller_id": ObjectId(user["sub"])})
    if not doc:
        raise HTTPException(status_code=404, detail={"code":"not_found","message":"Not found"})
    await db.listings.update_one({"_id": ObjectId(listing_id)}, {"$set": {"status": "submitted", "updated_at": __import__("time").time()}})
    await audit_log(actor_id=ObjectId(user["sub"]), actor_role=user.get("role"), action="listing.submit", target_type="listing", target_id=ObjectId(listing_id), ip=request.client.host if request.client else None, request_id=request.headers.get("X-Request-ID"))
    return {"listing": {"id": listing_id, "status": "submitted"}}
