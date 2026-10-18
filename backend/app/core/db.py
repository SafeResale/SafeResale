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

async def ensure_indexes():
    """Ensure indexes for users including KYC fields."""
    try:
        db = get_db()
        await db.users.create_index("kyc_status", background=True)
        await db.users.create_index("kyc_submitted_at", background=True, sparse=True)
    except Exception as e:
        print(f"[db] Warning: failed to ensure indexes: {e}")
