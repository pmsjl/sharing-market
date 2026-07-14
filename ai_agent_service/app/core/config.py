"""仅从环境变量读取 Python Agent 的运行配置。"""

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    """Python 服务自身配置；不保存 Java 数据库或业务系统密钥。"""

    internal_token: str = os.getenv("AI_AGENT_INTERNAL_TOKEN", "")
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    deepseek_timeout_seconds: float = float(os.getenv("DEEPSEEK_TIMEOUT_SECONDS", "30"))


settings = Settings()
