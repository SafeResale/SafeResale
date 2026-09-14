from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    mongo_url: str = "mongodb://localhost:27017"
    db_name: str = "saferesale"
    jwt_secret: str = "change-me-to-a-random-32-char-string"
    jwt_ttl_access: int = 900
    jwt_ttl_refresh: int = 2592000
    cache_driver: str = "memory"
    redis_url: str = ""
    storage_driver: str = "local"
    storage_dir: str = "uploads"
    vision_provider: str = "stub"
    ml_weights_dir: str = ""
    dev_verify_enabled: bool = True
    log_level: str = "info"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

settings = Settings()
