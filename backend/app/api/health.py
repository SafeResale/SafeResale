from fastapi import APIRouter
from app.core.db import ping_db
from app.core.config import settings

router = APIRouter()

@router.get("/health")
async def health():
    db_ok = await ping_db()
    return {
        "status": "ok" if db_ok else "degraded",
        "db": "up" if db_ok else "down",
        "cache": settings.cache_driver,
        "storage": settings.storage_driver,
        "vision_provider": settings.vision_provider,
    }
