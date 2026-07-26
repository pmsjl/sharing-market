import json
import time
from typing import Any

import httpx
from pydantic import ValidationError

from app.clients.java_backend import (
    JavaBackendClient,
    JavaBackendClientError,
)
from app.core.config import Settings
from app.models.agent import (
    AgentModelInfo,
    AgentOutput,
    AgentRunRequest,
    AgentRunResponse,
    AgentToolTrace,
    AgentUsage,
)
from app.models.tools import CommoditySearchArguments
from app.prompts.shopping_guide import build_messages
from app.tools.definitions import SEARCH_COMMODITIES_TOOL


class AgentServiceError(Exception):
    """可安全返回给 Java 的模型服务错误。"""

    def __init__(self, status_code: int, agent_error_key: str, message: str,
                 retryable: bool) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.agent_error_key = agent_error_key
        self.message = message
        self.retryable = retryable


class AgentService:
    """第一阶段只完成一次模型问答；商品工具、RAG 和工具轨迹后续再接入。"""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.java_backend_client = JavaBackendClient(settings)

    async def run(self, request_id: str,
                  request: AgentRunRequest) -> AgentRunResponse:
        if not self.settings.deepseek_api_key:
            raise AgentServiceError(503, "AI_MODEL_NOT_CONFIGURED", "模型服务尚未配置",
                                    False)
        if not self.settings.internal_token:
            raise AgentServiceError(
                503,
                "AI_AGENT_CONFIG_INVALID",
                "AI服务内部Token未配置",
                False,
            )
        #这里先对已有的信息进行校验
        traces: list[AgentToolTrace] = []
        messages: list[dict[str, Any]] = build_messages(request)
        input_tokens = 0
        output_tokens = 0
        answer: str = ""
        started_at = time.perf_counter()
        for tool_rounds in range(self.settings.max_tool_rounds + 1):
            payload = {
                "model": self.settings.deepseek_model,
                "messages": messages,
                "temperature": 0.4,
                "tools": [SEARCH_COMMODITIES_TOOL],
                "tool_choice": "auto"
            }
            try:
                async with httpx.AsyncClient(
                        timeout=self.settings.deepseek_timeout_seconds
                ) as client:
                    response = await client.post(
                        f"{self.settings.deepseek_base_url.rstrip('/')}/chat/completions",
                        headers={
                            "Authorization":
                            f"Bearer {self.settings.deepseek_api_key}"
                        },
                        json=payload,
                    )
                    response.raise_for_status()

            except httpx.TimeoutException as exception:
                raise AgentServiceError(504, "AI_MODEL_TIMEOUT", "模型响应超时",
                                        True) from exception
            except httpx.HTTPStatusError as exception:
                status_code = exception.response.status_code
                if status_code in (401, 403):
                    raise AgentServiceError(503, "AI_MODEL_AUTH_FAILED",
                                            "模型服务认证失败", False) from exception
                raise AgentServiceError(503, "AI_MODEL_UNAVAILABLE",
                                        "模型服务暂不可用", True) from exception
            except httpx.HTTPError as exception:
                raise AgentServiceError(503, "AI_MODEL_UNAVAILABLE",
                                        "模型服务暂不可用", True) from exception

            try:
                response_data = response.json()
            except ValueError as exception:
                raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID",
                                        "模型返回内容格式异常", True) from exception

            model_message = self._extract_model_message(response_data)
            tool_calls = model_message.get("tool_calls") or []
            #dict[key]和dict.get(key)当key为None时结果是不一样的
            #前者会直接报错，但是后者会返回None
            usage_data = response_data.get("usage") or {}
            input_tokens += usage_data.get("prompt_tokens") or 0
            output_tokens += usage_data.get("completion_tokens") or 0
            if not tool_calls:
                answer = self._extract_answer(model_message)
                break
                # 已经执行满最大工具轮数，不再追加，也不再执行
            if tool_rounds >= self.settings.max_tool_rounds:
                raise AgentServiceError(
                    502,
                    "AI_TOOL_ROUNDS_EXCEEDED",
                    "模型工具调用次数超过限制",
                    True,
                )

            messages.append({
                "role": "assistant",
                "content": model_message.get("content"),
                "tool_calls": tool_calls,
            })
            for tool_call in tool_calls:
                tool_message, trace = await self._execute_tool_calls(
                    request_id, tool_call)
                messages.append(tool_message)
                traces.append(trace)
        # 10. 组装 Java 响应
        latency_ms = int((time.perf_counter() - started_at) * 1000)

        return AgentRunResponse(
            requestId=request_id,
            answer=answer,
            output=AgentOutput(summary=self._build_summary(request.message)),
            model=AgentModelInfo(
                provider="deepseek",
                name=self.settings.deepseek_model,
            ),
            usage=AgentUsage(
                inputTokens=input_tokens or None,
                outputTokens=output_tokens or None,
            ),
            latencyMs=latency_ms,
            traces=traces,
        )

    """deepseek输出格式response_data[choices][0][message]
{
  "model": "deepseek-chat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "id": "call_123",
            "type": "function",
            "function": {
              "name": "search_commodities",
              "arguments": "{\"keywords\":[\"手机\"],\"maxPrice\":1000,\"sortBy\":\"PRICE_ASC\",\"limit\":5}"
            }
          }
        ]
      },
    }
  ],
  "usage": {
    "prompt_tokens": 1250,
    "completion_tokens": 38,
    "total_tokens": 1288,
    "prompt_cache_hit_tokens": 1000,
    "prompt_cache_miss_tokens": 250
  }
}
    """

    async def _execute_tool_calls(
            self, request_id: str,
            tool_call: dict) -> tuple[dict, AgentToolTrace]:
        call_id = tool_call.get("id")
        function_data = tool_call.get("function")

        if not isinstance(call_id, str) or not call_id:
            raise AgentServiceError(
                502,
                "AI_TOOL_CALL_INVALID",
                "模型返回的工具调用缺少调用ID",
                True,
            )

        if not isinstance(function_data, dict):
            raise AgentServiceError(
                502,
                "AI_TOOL_CALL_INVALID",
                "模型返回的工具调用结构异常",
                True,
            )
        tool_name = function_data.get("name")
        raw_arguments = function_data.get("arguments")
        if not isinstance(tool_name, str) or not tool_name:
            raise AgentServiceError(
                502,
                "AI_TOOL_CALL_INVALID",
                "模型返回的工具名称格式异常",
                True,
            )
        if tool_name != SEARCH_COMMODITIES_TOOL["function"]["name"]:
            raise AgentServiceError(
                502,
                "AI_TOOL_NOT_SUPPORTED",
                f"模型请求了不支持的工具：{tool_name}",
                False,
            )
        if not isinstance(raw_arguments, str):
            raise AgentServiceError(
                502,
                "AI_TOOL_ARGUMENTS_INVALID",
                "模型返回的工具参数格式异常",
                False,
            )

        try:
            arguments_data = json.loads(raw_arguments)
            arguments = CommoditySearchArguments.model_validate(arguments_data)
        except (json.JSONDecodeError, ValidationError, TypeError) as exception:
            raise AgentServiceError(
                502,
                "AI_TOOL_ARGUMENTS_INVALID",
                "模型生成的商品搜索参数不合法",
                True,
            ) from exception

        tool_started_at = time.perf_counter()

        try:
            result = await self.java_backend_client.search_commodities(
                request_id=request_id,
                arguments=arguments,
            )
        except JavaBackendClientError as exception:
            raise AgentServiceError(503 if exception.retryable else 502,
                                    exception.agent_error_key,
                                    exception.message, exception.retryable)

        tool_latency_ms = int((time.perf_counter() - tool_started_at) * 1000)
        tool_message = {
            "role": "tool",
            "tool_call_id": call_id,
            "content": result.model_dump_json()
        }
        trace = AgentToolTrace(
            toolName=tool_name,
            toolArguments=arguments.model_dump(
                mode="json",
                exclude_none=True,
            ),
            toolResultSummary={
                "matchedCount": result.matchedCount,
                "returnedCount": len(result.items),
            },
            status="SUCCESS",
            latencyMs=tool_latency_ms,
        )
        return tool_message, trace

    @staticmethod
    def _extract_model_message(response_data: dict) -> dict:
        try:
            message = response_data["choices"][0]["message"]
        except (KeyError, IndexError, TypeError) as exception:
            raise AgentServiceError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            ) from exception
        if not isinstance(message, dict):
            raise AgentServiceError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            )
        return message

    @staticmethod
    def _extract_answer(model_message: dict) -> str:
        answer = model_message["content"]
        if not isinstance(answer, str):
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID",
                                    "模型返回内容格式异常", True)
        answer = answer.strip()
        if not answer:
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID",
                                    "模型没有返回回答内容", True)
        return answer

    @staticmethod
    def _build_summary(message: str) -> str:
        normalized_message = " ".join(message.split())
        return normalized_message[:80]
