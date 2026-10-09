from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase
from .config import settings

_client: AsyncIOMotorClient | None = None

def get_client() -> AsyncIOMotorClient:
    global _client
    if _client is None:
        _client = AsyncIOMotorClient(settings.mongo_url)
    return _client

def get_db() -> AsyncIOMotorDatabase:
    return get_client()[settings.db_name]

async def ping_db() -> bool:
    try:
        await get_client().admin.command("ping")
        return True
    except Exception:
        return False
