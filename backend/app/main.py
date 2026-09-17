import uuid
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.health import router as health_router
from app.api.auth import router as auth_router

app = FastAPI(title="SafeResale API", version="1.0.0")

app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    rid = request.headers.get("X-Request-ID") or str(uuid.uuid4())
    response = await call_next(request)
    response.headers["X-Request-ID"] = rid
    return response

app.include_router(health_router)
app.include_router(auth_router)

# placeholders for next phases — imported lazily to keep Phase 0 green
try:
    from app.api.listings import router as listings_router  # type: ignore
    app.include_router(listings_router)
except Exception:
    pass
try:
    from app.api.uploads import router as uploads_router, bytes_router as upload_bytes_router  # type: ignore
    app.include_router(uploads_router)
    app.include_router(upload_bytes_router)
except Exception:
    pass
try:
    from app.api.verification import router as verification_router  # type: ignore
    app.include_router(verification_router)
except Exception:
    pass
try:
    from app.api.admin import router as admin_router  # type: ignore
    app.include_router(admin_router)
except Exception:
    pass
try:
    from app.api.escrow import router as escrow_router  # type: ignore
    app.include_router(escrow_router)
except Exception:
    pass
try:
    from app.api.reports import router as reports_router  # type: ignore
    app.include_router(reports_router)
except Exception:
    pass
try:
    from app.api.admin_users import router as admin_users_router  # type: ignore
    app.include_router(admin_users_router)
except Exception:
    pass
try:
    from app.api.admin_catalog import router as admin_catalog_router  # type: ignore
    app.include_router(admin_catalog_router)
except Exception:
    pass
try:
    from app.api.admin_content import router as admin_content_router  # type: ignore
    app.include_router(admin_content_router)
except Exception:
    pass
try:
    from app.api.admin_support import router as admin_support_router  # type: ignore
    app.include_router(admin_support_router)
except Exception:
    pass
try:
    from app.api.admin_settings import router as admin_settings_router  # type: ignore
    app.include_router(admin_settings_router)
except Exception:
    pass
try:
    from app.api.public_support import router as public_support_router  # type: ignore
    app.include_router(public_support_router)
except Exception:
    pass
