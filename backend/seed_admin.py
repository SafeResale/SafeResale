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
    # upsert rahul.legend345@gmail.com / Rahul@980 (admin)
    EMAIL = "rahul.legend345@gmail.com"
    PASSWORD = "Rahul@980"
    existing = await db.users.find_one({"email": EMAIL})
    if existing:
        print(f"admin exists: {existing['_id']} role={existing.get('role')}")
        await db.users.update_one({"_id": existing["_id"]}, {"$set": {"role": "admin", "verified": True, "roles": ["admin"], "name": "Admin", "password_hash": pwd.hash(PASSWORD)}})
        print("updated to admin")
    else:
        res = await db.users.insert_one({
            "name": "Admin", "email": EMAIL, "password_hash": pwd.hash(PASSWORD),
            "role": "admin", "roles": ["admin"], "verified": True, "status": "active",
            "created_at": time.time(), "updated_at": time.time()
        })
        print(f"created admin: {res.inserted_id}  email={EMAIL}  password={PASSWORD}")

if __name__ == "__main__":
    asyncio.run(main())
