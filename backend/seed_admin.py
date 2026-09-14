import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from passlib.context import CryptContext
import time

MONGO_URL = "mongodb://localhost:27017"
DB_NAME = "saferesale"
pwd = CryptContext(schemes=["argon2"], deprecated="auto")

async def main():
    client = AsyncIOMotorClient(MONGO_URL)
    db = client[DB_NAME]
    # upsert admin@example.com / Admin123!
    existing = await db.users.find_one({"email": "admin@example.com"})
    if existing:
        print(f"admin exists: {existing['_id']} role={existing.get('role')}")
        await db.users.update_one({"_id": existing["_id"]}, {"$set": {"role": "admin", "verified": True, "roles": ["admin"]}})
        print("updated to admin")
    else:
        res = await db.users.insert_one({
            "name": "Admin", "email": "admin@example.com", "password_hash": pwd.hash("Admin123!"),
            "role": "admin", "roles": ["admin"], "verified": True, "status": "active",
            "created_at": time.time(), "updated_at": time.time()
        })
        print(f"created admin: {res.inserted_id}  email=admin@example.com  password=Admin123!")

if __name__ == "__main__":
    asyncio.run(main())
