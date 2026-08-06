import asyncio

import httpx
import pytest

from app.core.config import Settings
from app.rag import embedding_client as embedding_module
from app.rag.embedding_client import EmbeddingClient


def _settings() -> Settings:
    return Settings(
        embedding_base_url="https://embedding.example/v1/",
        embedding_api_key="test-key",
        embedding_model="test-embedding",
        embedding_dimensions=2,
    )


def test_validate_sorts_valid_out_of_order_indexes():
    client = EmbeddingClient(_settings())

    assert client._validate(
        {
            "data": [
                {"index": 1, "embedding": [0.0, 1.0]},
                {"index": 0, "embedding": [1.0, 0.0]},
            ]
        },
        2,
    ) == [[1.0, 0.0], [0.0, 1.0]]


@pytest.mark.parametrize(
    "data",
    [
        [{"index": 0, "embedding": [1.0, 0.0]}],
        [
            {"index": 0, "embedding": [1.0, 0.0]},
            {"index": 0, "embedding": [0.0, 1.0]},
        ],
        [
            {"index": 0, "embedding": [1.0, 0.0]},
            {"index": 2, "embedding": [0.0, 1.0]},
        ],
    ],
)
def test_validate_rejects_missing_or_duplicate_indexes(data):
    # 数量不足会先触发数量校验；重复或越界序号会触发后续排列完整性校验。
    with pytest.raises(ValueError):
        EmbeddingClient(_settings())._validate({"data": data}, 2)


@pytest.mark.parametrize(
    "vector",
    [[1.0], [float("nan"), 0.0], [float("inf"), 0.0], [True, 0.0]],
)
def test_validate_rejects_invalid_vectors(vector):
    with pytest.raises(ValueError):
        EmbeddingClient(_settings())._validate(
            {"data": [{"index": 0, "embedding": vector}]}, 1
        )


class _Response:
    def __init__(self, status_code: int, payload: dict | None = None) -> None:
        self.status_code = status_code
        self._payload = payload or {}

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            request = httpx.Request("POST", "https://embedding.example/v1/embeddings")
            response = httpx.Response(self.status_code, request=request)
            raise httpx.HTTPStatusError("failure", request=request, response=response)

    def json(self) -> dict:
        return self._payload


class _AsyncClient:
    def __init__(self, responses: list[_Response]) -> None:
        self.responses = responses
        self.calls = 0
        self.urls: list[str] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    async def post(self, url: str, **kwargs):
        self.calls += 1
        self.urls.append(url)
        return self.responses.pop(0)


def test_retries_429_but_not_400(monkeypatch):
    async def no_sleep(_: float) -> None:
        return None

    retry_client = _AsyncClient(
        [
            _Response(429),
            _Response(200, {"data": [{"index": 0, "embedding": [1.0, 0.0]}]}),
        ]
    )
    monkeypatch.setattr(embedding_module.httpx, "AsyncClient", lambda **_: retry_client)
    monkeypatch.setattr(embedding_module.asyncio, "sleep", no_sleep)

    assert asyncio.run(EmbeddingClient(_settings()).embed_one("query")) == [1.0, 0.0]
    assert retry_client.calls == 2
    assert retry_client.urls == [
        "https://embedding.example/v1/embeddings",
        "https://embedding.example/v1/embeddings",
    ]

    bad_request_client = _AsyncClient([_Response(400)])
    monkeypatch.setattr(embedding_module.httpx, "AsyncClient", lambda **_: bad_request_client)
    with pytest.raises(httpx.HTTPStatusError):
        asyncio.run(EmbeddingClient(_settings()).embed_one("query"))
    assert bad_request_client.calls == 1
