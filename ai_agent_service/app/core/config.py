"""仅从环境变量读取 Python Agent 的运行配置。"""

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    """Python 服务自身配置；不保存 Java 数据库或业务系统密钥。"""

    internal_token: str = os.getenv("AI_AGENT_INTERNAL_TOKEN", "")
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "")
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-5.6-terra")
    openai_timeout_seconds: float = float(
        os.getenv("OPENAI_TIMEOUT_SECONDS", "30"))
    openai_reasoning_effort: str = os.getenv("OPENAI_REASONING_EFFORT",
                                             "medium")
    java_backend_base_url: str = os.getenv(
        "JAVA_BACKEND_BASE_URL",
        "http://127.0.0.1:8102",
    )

    java_backend_timeout_seconds: float = float(
        os.getenv("JAVA_BACKEND_TIMEOUT_SECONDS", "10"))

    max_tool_rounds: int = int(os.getenv("AI_MAX_TOOL_ROUNDS", "4"))


settings = Settings()
