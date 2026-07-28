"""Python Agent 健康检查路由。"""

from fastapi import APIRouter

from app.core.config import settings


router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> dict[str, object]:
    """只报告配置状态，不泄露密钥，也不主动请求模型。"""
    model_configured = bool(settings.openai_api_key and settings.openai_base_url)
    token_configured = bool(settings.internal_token)
    return {
        "status": "UP" if model_configured and token_configured else "DEGRADED",
        "modelConfigured": model_configured,
        "internalTokenConfigured": token_configured,
        "javaBackendReachable": False,
        "ragEnabled": False,
    }
