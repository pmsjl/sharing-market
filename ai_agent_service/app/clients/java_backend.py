"""访问Java内部AI工具接口。"""

import httpx

from app.core.config import Settings
from app.models.tools import (
    CommoditySearchArguments,
    CommoditySearchToolResponse,
    UserPreferenceToolResponse,
)
from app.rag.models import (
    PostSnapshot,
    PostSnapshotPage,
    PostVersionCandidate,
    PostVersionValidationResponse,
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

    async def get_my_preference_signals(
        self,
        request_id: str,
        user_id: int,
    ) -> UserPreferenceToolResponse:
        url = (
            f"{self.settings.java_backend_base_url.rstrip('/')}"
            f"/api/internal/ai/tools/users/{user_id}/preference-signals"
        )

        try:
            async with httpx.AsyncClient(
                timeout=self.settings.java_backend_timeout_seconds,
                trust_env=False,
            ) as client:
                response = await client.get(
                    url,
                    headers={
                        "X-Internal-Token": self.settings.internal_token,
                        "X-Request-Id": request_id,
                    },
                )
                response.raise_for_status()
        except httpx.TimeoutException as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_TIMEOUT",
                "Java 用户偏好工具响应超时",
                True,
            ) from exception
        except httpx.HTTPStatusError as exception:
            status_code = exception.response.status_code
            if status_code in (401, 403):
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "Java 用户偏好工具身份校验失败",
                    False,
                ) from exception
            if status_code == 400:
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "Java 用户偏好工具拒绝了请求参数",
                    False,
                ) from exception
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                f"Java 用户偏好工具返回异常状态：{status_code}",
                status_code >= 500 or status_code == 429,
            ) from exception
        except httpx.HTTPError as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                "Java 用户偏好工具暂不可用",
                True,
            ) from exception

        try:
            result = UserPreferenceToolResponse.model_validate(
                response.json()
            )
        except (ValueError, TypeError) as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_RESPONSE_INVALID",
                "Java 用户偏好工具返回结构异常",
                True,
            ) from exception

        if result.requestId != request_id:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_REQUEST_ID_MISMATCH",
                "Java 用户偏好工具返回的 requestId 不一致",
                True,
            )

        return result

    async def fetch_post_snapshot(self) -> list[PostSnapshot]:
        """完整读取 Java Post 快照；任何分页异常都会终止离线重建。"""
        page_size = self.settings.rag_post_snapshot_page_size
        if page_size < 1 or page_size > 200:
            raise ValueError("RAG_POST_SNAPSHOT_PAGE_SIZE 必须在 1 到 200 之间")
        if not self.settings.internal_token.strip():
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_UNAUTHORIZED",
                "Post 快照内部 Token 未配置",
                False,
            )

        url = (
            f"{self.settings.java_backend_base_url.rstrip('/')}"
            "/api/internal/ai/rag/posts"
        )
        after_id = 0
        posts: list[PostSnapshot] = []
        seen_ids: set[int] = set()

        async with httpx.AsyncClient(
            timeout=self.settings.java_backend_timeout_seconds,
            trust_env=False,
        ) as client:
            while True:
                try:
                    response = await client.get(
                        url,
                        headers={
                            "X-Internal-Token": self.settings.internal_token,
                        },
                        params={
                            "afterId": after_id,
                            "limit": page_size,
                        },
                    )
                    response.raise_for_status()
                except httpx.TimeoutException as exception:
                    raise JavaBackendClientError(
                        "AI_JAVA_RAG_SNAPSHOT_TIMEOUT",
                        "Java Post 快照接口响应超时",
                        True,
                    ) from exception
                except httpx.HTTPStatusError as exception:
                    self._raise_post_snapshot_status_error(exception)
                except httpx.HTTPError as exception:
                    raise JavaBackendClientError(
                        "AI_JAVA_RAG_SNAPSHOT_UNAVAILABLE",
                        "Java Post 快照接口暂不可用",
                        True,
                    ) from exception

                try:
                    post_page = PostSnapshotPage.model_validate(response.json())
                except (ValueError, TypeError) as exception:
                    raise JavaBackendClientError(
                        "AI_JAVA_RAG_SNAPSHOT_RESPONSE_INVALID",
                        "Java Post 快照接口返回结构异常",
                        False,
                    ) from exception

                self._validate_post_snapshot_page(post_page, after_id, seen_ids)
                posts.extend(post_page.items)
                seen_ids.update(item.id for item in post_page.items)

                if not post_page.has_more:
                    return posts
                after_id = post_page.next_after_id

    def _raise_post_snapshot_status_error(
        self,
        exception: httpx.HTTPStatusError,
    ) -> None:
        status_code = exception.response.status_code
        if status_code in (401, 403):
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_UNAUTHORIZED",
                "Java Post 快照接口身份校验失败",
                False,
            ) from exception
        if status_code == 400:
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_ARGUMENTS_INVALID",
                "Java Post 快照接口拒绝了分页参数",
                False,
            ) from exception
        raise JavaBackendClientError(
            "AI_JAVA_RAG_SNAPSHOT_UNAVAILABLE",
            f"Java Post 快照接口返回异常状态：{status_code}",
            status_code >= 500 or status_code == 429,
        ) from exception

    def _validate_post_snapshot_page(
        self,
        post_page: PostSnapshotPage,
        after_id: int,
        seen_ids: set[int],
    ) -> None:
        if post_page.next_after_id < after_id:
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_CURSOR_INVALID",
                "Post 快照分页游标发生倒退",
                False,
            )
        if post_page.has_more and post_page.next_after_id <= after_id:
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_CURSOR_STALLED",
                "Post 快照分页游标没有向前推进",
                False,
            )

        post_page_ids = [item.id for item in post_page.items]
        post_page_seen: set[int] = set()
        for post_id in post_page_ids:
            if post_id in seen_ids or post_id in post_page_seen:
                raise JavaBackendClientError(
                    "AI_JAVA_RAG_SNAPSHOT_DUPLICATE_ID",
                    f"Post 快照包含重复 ID：{post_id}",
                    False,
                )
            post_page_seen.add(post_id)

        if post_page_ids != sorted(post_page_ids):
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_ORDER_INVALID",
                "Post 快照页内 ID 未按升序返回",
                False,
            )
        if any(
            post_id <= after_id or post_id > post_page.next_after_id
            for post_id in post_page_ids
        ):
            raise JavaBackendClientError(
                "AI_JAVA_RAG_SNAPSHOT_CURSOR_INVALID",
                "Post 快照 ID 不在当前游标窗口内",
                False,
            )

    async def validate_post_versions(
        self,
        request_id: str,
        candidates: list[PostVersionCandidate],
    ) -> set[tuple[str, str]]:
        """请求内实时确认索引中的 Post 仍存在且版本未变化。"""
        if not candidates:
            return set()
        if len(candidates) > 10:
            raise ValueError("Post 版本校验候选最多 10 项")

        url = (
            f"{self.settings.java_backend_base_url.rstrip('/')}"
            "/api/internal/ai/tools/posts/validate"
        )
        try:
            async with httpx.AsyncClient(
                timeout=self.settings.java_backend_timeout_seconds,
                trust_env=False,
            ) as client:
                response = await client.post(
                    url,
                    headers={
                        "X-Internal-Token": self.settings.internal_token,
                        "X-Request-Id": request_id,
                    },
                    json={
                        "candidates": [
                            candidate.model_dump(mode="json", by_alias=True)
                            for candidate in candidates
                        ],
                    },
                )
                response.raise_for_status()
        except httpx.TimeoutException as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_TIMEOUT",
                "Java Post 版本校验响应超时",
                True,
            ) from exception
        except httpx.HTTPStatusError as exception:
            status_code = exception.response.status_code
            if status_code in (401, 403):
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "Java Post 版本校验身份校验失败",
                    False,
                ) from exception
            if status_code == 400:
                raise JavaBackendClientError(
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "Java Post 版本校验拒绝了请求参数",
                    False,
                ) from exception
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                f"Java Post 版本校验返回异常状态：{status_code}",
                status_code >= 500 or status_code == 429,
            ) from exception
        except httpx.HTTPError as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_UNAVAILABLE",
                "Java Post 版本校验暂不可用",
                True,
            ) from exception

        try:
            result = PostVersionValidationResponse.model_validate(
                response.json()
            )
        except (ValueError, TypeError) as exception:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_RESPONSE_INVALID",
                "Java Post 版本校验返回结构异常",
                False,
            ) from exception
        if result.request_id != request_id:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_REQUEST_ID_MISMATCH",
                "Java Post 版本校验返回的 requestId 不一致",
                False,
            )

        requested = {
            (str(candidate.post_id), candidate.source_version)
            for candidate in candidates
        }
        valid = {
            (str(candidate.post_id), candidate.source_version)
            for candidate in result.valid_candidates
        }
        if len(valid) != len(result.valid_candidates) or not valid <= requested:
            raise JavaBackendClientError(
                "AI_JAVA_TOOL_RESPONSE_INVALID",
                "Java Post 版本校验返回了重复或未请求的候选",
                False,
            )
        return valid
