"""Rate limiting via Cache abstraction (memory default, Redis-ready) — 06-security.md:2.3."""
from fastapi import Request, HTTPException
from app.core.cache import cache

# Limits per 04-api-contract.md:10
LIMITS = {
    "auth": (10, 900),          # 10 / 15 min / IP+account
    "upload-token": (60, 600),  # 60 / 10 min / user
    "admin_review": (60, 600),
    "general": (300, 60),       # 300 / min / user
}

async def check_rate_limit(request: Request, bucket: str = "general", key: str | None = None):
    limit, window = LIMITS.get(bucket, LIMITS["general"])
    # key = IP for auth, user id for authed, else IP
    ident = key or request.client.host if request.client else "unknown"
    cache_key = f"rl:{bucket}:{ident}"
    count = await cache.incr(cache_key, window)
    if count > limit:
        raise HTTPException(status_code=429, detail={"code": "rate_limited", "message": f"Too many requests for {bucket}", "retry_after": window})
    return True
