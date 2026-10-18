import asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from passlib.context import CryptContext
import time

MONGO_URL = "mongodb://localhost:27017"
DB_NAME = "saferesale"
pwd = CryptContext(schemes=["argon2"], deprecated="auto")

EMAIL = "inspector@gmail.com"
PASSWORD = "Rahul@980"

async def main():
    client = AsyncIOMotorClient(MONGO_URL)
    db = client[DB_NAME]
    
    existing = await db.users.find_one({"email": EMAIL})
    if existing:
        print(f"Inspector exists: {existing['_id']} role={existing.get('role')}")
        await db.users.update_one(
            {"_id": existing["_id"]},
            {
                "$set": {
                    "role": "inspector",
                    "roles": ["inspector"],
                    "verified": True,
                    "status": "active",
                    "name": "Certified Inspector",
                    "password_hash": pwd.hash(PASSWORD),
                    "phone": "+91 98765 43210",
                    "updated_at": time.time(),
                }
            }
        )
        print("Updated inspector credentials & role.")
    else:
        res = await db.users.insert_one({
            "name": "Certified Inspector",
            "email": EMAIL,
            "password_hash": pwd.hash(PASSWORD),
            "role": "inspector",
            "roles": ["inspector"],
            "phone": "+91 98765 43210",
            "verified": True,
            "status": "active",
            "created_at": time.time(),
            "updated_at": time.time(),
        })
        print(f"Created inspector account: {res.inserted_id} email={EMAIL}")

if __name__ == "__main__":
    asyncio.run(main())
