"""Python Agent 健康检查路由。"""

from fastapi import APIRouter

from app.container import agent_service
from app.core.config import settings


router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> dict[str, object]:
    """只报告配置状态，不泄露密钥，也不主动请求模型。"""
    model_configured = bool(settings.openai_api_key and settings.openai_base_url)
    token_configured = bool(settings.internal_token)
    rag_service = agent_service.rag_service
    retriever = getattr(rag_service, "retriever", None)
    index_store = getattr(retriever, "index_store", None)
    metadata = getattr(index_store, "metadata", {}) or {}
    return {
        "status": "UP" if model_configured and token_configured else "DEGRADED",
        "modelConfigured": model_configured,
        "internalTokenConfigured": token_configured,
        "javaBackendReachable": False,
        "ragEnabled": settings.rag_enabled,
        "ragReady": bool(getattr(rag_service, "ready", False)),
        "ragBuildId": metadata.get("buildId"),
        "guideDocumentCount": metadata.get("guideDocumentCount"),
        "postDocumentCount": metadata.get("postDocumentCount"),
        "postSnapshotAt": metadata.get("postSnapshotAt"),
        "ragReloadError": getattr(rag_service, "reload_error", None),
    }
