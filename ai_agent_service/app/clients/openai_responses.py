"""OpenAI Responses 兼容接口客户端。"""

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
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.settings = settings
        self.transport = transport

    async def create_response(
        self,
        input_items: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> dict[str, Any]:
        payload = {
            "model": self.settings.openai_model,
            "input": input_items,
            "tools": tools,
            "tool_choice": "auto",
            "reasoning": {
                "effort": self.settings.openai_reasoning_effort,
            },
            "store": False,
            # OpenAI 官方默认非流式，但部分兼容中转在省略时会返回 SSE。
            # 本服务只实现同步 JSON 契约，因此必须显式关闭流式输出。
            "stream": False,
        }

        try:
            async with httpx.AsyncClient(
                    timeout=self.settings.openai_timeout_seconds,
                    transport=self.transport,
            ) as client:
                response = await client.post(
                    f"{self.settings.openai_base_url.rstrip('/')}/responses",
                    headers={
                        "Authorization":
                        f"Bearer {self.settings.openai_api_key}",
                        "Content-Type": "application/json",
                        # 避免部分中转的 Cloudflare 规则拦截 httpx 默认标识。
                        "User-Agent": "sharing-market-ai-agent/0.1",
                        "Accept": "application/json",
                    },
                    json=payload,
                )
                response.raise_for_status()
        except httpx.TimeoutException as exception:
            raise OpenAIResponsesClientError(
                504,
                "AI_MODEL_TIMEOUT",
                "模型响应超时",
                True,
            ) from exception
        except httpx.HTTPStatusError as exception:
            raise self._map_status_error(exception) from exception
        except httpx.HTTPError as exception:
            raise OpenAIResponsesClientError(
                503,
                "AI_MODEL_UNAVAILABLE",
                "模型服务暂不可用",
                True,
            ) from exception

        response_data = self._parse_response_data(response)

        if not isinstance(response_data, dict):
            raise OpenAIResponsesClientError(
                502,
                "AI_MODEL_RESPONSE_INVALID",
                "模型返回内容格式异常",
                True,
            )
        return response_data


#使用openai中转出现内容不兼容，
#以下为对中转输出的内容进行解析提取实际格式对应的内容，无需重点关注

    @classmethod
    def _parse_response_data(cls, response: httpx.Response) -> dict[str, Any]:
        """兼容普通 JSON，以及忽略 stream=false 的中转所返回的缓冲 SSE。"""
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
