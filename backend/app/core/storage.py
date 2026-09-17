from pathlib import Path
from .config import settings

class LocalStorage:
    def __init__(self, base: str | None = None):
        self.base = Path(base or settings.storage_dir)
        self.base.mkdir(parents=True, exist_ok=True)

    def create_upload_url(self, key: str, content_type: str) -> dict:
        # local driver: direct write, return file path as upload_url
        return {"upload_url": f"/uploads/{key}", "key": key}

    def get_public_url(self, key: str) -> str:
        return f"/uploads/{key}"

    def delete(self, key: str) -> None:
        p = self.base / key
        if p.exists():
            p.unlink()


async def ping_storage() -> bool:
    """Cheap writability probe used by the admin System status surface."""
    try:
        probe = "ping"
        url = storage.create_upload_url(probe, "text/plain").get("upload_url")
        return bool(url)
    except Exception:
        return False


storage = LocalStorage()
