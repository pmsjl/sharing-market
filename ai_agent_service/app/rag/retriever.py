"""按精确父文档、优先类别、兜底类别依次补足检索结果。"""

import numpy as np

from app.core.config import Settings
from app.rag.embedding_client import EmbeddingClient, l2_normalize
from app.rag.index_store import IndexStore
from app.rag.models import RagQueryPlan, RetrievedChunk


class Retriever:

    def __init__(
        self,
        settings: Settings,
        embedding_client: EmbeddingClient,
        index_store: IndexStore | None,
    ) -> None:
        self.settings = settings
        self.embedding_client = embedding_client
        self.index_store = index_store
        self.ready = index_store is not None

    @classmethod
    def load(cls, settings: Settings) -> "Retriever":
        return cls(settings, EmbeddingClient(settings),
                   IndexStore.load(settings))

    #加了property就是相当于原本是retriever.chunks()
    #现在少加个括号，变成retriever.chunks
    @property
    def chunks(self):
        return self.index_store.chunks

    @property
    def vectors(self) -> np.ndarray:
        return self.index_store.vectors

    @property
    def index(self):
        return self.index_store.index

    async def retrieve(
        self,
        query: str,
        plan: RagQueryPlan,
    ) -> list[RetrievedChunk]:
        if not self.ready or not plan.should_retrieve:
            return []

        query_vector = l2_normalize(await
                                    self.embedding_client.embed_one(query))
        has_scopes = bool(plan.course_document_ids or plan.extra_categories
                          or plan.fallback_categories)
        if not has_scopes:
            return self._retrieve_unfiltered(query_vector)

        course_document_ids = set(plan.course_document_ids)
        extra_categories = set(plan.extra_categories)
        fallback_categories = set(plan.fallback_categories)

        course_chunk_rows = [
            row for row, chunk in enumerate(self.chunks)
            if chunk.document_id in course_document_ids
        ]
        exact_row_set = set(course_chunk_rows)
        extra_rows = [
            row for row, chunk in enumerate(self.chunks)
            if row not in exact_row_set and chunk.category in extra_categories
        ]
        primary_row_set = exact_row_set | set(extra_rows)
        fallback_rows = [
            row for row, chunk in enumerate(self.chunks)
            if row not in primary_row_set and chunk.category in fallback_categories
        ]

        results: list[RetrievedChunk] = []
        per_document: dict[str, int] = {}
        seen_chunk_ids: set[str] = set()
        for rows in (course_chunk_rows, extra_rows, fallback_rows):
            self._append_lane(
                rows,
                query_vector,
                results,
                per_document,
                seen_chunk_ids,
            )
            if len(results) >= self.settings.rag_top_k:
                break
        return results

    def _retrieve_unfiltered(
        self,
        query_vector: np.ndarray,
    ) -> list[RetrievedChunk]:
        scores, rows = self.index.search(
            query_vector.reshape(1, -1),
            self.settings.rag_top_k * 6,
        )
        pairs = [(int(row), float(score))
                 for row, score in zip(rows[0], scores[0]) if row >= 0]
        results: list[RetrievedChunk] = []
        self._append_pairs(pairs, results, {}, set())
        return results

    def _append_lane(
        self,
        rows: list[int],
        query_vector: np.ndarray,
        results: list[RetrievedChunk],
        per_document: dict[str, int],
        seen_chunk_ids: set[str],
    ) -> None:
        pairs = [(row, float(self.vectors[row] @ query_vector))
                 for row in rows]
        self._append_pairs(
            pairs,
            results,
            per_document,
            seen_chunk_ids,
        )

    def _append_pairs(
        self,
        pairs: list[tuple[int, float]],
        results: list[RetrievedChunk],
        per_document: dict[str, int],
        seen_chunk_ids: set[str],
    ) -> None:
        pairs.sort(key=lambda pair: pair[1], reverse=True)
        for row, score in pairs:
            if len(results) >= self.settings.rag_top_k:
                return
            if score < self.settings.rag_score_threshold:
                break
            chunk = self.chunks[row]
            if chunk.chunk_id in seen_chunk_ids:
                continue
            used = per_document.get(chunk.document_id, 0)
            if used >= self.settings.rag_max_chunks_per_document:
                continue
            per_document[chunk.document_id] = used + 1
            seen_chunk_ids.add(chunk.chunk_id)
            results.append(
                RetrievedChunk(
                    chunk_id=chunk.chunk_id,
                    document_id=chunk.document_id,
                    source_type=chunk.source_type,
                    source_id=chunk.source_id,
                    category=chunk.category,
                    title=chunk.title,
                    section=chunk.section,
                    content=chunk.content,
                    score=score,
                    metadata=chunk.metadata,
                ))
