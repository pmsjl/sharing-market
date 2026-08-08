"""独立的 OpenAI 兼容 ``/embeddings`` 客户端。

这里不复用 ``OpenAIResponsesClient.create_response()``，因为生成请求携带的
工具、文本和推理字段不是 Embedding 端点可以接受的参数。
"""

from __future__ import annotations

import asyncio
import math
from collections.abc import Mapping
from typing import Any

import httpx
import numpy as np

from app.core.config import Settings

_TIMEOUT_SECONDS = 30
_NON_RETRYABLE_STATUS_CODES = {400, 401, 403}


def l2_normalize(vector: list[float] | np.ndarray) -> np.ndarray:
    """返回 float32 单位向量，使内积等价于余弦相似度。"""
    array = np.asarray(vector, dtype=np.float32)
    norm = np.linalg.norm(array)
    if norm == 0:
        return array
    return array / norm


class EmbeddingClient:
    """带响应结构校验和有限重试的分批 Embedding 客户端。"""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    async def embed_one(self, text: str) -> list[float]:
        """复用批量客户端的全部校验规则，为单条查询生成向量。"""
        return (await self.embed_batch([text]))[0]

    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """按固定大小分批，避免重建索引时产生过大的单次请求。"""
        vectors: list[list[float]] = []
        _BATCH_SIZE = self.settings.embedding_batch_size
        for start in range(0, len(texts), _BATCH_SIZE):
            vectors.extend(await self._embed_batch_once(texts[start:start +
                                                              _BATCH_SIZE]))
        return vectors

    async def _embed_batch_once(self, batch: list[str]) -> list[list[float]]:
        url = f"{self.settings.embedding_base_url.rstrip('/')}/embeddings"
        headers = {
            "Authorization": f"Bearer {self.settings.embedding_api_key}"
        }
        payload = {
            "model": self.settings.embedding_model,
            "input": batch,
            "dimensions": self.settings.embedding_dimensions,
        }
        async with httpx.AsyncClient(timeout=_TIMEOUT_SECONDS) as client:
            for attempt in range(3):
                try:
                    response = await client.post(url,
                                                 headers=headers,
                                                 json=payload)
                    response.raise_for_status()
                    return self._validate(response.json(), len(batch))
                except (httpx.HTTPStatusError, httpx.TransportError) as exc:
                    status_code = getattr(getattr(exc, "response", None),
                                          "status_code", None)
                    if status_code in _NON_RETRYABLE_STATUS_CODES or attempt == 2:
                        raise
                    # 只重试临时网络错误、429 和 5xx；等待时间依次为 1 秒、2 秒。
                    await asyncio.sleep(2**attempt)

        raise AssertionError("embedding retry loop must return or raise")

    def _validate(
        self,
        payload: Mapping[str, Any],
        # Mapping就是dict父类
        # 所以直接这么写就可以
        expected_count: int,
    ) -> list[list[float]]:
        """校验响应结构、完整输入序号、向量维度和数值有限性。
        {
            "object": "list",
            "data": [
                {
                    "object": "embedding",
                    "embedding": [
                        0.0123,
                        -0.0234,
                        0.0567
                    ],
                    "index": 0
                },
                {
                    "object": "embedding",
                    "embedding": [
                        0.0345,
                        0.0789,
                        -0.0112
                    ],
                    "index": 1
                }
            ],
            "model": "text-embedding-v4",
            "usage": {
                "prompt_tokens": 10,
                "total_tokens": 10
            }
        }
        """
        items = payload.get("data")
        if not isinstance(items, list):
            raise ValueError("embeddings: data 不是数组")
        if len(items) != expected_count:
            raise ValueError(
                f"embeddings: 数量不符 {len(items)} != {expected_count}")

        expected_indexes = set(range(expected_count))
        indexed_items: list[tuple[int, Mapping[str, Any]]] = []
        for item in items:
            if not isinstance(item, Mapping):
                raise ValueError("embeddings: data 元素不是对象")
            index = item.get("index")
            if isinstance(index, bool) or not isinstance(index, int):
                raise ValueError("embeddings: index 必须是整数")
            indexed_items.append((index, item))
        actual_indexes = {index for index, _ in indexed_items}
        if actual_indexes != expected_indexes:
            raise ValueError("embeddings: index 必须完整且不能重复")

        vectors: list[list[float]] = []
        for _, item in sorted(indexed_items, key=lambda pair: pair[0]):
            vector = item.get("embedding")
            if (not isinstance(vector, list)
                    or len(vector) != self.settings.embedding_dimensions):
                raise ValueError(
                    f"embeddings: 维度不符 != {self.settings.embedding_dimensions}"
                )
            if any(
                    isinstance(value, bool) or not isinstance(
                        value, (int, float)) or not math.isfinite(value)
                    for value in vector):
                raise ValueError("embeddings: 向量含非数字或非有限值")
            vectors.append(vector)
        return vectors
