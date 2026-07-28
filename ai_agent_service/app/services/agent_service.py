import json
import time
from typing import Any, Protocol

from pydantic import ValidationError

from app.clients.java_backend import (
    JavaBackendClient,
    JavaBackendClientError,
)
from app.clients.openai_responses import (
    OpenAIResponsesClient,
    OpenAIResponsesClientError,
)
from app.core.config import Settings
from app.models.agent import (
    AgentIntent,
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


class ResponsesClient(Protocol):
    async def create_response(
        self,
        input_items: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> dict[str, Any]:
        ...


class JavaToolClient(Protocol):
    async def search_commodities(
        self,
        request_id: str,
        arguments: CommoditySearchArguments,
    ) -> Any:
        ...


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
    """编排 OpenAI Responses 模型调用和受控 Java 商品工具。"""

    def __init__(
        self,
        settings: Settings,
        openai_client: ResponsesClient | None = None,
        java_backend_client: JavaToolClient | None = None,
    ) -> None:
        self.settings = settings
        self.openai_client = openai_client or OpenAIResponsesClient(settings)
        self.java_backend_client = java_backend_client or JavaBackendClient(settings)

    async def run(self, request_id: str,
                  request: AgentRunRequest) -> AgentRunResponse:
        if not self.settings.openai_api_key or not self.settings.openai_base_url:
            raise AgentServiceError(503, "AI_MODEL_NOT_CONFIGURED", "模型服务尚未配置",
                                    False)
        if not self.settings.internal_token:
            raise AgentServiceError(
                503,
                "AI_AGENT_CONFIG_INVALID",
                "AI服务内部Token未配置",
                False,
            )
        if self.settings.openai_reasoning_effort not in {
            "none", "low", "medium", "high", "xhigh", "max"
        }:
            raise AgentServiceError(
                503,
                "AI_AGENT_CONFIG_INVALID",
                "模型推理强度配置不合法",
                False,
            )

        traces: list[AgentToolTrace] = []
        input_items: list[dict[str, Any]] = build_messages(request)
        input_tokens = 0
        output_tokens = 0
        answer: str = ""
        model_name = self.settings.openai_model
        started_at = time.perf_counter()
        for tool_rounds in range(self.settings.max_tool_rounds + 1):
            try:
                response_data = await self.openai_client.create_response(
                    input_items=input_items,
                    tools=[SEARCH_COMMODITIES_TOOL],
                )
            except OpenAIResponsesClientError as exception:
                raise AgentServiceError(
                    exception.status_code,
                    exception.agent_error_key,
                    exception.message,
                    exception.retryable,
                ) from exception

            output_items = self._extract_output_items(response_data)
            tool_calls = [
                item for item in output_items
                if item.get("type") == "function_call"
            ]
            usage_data = response_data.get("usage") or {}
            if not isinstance(usage_data, dict):
                raise AgentServiceError(
                    502,
                    "AI_MODEL_RESPONSE_INVALID",
                    "模型返回内容格式异常",
                    True,
                )
            input_tokens += usage_data.get("input_tokens") or 0
            output_tokens += usage_data.get("output_tokens") or 0
            returned_model = response_data.get("model")
            if isinstance(returned_model, str) and returned_model:
                model_name = returned_model

            if not tool_calls:
                answer = self._extract_answer(output_items)
                break

            if tool_rounds >= self.settings.max_tool_rounds:
                raise AgentServiceError(
                    502,
                    "AI_TOOL_ROUNDS_EXCEEDED",
                    "模型工具调用次数超过限制",
                    True,
                )

            # store=false 时，下一次请求要重新带上本次 response.output
            # （包括可能存在的 reasoning/function_call 项），再追加与
            # call_id 对应的 function_call_output。
            input_items.extend(output_items)
            for tool_call in tool_calls:
                tool_message, trace = await self._execute_tool_calls(
                    request_id, tool_call)
                input_items.append(tool_message)
                traces.append(trace)

        latency_ms = int((time.perf_counter() - started_at) * 1000)

        return AgentRunResponse(
            requestId=request_id,
            answer=answer,
            output=AgentOutput(
                intent=AgentIntent.GENERAL_GUIDE,
                summary=self._build_summary(request.message),
            ),
            model=AgentModelInfo(
                provider="openai",
                name=model_name,
            ),
            usage=AgentUsage(
                inputTokens=input_tokens or None,
                outputTokens=output_tokens or None,
            ),
            latencyMs=latency_ms,
            traces=traces,
        )

    async def _execute_tool_calls(
            self, request_id: str,
            tool_call: dict) -> tuple[dict, AgentToolTrace]:
        call_id = tool_call.get("call_id")

        if not isinstance(call_id, str) or not call_id:
            raise AgentServiceError(
                502,
                "AI_TOOL_CALL_INVALID",
                "模型返回的工具调用缺少调用ID",
                True,
            )

        tool_name = tool_call.get("name")
        raw_arguments = tool_call.get("arguments")
        if not isinstance(tool_name, str) or not tool_name:
            raise AgentServiceError(
                502,
                "AI_TOOL_CALL_INVALID",
                "模型返回的工具名称格式异常",
                True,
            )
        if tool_name != SEARCH_COMMODITIES_TOOL["name"]:
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
            "type": "function_call_output",
            "call_id": call_id,
            "output": result.model_dump_json(),
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
    def _extract_output_items(response_data: dict) -> list[dict[str, Any]]:
        """
        OpenAI Responses 的输出
        直接返回文本时，包含常见附加字段的响应示例：
        {
          "id": "resp_...",
          "object": "response",
          "created_at": 1756315696,
          "status": "completed",
          "model": "gpt-5.6-terra",
          "output": [
            {
              "id": "rs_...",
              "type": "reasoning",
              "content": [],
              "summary": []
            },
            {
              "id": "msg_...",
              "type": "message",
              "status": "completed",
              "role": "assistant",
              "content": [
                {
                  "type": "output_text",
                  "text": "这里是最终回答",
                  "annotations": [],
                  "logprobs": []
                }
              ]
            }
          ],
          "usage": {
            "input_tokens": 1250,
            "output_tokens": 38,
            "total_tokens": 1288
          }
        }

        需要调用工具时，output 中会出现 function_call（reasoning 项可能存在）：
        [
            {
              "id": "rs_...",
              "type": "reasoning",
              "content": [],
              "summary": []
            },
            {
              "id": "fc_...",
              "type": "function_call",
              "status": "completed",
              "call_id": "call_...",
              "name": "search_commodities",
              "arguments": "{\"keywords\":[\"手机\"]}"
            }
        ]

        模型的整个 output 会加入下一次 input，工具执行结果再作为新 item 追加：
        {
          "type": "function_call_output",
          "call_id": "call_...",
          "output": "{\"matchedCount\":1,\"items\":[...]}"
        } 
        """
        output_items = response_data.get("output")
        if (
            not isinstance(output_items, list)
            or not all(isinstance(item, dict) for item in output_items)
        ):
            raise AgentServiceError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            )
        return output_items

    @staticmethod
    def _extract_answer(output_items: list[dict[str, Any]]) -> str:
        text_parts: list[str] = []
        for item in output_items:
            if item.get("type") != "message":
                continue
            content = item.get("content")
            if not isinstance(content, list):
                raise AgentServiceError(
                    502,
                    "AI_MODEL_RESPONSE_INVALID",
                    "模型返回内容格式异常",
                    True,
                )
            for part in content:
                if not isinstance(part, dict):
                    raise AgentServiceError(
                        502,
                        "AI_MODEL_RESPONSE_INVALID",
                        "模型返回内容格式异常",
                        True,
                    )
                if part.get("type") == "output_text":
                    text = part.get("text")
                    if not isinstance(text, str):
                        raise AgentServiceError(
                            502,
                            "AI_MODEL_RESPONSE_INVALID",
                            "模型返回内容格式异常",
                            True,
                        )
                    text_parts.append(text)

        answer = "".join(text_parts).strip()
        if not answer:
            raise AgentServiceError(502, "AI_MODEL_RESPONSE_INVALID",
                                    "模型没有返回回答内容", True)
        return answer

    @staticmethod
    def _build_summary(message: str) -> str:
        normalized_message = " ".join(message.split())
        return normalized_message[:80]
