"""调用模型并组装 Java 约定的 Agent Run 响应。"""

import time

import httpx

from app.core.config import Settings
from app.models.agent import AgentModelInfo, AgentOutput, AgentRunRequest, AgentRunResponse, AgentUsage
from app.prompts.shopping_guide import build_messages


class AgentServiceError(Exception):
    """可安全返回给 Java 的模型服务错误。"""

    def __init__(self, status_code: int, agent_error_key: str, message: str, retryable: bool) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.agent_error_key = agent_error_key
        self.message = message
        self.retryable = retryable


class AgentService:
    """第一阶段只完成一次模型问答；商品工具、RAG 和工具轨迹后续再接入。"""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    async def run(self, request_id: str, request: AgentRunRequest) -> AgentRunResponse:
        if not self.settings.deepseek_api_key:
            raise AgentServiceError(503, "AI_MODEL_NOT_CONFIGURED", "模型服务尚未配置", False)

        started_at = time.perf_counter()
        payload = {
            "model": self.settings.deepseek_model,
            "messages": build_messages(request),
            "temperature": 0.4,
        }
        try:
            async with httpx.AsyncClient(timeout=self.settings.deepseek_timeout_seconds) as client:
                response = await client.post(
                    f"{self.settings.deepseek_base_url.rstrip('/')}/chat/completions",
                    headers={"Authorization": f"Bearer {self.settings.deepseek_api_key}"},
                    json=payload,
                )
                response.raise_for_status()
        except httpx.TimeoutException as exception:
            raise AgentServiceError(504, "AI_MODEL_TIMEOUT", "模型响应超时", True) from exception
        except httpx.HTTPStatusError as exception:
            status_code = exception.response.status_code
            if status_code in (401, 403):
                raise AgentServiceError(503, "AI_MODEL_AUTH_FAILED", "模型服务认证失败", False) from exception
            raise AgentServiceError(503, "AI_MODEL_UNAVAILABLE", "模型服务暂不可用", True) from exception
        except httpx.HTTPError as exception:
            raise AgentServiceError(503, "AI_MODEL_UNAVAILABLE", "模型服务暂不可用", True) from exception

        try:
            response_data = response.json()
        except ValueError as exception:
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID", "模型返回内容格式异常", True) from exception
        answer = self._extract_answer(response_data)
        usage_data = response_data.get("usage") or {}
        latency_ms = int((time.perf_counter() - started_at) * 1000)
        return AgentRunResponse(
            requestId=request_id,
            answer=answer,
            output=AgentOutput(summary=self._build_summary(request.message)),
            model=AgentModelInfo(provider="deepseek", name=self.settings.deepseek_model),
            usage=AgentUsage(
                inputTokens=usage_data.get("prompt_tokens"),
                outputTokens=usage_data.get("completion_tokens"),
            ),
            latencyMs=latency_ms,
            traces=[],
        )

    @staticmethod
    def _extract_answer(response_data: dict) -> str:
        try:
            answer = response_data["choices"][0]["message"]["content"].strip()
        except (KeyError, IndexError, AttributeError, TypeError) as exception:
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID", "模型返回内容格式异常", True) from exception
        if not answer:
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID", "模型没有返回回答内容", True)
        return answer

    @staticmethod
    def _build_summary(message: str) -> str:
        normalized_message = " ".join(message.split())
        return normalized_message[:80]
