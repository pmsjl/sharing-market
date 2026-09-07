"""OpenAI Responses 兼容接口客户端。"""

import asyncio
import json
from typing import Any

import httpx

from app.core.config import Settings


class OpenAIResponsesClientError(Exception):
    """可由 AgentService 映射为内部 Agent 错误的模型客户端异常。"""

    def __init__(
        self,
        status_code: int,
        agent_error_key: str,
        message: str,
        retryable: bool,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.agent_error_key = agent_error_key
        self.message = message
        self.retryable = retryable


class OpenAIResponsesClient:
    """通过 httpx 调用 OpenAI Responses 兼容中转服务。"""

    def __init__(
        self,
        settings: Settings,
    ) -> None:
        self.settings = settings

    async def create_response(
        self,
        input_items: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        text_format: dict[str, Any],
    ) -> dict[str, Any]:
        payload = {
            "model": self.settings.openai_model,
            "input": input_items,
            "tools": tools,
            "tool_choice": "auto",
            "reasoning": {
                "effort": self.settings.openai_reasoning_effort,
            },
            "text": {
                "verbosity": self.settings.openai_text_verbosity,
                "format": text_format,
                #利用json schema实现结构化输出
            },
            "store": False,
            # 上游以 SSE 返回事件；本客户端仍会缓冲到 response.completed，
            # 再向 AgentService 提供完整 JSON 契约。
            "stream": True,
        }

        return await self._post_payload(
            payload,
            timeout_seconds=self.settings.openai_timeout_seconds,
        )

    async def create_router_response(
        self,
        input_items: list[dict[str, Any]],
        text_format: dict[str, Any],
    ) -> dict[str, Any]:
        """调用独立可配置的只读意图Router，不向模型暴露任何工具。"""
        payload = {
            "model": self.settings.openai_router_model,
            "input": input_items,
            "tools": [],
            "tool_choice": "none",
            "reasoning": {
                "effort": self.settings.openai_router_reasoning_effort,
            },
            "text": {
                "verbosity": self.settings.openai_router_text_verbosity,
                "format": text_format,
            },
            "store": False,
            "stream": True,
        }
        return await self._post_payload(
            payload,
            timeout_seconds=self.settings.openai_router_timeout_seconds,
        )

    async def _post_payload(
        self,
        payload: dict[str, Any],
        *,
        timeout_seconds: float,
    ) -> dict[str, Any]:
        """发送并解析请求；只对尚未产生有效结果的临时故障重试一次。"""
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            for attempt in range(2):
                try:
                    response = await client.post(
                        f"{self.settings.openai_base_url.rstrip('/')}/responses",
                        headers={
                            "Authorization": f"Bearer {self.settings.openai_api_key}",
                            "Content-Type": "application/json",
                            # 保留 httpx 默认标识；自定义标识会被当前中转网关拒绝。
                            "Accept": "text/event-stream",
                        },
                        json=payload,
                    )
                    response.raise_for_status()
                    response_data = self._parse_response_data(response)
                    self._raise_for_embedded_error(response_data)
                    return response_data
                except httpx.TimeoutException as exception:
                    # 超时后的上游执行状态不明确，自动重试可能重复计费。
                    raise OpenAIResponsesClientError(
                        504, "AI_MODEL_TIMEOUT", "模型响应超时", True
                    ) from exception
                except httpx.HTTPStatusError as exception:
                    error = self._map_status_error(exception)
                except httpx.HTTPError as exception:
                    error = OpenAIResponsesClientError(
                        503, "AI_MODEL_UNAVAILABLE", "模型服务暂不可用", True
                    )
                    error.__cause__ = exception
                except OpenAIResponsesClientError as exception:
                    error = exception

                if attempt == 1 or not self._can_retry(error):
                    raise error
                await asyncio.sleep(1)

        raise AssertionError("model retry loop must return or raise")

    @staticmethod
    def _can_retry(error: OpenAIResponsesClientError) -> bool:
        return error.agent_error_key in {
            "AI_MODEL_OVERLOADED",
            "AI_MODEL_RATE_LIMITED",
            "AI_MODEL_UNAVAILABLE",
        }

    @classmethod
    def _raise_for_embedded_error(cls, response_data: dict[str, Any]) -> None:
        """识别中转将上游错误包装为 HTTP 200 的非标准响应。"""
        error = response_data.get("error")
        if not isinstance(error, dict):
            return
        message = error.get("message")
        if not isinstance(message, str) or not message.strip():
            return
        raise cls._map_embedded_error(error)

    @staticmethod
    def _map_embedded_error(error: dict[str, Any]) -> OpenAIResponsesClientError:
        message = str(error.get("message") or "").lower()
        code = str(error.get("code") or "").lower()
        if ("overload" in message or "try again later" in message
                or code in {"server_error", "overloaded"}):
            return OpenAIResponsesClientError(
                503,
                "AI_MODEL_OVERLOADED",
                "模型服务当前繁忙",
                True,
            )
        return OpenAIResponsesClientError(
            502,
            "AI_MODEL_REQUEST_REJECTED",
            "模型服务返回错误",
            False,
        )


#使用openai中转出现内容不兼容，
#以下为对中转输出的内容进行解析提取实际格式对应的内容，无需重点关注

    @classmethod
    def _parse_response_data(cls, response: httpx.Response) -> dict[str, Any]:
        """兼容普通 JSON，以及中转返回的缓冲 SSE。"""
        content_type = response.headers.get("content-type", "").lower()
        response_text = response.text
        if ("text/event-stream" in content_type
                or response_text.lstrip().startswith(("event:", "data:"))):
            return cls._parse_completed_sse_response(response_text)

        try:
            response_data = response.json()
        except ValueError as exception:
            raise OpenAIResponsesClientError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            ) from exception
        if not isinstance(response_data, dict):
            raise OpenAIResponsesClientError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            )
        return response_data

    @staticmethod
    def _parse_completed_sse_response(response_text: str) -> dict[str, Any]:
        completed_response: dict[str, Any] | None = None
        failed_error: dict[str, Any] | None = None
        completed_items: dict[int, dict[str, Any]] = {}
        for line in response_text.splitlines():
            stripped_line = line.strip()
            if not stripped_line.startswith("data:"):
                continue
            raw_data = stripped_line.removeprefix("data:").strip()
            if not raw_data or raw_data == "[DONE]":
                continue
            try:
                event_data = json.loads(raw_data)
            except json.JSONDecodeError:
                continue
            if not isinstance(event_data, dict):
                continue

            event_type = event_data.get("type")
            if event_type == "error":
                failed_error = event_data
                continue
            if event_type in {"response.failed", "response.incomplete"}:
                event_response = event_data.get("response")
                if isinstance(event_response, dict):
                    event_error = event_response.get("error")
                    if isinstance(event_error, dict):
                        failed_error = event_error
                continue
            if event_type == "response.output_item.done":
                output_index = event_data.get("output_index")
                output_item = event_data.get("item")
                if (isinstance(output_index, int)
                        and isinstance(output_item, dict)):
                    completed_items[output_index] = output_item
                continue

            event_response = event_data.get("response")
            if (event_type == "response.completed"
                    and isinstance(event_response, dict)):
                completed_response = event_response
                continue
            if (event_data.get("object") == "response"
                    and event_data.get("status") == "completed"):
                completed_response = event_data

        if completed_response is None:
            if failed_error is not None:
                raise OpenAIResponsesClient._map_embedded_error(failed_error)
            raise OpenAIResponsesClientError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回的流式内容未正常完成",
                True,
            )

        response_output = completed_response.get("output")
        if ((not isinstance(response_output, list) or not response_output)
                and completed_items):
            completed_response = dict(completed_response)
            completed_response["output"] = [
                completed_items[index] for index in sorted(completed_items)
            ]
        return completed_response

    @staticmethod
    def _map_status_error(
        exception: httpx.HTTPStatusError, ) -> OpenAIResponsesClientError:
        status_code = exception.response.status_code
        if status_code in (401, 403):
            return OpenAIResponsesClientError(
                503,
                "AI_MODEL_AUTH_FAILED",
                "模型服务认证失败",
                False,
            )
        if status_code == 429:
            return OpenAIResponsesClientError(
                503,
                "AI_MODEL_RATE_LIMITED",
                "模型服务请求过于频繁",
                True,
            )
        if 400 <= status_code < 500:
            return OpenAIResponsesClientError(
                503,
                "AI_MODEL_REQUEST_REJECTED",
                "模型服务拒绝了当前请求",
                False,
            )
        return OpenAIResponsesClientError(
            503,
            "AI_MODEL_UNAVAILABLE",
            "模型服务暂不可用",
            True,
        )
