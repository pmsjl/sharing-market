"""Java 调用的内部 Agent Run 路由。"""

from typing import Annotated

from fastapi import APIRouter, Header
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.core.security import InternalAuthenticationError, verify_internal_token
from app.models.agent import AgentErrorResponse, AgentRunRequest, AgentRunResponse
from app.services.agent_service import AgentService, AgentServiceError


router = APIRouter(tags=["agent"])
agent_service = AgentService(settings)


@router.post("/agent/v1/runs", response_model=AgentRunResponse)
async def run_agent(
    request: AgentRunRequest,
    x_internal_token: Annotated[str | None, Header()] = None,
    x_request_id: Annotated[str | None, Header()] = None,
):
    """接收 Java 已鉴权上下文，调用模型后返回统一内部响应。"""
    try:
        verify_internal_token(x_internal_token)
    except InternalAuthenticationError:
        return _error_response(x_request_id, 401, "AI_INTERNAL_UNAUTHORIZED", "内部服务身份校验失败", False)
    except RuntimeError:
        return _error_response(x_request_id, 503, "AI_AGENT_CONFIG_INVALID", "AI 服务内部 Token 未配置", False)
    if not x_request_id:
        return _error_response(None, 400, "AI_REQUEST_ID_REQUIRED", "缺少 X-Request-Id", False)

    try:
        return await agent_service.run(x_request_id, request)
    except AgentServiceError as exception:
        return _error_response(
            x_request_id,
            exception.status_code,
            exception.agent_error_key,
            exception.message,
            exception.retryable,
        )


def _error_response(request_id: str | None, status_code: int, agent_error_key: str, message: str,
                    retryable: bool) -> JSONResponse:
    body = AgentErrorResponse(
        requestId=request_id,
        agentErrorKey=agent_error_key,
        message=message,
        retryable=retryable,
    )
    return JSONResponse(status_code=status_code, content=body.model_dump())
