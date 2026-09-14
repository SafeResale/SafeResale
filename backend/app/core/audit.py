from app.core.db import get_db
import time

async def log(actor_id=None, actor_role=None, action="", target_type=None, target_id=None, detail=None, ip=None, request_id=None):
    try:
        db = get_db()
        await db.audit_logs.insert_one({
            "actor_id": actor_id, "actor_role": actor_role, "action": action,
            "target_type": target_type, "target_id": target_id, "detail": detail or {},
            "ip": ip, "request_id": request_id, "created_at": time.time()
        })
    except Exception:
        pass
