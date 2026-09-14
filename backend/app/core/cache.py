import asyncio
import time
from typing import Any

class MemoryCache:
    def __init__(self):
        self._store: dict[str, tuple[Any, float | None]] = {}
        self._lock = asyncio.Lock()

    async def get(self, key: str):
        async with self._lock:
            item = self._store.get(key)
            if not item:
                return None
            val, exp = item
            if exp and time.time() > exp:
                del self._store[key]
                return None
            return val

    async def set(self, key: str, value: Any, ttl: int | None = None):
        exp = time.time() + ttl if ttl else None
        async with self._lock:
            self._store[key] = (value, exp)

    async def incr(self, key: str, expire: int) -> int:
        async with self._lock:
            val, exp = self._store.get(key, (0, None))
            if exp and time.time() > exp:
                val = 0
            val = int(val) + 1
            self._store[key] = (val, time.time() + expire)
            return val

    async def exists(self, key: str) -> bool:
        return (await self.get(key)) is not None

cache = MemoryCache()
