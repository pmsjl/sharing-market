"""仅从环境变量读取 Python Agent 的运行配置。"""

from dataclasses import dataclass
import os
from pathlib import Path

from dotenv import load_dotenv

# 固定从服务根目录加载本地 .env；已经存在的系统环境变量优先。
_ENV_PATH = Path(__file__).resolve().parents[2] / ".env"
load_dotenv(_ENV_PATH, override=False)


@dataclass(frozen=True)
class Settings:
    """Python 服务自身配置；不保存 Java 数据库或业务系统密钥。"""

    internal_token: str = os.getenv("AI_AGENT_INTERNAL_TOKEN", "")
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "")
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-5.6-terra")
    openai_timeout_seconds: float = float(
        os.getenv("OPENAI_TIMEOUT_SECONDS", "70"))
    openai_reasoning_effort: str = os.getenv("OPENAI_REASONING_EFFORT",
                                             "medium")
    openai_text_verbosity: str = os.getenv("OPENAI_TEXT_VERBOSITY", "high")
    java_backend_base_url: str = os.getenv(
        "JAVA_BACKEND_BASE_URL",
        "http://127.0.0.1:8102",
    )

    java_backend_timeout_seconds: float = float(
        os.getenv("JAVA_BACKEND_TIMEOUT_SECONDS", "10"))

    max_tool_rounds: int = int(os.getenv("AI_MAX_TOOL_ROUNDS", "4"))

    #RAG相关参数
    rag_enabled: bool = os.getenv("RAG_ENABLED", "false").lower() == "true"

    # Embedding 服务必须独立配置，不能回退到不支持 /embeddings 的生成模型中转。
    embedding_base_url: str = os.getenv("EMBEDDING_BASE_URL", "")
    embedding_api_key: str = os.getenv("EMBEDDING_API_KEY", "")
    embedding_model: str = os.getenv(
        "EMBEDDING_MODEL",
        "text-embedding-v4",
    )
    embedding_dimensions: int = int(os.getenv("EMBEDDING_DIMENSIONS", "1024"))
    embedding_batch_size: int = int(os.getenv("EMBEDDING_BATCH_SIZE", "10"))

    rag_index_dir: str = os.getenv(
        "RAG_INDEX_DIR",
        ".cache/rag_index",
    )
    rag_guide_top_k: int = int(
        os.getenv("RAG_GUIDE_TOP_K", os.getenv("RAG_TOP_K", "5")))
    rag_score_threshold: float = float(os.getenv("RAG_SCORE_THRESHOLD",
                                                 "0.50"))
    rag_guide_max_chunks_per_document: int = int(
        os.getenv(
            "RAG_GUIDE_MAX_CHUNKS_PER_DOCUMENT",
            os.getenv("RAG_MAX_CHUNKS_PER_DOCUMENT", "2"),
        ))
    rag_post_top_k: int = int(os.getenv("RAG_POST_TOP_K", "3"))
    rag_post_score_threshold: float = float(
        os.getenv("RAG_POST_SCORE_THRESHOLD", "0.50"))
    rag_post_max_chunks_per_document: int = int(
        os.getenv("RAG_POST_MAX_CHUNKS_PER_DOCUMENT", "1"))
    rag_post_snapshot_page_size: int = int(
        os.getenv("RAG_POST_SNAPSHOT_PAGE_SIZE", "200"))


settings = Settings()
