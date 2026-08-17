import asyncio
import json
from urllib.parse import parse_qs

import httpx
import pytest

from app.clients import java_backend as java_backend_module
from app.clients.java_backend import JavaBackendClient, JavaBackendClientError
from app.core.config import Settings
from app.rag.models import PostVersionCandidate


def _settings(**overrides) -> Settings:
    values = {
        "internal_token": "internal-token",
        "java_backend_base_url": "http://127.0.0.1:8102/",
        "java_backend_timeout_seconds": 10,
        "rag_post_snapshot_page_size": 2,
    }
    values.update(overrides)
    return Settings(**values)


def _post(post_id: int) -> dict:
    return {
        "id": post_id,
        "title": f"帖子 {post_id}",
        "content": "这是一篇用于测试离线快照分页的校园交易经验帖。",
        "tags": ["校园", "验货"],
        "createTime": "2026-08-15 20:22:00",
        "updateTime": "2026-08-15 20:23:00",
        "sourceVersion": str(1_786_796_580_000 + post_id),
    }


def _mock_client(monkeypatch, responses: list[dict | int]):
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        response = responses.pop(0)
        if isinstance(response, int):
            return httpx.Response(response)
        return httpx.Response(200, json=response)

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    monkeypatch.setattr(
        java_backend_module.httpx,
        "AsyncClient",
        lambda **_: client,
    )
    return captured


def test_fetch_post_snapshot_reads_all_pages_and_uses_scanned_cursor(monkeypatch):
    captured = _mock_client(
        monkeypatch,
        [
            {
                "items": [_post(1)],
                "nextAfterId": 2,
                "hasMore": True,
            },
            {
                "items": [_post(3)],
                "nextAfterId": 3,
                "hasMore": False,
            },
        ],
    )

    posts = asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert [item.id for item in posts] == [1, 3]
    assert [request.method for request in captured] == ["GET", "GET"]
    assert all(
        request.url.path == "/api/internal/ai/rag/posts"
        for request in captured
    )
    assert [parse_qs(request.url.query.decode()) for request in captured] == [
        {"afterId": ["0"], "limit": ["2"]},
        {"afterId": ["2"], "limit": ["2"]},
    ]
    assert all(
        request.headers["X-Internal-Token"] == "internal-token"
        for request in captured
    )
    assert all("X-Request-Id" not in request.headers for request in captured)


def test_fetch_post_snapshot_allows_filtered_empty_page(monkeypatch):
    _mock_client(
        monkeypatch,
        [
            {"items": [], "nextAfterId": 5, "hasMore": True},
            {
                "items": [_post(6)],
                "nextAfterId": 6,
                "hasMore": False,
            },
        ],
    )

    posts = asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert [item.id for item in posts] == [6]


def test_fetch_post_snapshot_rejects_duplicate_id_across_pages(monkeypatch):
    _mock_client(
        monkeypatch,
        [
            {
                "items": [_post(2)],
                "nextAfterId": 2,
                "hasMore": True,
            },
            {
                "items": [_post(2)],
                "nextAfterId": 3,
                "hasMore": False,
            },
        ],
    )

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == "AI_JAVA_RAG_SNAPSHOT_DUPLICATE_ID"
    assert raised.value.retryable is False


def test_fetch_post_snapshot_rejects_stalled_cursor(monkeypatch):
    _mock_client(
        monkeypatch,
        [{"items": [], "nextAfterId": 0, "hasMore": True}],
    )

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == "AI_JAVA_RAG_SNAPSHOT_CURSOR_STALLED"


@pytest.mark.parametrize(
    ("page", "expected_key"),
    [
        (
            {
                "items": [_post(2), _post(1)],
                "nextAfterId": 2,
                "hasMore": False,
            },
            "AI_JAVA_RAG_SNAPSHOT_ORDER_INVALID",
        ),
        (
            {
                "items": [_post(3)],
                "nextAfterId": 2,
                "hasMore": False,
            },
            "AI_JAVA_RAG_SNAPSHOT_CURSOR_INVALID",
        ),
    ],
)
def test_fetch_post_snapshot_rejects_invalid_page_window(
    monkeypatch,
    page,
    expected_key,
):
    _mock_client(monkeypatch, [page])

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == expected_key


def test_fetch_post_snapshot_rejects_invalid_response_shape(monkeypatch):
    invalid_post = _post(1)
    invalid_post["sourceVersion"] = "not-a-version"
    _mock_client(
        monkeypatch,
        [{
            "items": [invalid_post],
            "nextAfterId": 1,
            "hasMore": False,
        }],
    )

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert (
        raised.value.agent_error_key
        == "AI_JAVA_RAG_SNAPSHOT_RESPONSE_INVALID"
    )


def test_fetch_post_snapshot_maps_unauthorized_status(monkeypatch):
    _mock_client(monkeypatch, [401])

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == "AI_JAVA_RAG_SNAPSHOT_UNAUTHORIZED"
    assert raised.value.retryable is False


@pytest.mark.parametrize(
    ("status_code", "expected_key", "retryable"),
    [
        (400, "AI_JAVA_RAG_SNAPSHOT_ARGUMENTS_INVALID", False),
        (429, "AI_JAVA_RAG_SNAPSHOT_UNAVAILABLE", True),
        (503, "AI_JAVA_RAG_SNAPSHOT_UNAVAILABLE", True),
    ],
)
def test_fetch_post_snapshot_maps_other_http_statuses(
    monkeypatch,
    status_code,
    expected_key,
    retryable,
):
    _mock_client(monkeypatch, [status_code])

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == expected_key
    assert raised.value.retryable is retryable


def test_fetch_post_snapshot_maps_timeout(monkeypatch):
    class TimeoutClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def get(self, url, **kwargs):
            request = httpx.Request("GET", url)
            raise httpx.ReadTimeout("timeout", request=request)

    monkeypatch.setattr(
        java_backend_module.httpx,
        "AsyncClient",
        lambda **_: TimeoutClient(),
    )

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(JavaBackendClient(_settings()).fetch_post_snapshot())

    assert raised.value.agent_error_key == "AI_JAVA_RAG_SNAPSHOT_TIMEOUT"
    assert raised.value.retryable is True


def test_fetch_post_snapshot_validates_local_page_size_before_http(monkeypatch):
    def fail_client(**_):
        raise AssertionError("invalid configuration must not create HTTP client")

    monkeypatch.setattr(java_backend_module.httpx, "AsyncClient", fail_client)

    with pytest.raises(ValueError, match="RAG_POST_SNAPSHOT_PAGE_SIZE"):
        asyncio.run(
            JavaBackendClient(
                _settings(rag_post_snapshot_page_size=201)
            ).fetch_post_snapshot()
        )


def test_validate_post_versions_sends_request_identity_and_returns_whitelist(
    monkeypatch,
):
    captured = _mock_client(
        monkeypatch,
        [{
            "requestId": "request-7",
            "validCandidates": [
                {"postId": 11, "sourceVersion": "1786796580000"}
            ],
        }],
    )
    candidates = [
        PostVersionCandidate(postId=11, sourceVersion="1786796580000"),
        PostVersionCandidate(postId=12, sourceVersion="1786796580001"),
    ]

    valid = asyncio.run(
        JavaBackendClient(_settings()).validate_post_versions(
            "request-7",
            candidates,
        )
    )

    assert valid == {("11", "1786796580000")}
    request = captured[0]
    assert request.url.path == "/api/internal/ai/tools/posts/validate"
    assert request.headers["X-Internal-Token"] == "internal-token"
    assert request.headers["X-Request-Id"] == "request-7"
    assert json.loads(request.content) == {
        "candidates": [
            {"postId": 11, "sourceVersion": "1786796580000"},
            {"postId": 12, "sourceVersion": "1786796580001"},
        ]
    }


def test_validate_post_versions_rejects_unrequested_candidate(monkeypatch):
    _mock_client(
        monkeypatch,
        [{
            "requestId": "request-8",
            "validCandidates": [
                {"postId": 99, "sourceVersion": "1786796580099"}
            ],
        }],
    )

    with pytest.raises(JavaBackendClientError) as raised:
        asyncio.run(
            JavaBackendClient(_settings()).validate_post_versions(
                "request-8",
                [PostVersionCandidate(
                    postId=11,
                    sourceVersion="1786796580000",
                )],
            )
        )

    assert raised.value.agent_error_key == "AI_JAVA_TOOL_RESPONSE_INVALID"
