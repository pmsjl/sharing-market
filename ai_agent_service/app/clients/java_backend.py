"""访问Java内部AI工具接口。"""

import httpx

from app.core.config import Settings
from app.models.tools import (
    CommoditySearchArguments,
    CommoditySearchToolResponse,
)


class JavaBackendClientError(Exception):
    """Java内部工具调用失败。"""

    def __init__(
        self,
        agent_error_key: str,
        message: str,
        retryable: bool,
    ) -> None:
        super().__init__(message)
        self.agent_error_key = agent_error_key
        self.message = message
        self.retryable = retryable


class JavaBackendClient:

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    async def search_commodities(
        self,
        request_id: str,
        arguments: CommoditySearchArguments,
    ) -> CommoditySearchToolResponse:
        url = (f"{self.settings.java_backend_base_url.rstrip('/')}"
               "/api/internal/ai/tools/commodities/search")

        try:
            async with httpx.AsyncClient(
                    timeout=self.settings.java_backend_timeout_seconds,
                    trust_env=False,
            ) as client:
                response = await client.post(
                    url,
                    headers={
                        "X-Internal-Token": (self.settings.internal_token),
                        "X-Request-Id": request_id,
                    },
                    json=arguments.model_dump(
                        mode="json",
                        exclude_none=True,
                    ),
                )

                response.raise_for_status()

        except httpx.TimeoutException as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_TIMEOUT",
                "Java商品搜索工具响应超时",
                True,
            ) from exception

        except httpx.HTTPStatusError as exception:
            status_code = exception.response.status_code

            if status_code in (401, 403):
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "Java商品搜索工具身份校验失败",
                    False,
                ) from exception

            if status_code == 400:
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "Java商品搜索工具拒绝了查询参数",
                    False,
                ) from exception

            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                f"Java商品搜索工具返回异常状态：{status_code}",
                status_code >= 500 or status_code == 429,
            ) from exception

        except httpx.HTTPError as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                "Java商品搜索工具暂不可用",
                True,
            ) from exception

        try:
            result = CommoditySearchToolResponse.model_validate(
                response.json())
        except (ValueError, TypeError) as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_RESPONSE_INVALID",
                "Java商品搜索工具返回结构异常",
                True,
            ) from exception

        if result.requestId != request_id:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_REQUEST_ID_MISMATCH",
                "Java商品搜索工具返回的requestId不一致",
                True,
            )

        return result
