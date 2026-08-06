import asyncio

import faiss
import numpy as np

from app.core.config import Settings
from app.rag.index_store import IndexStore
from app.rag.models import KnowledgeChunk, RagQueryPlan
from app.rag.retriever import Retriever


class _QueryEmbedder:
    async def embed_one(self, query: str) -> list[float]:
        assert query == "target"
        return [1.0, 0.0]


def _chunk(chunk_id: str, document_id: str, category: str) -> KnowledgeChunk:
    return KnowledgeChunk(
        chunk_id=chunk_id,
        document_id=document_id,
        source_id=document_id.removeprefix("GUIDE:"),
        category=category,
        title=document_id,
        section="测试小节",
        chunk_index=0,
        content=chunk_id,
        embedding_text=chunk_id,
    )


def _retriever() -> Retriever:
    chunks = [
        _chunk("d1-1", "GUIDE:d1", "course_materials"),
        _chunk("d1-2", "GUIDE:d1", "course_materials"),
        _chunk("d2-1", "GUIDE:d2", "course_materials"),
        _chunk("dorm-1", "GUIDE:dorm", "campus_dorm"),
        _chunk("platform-1", "GUIDE:platform", "platform_policy"),
        _chunk("lifecycle-1", "GUIDE:lifecycle", "campus_lifecycle"),
    ]
    vectors = np.array(
        [
            [0.70, 0.0],
            [0.69, 0.0],
            [0.99, 0.0],
            [0.60, 0.0],
            [0.95, 0.0],
            [0.55, 0.0],
        ],
        dtype=np.float32,
    )
    index = faiss.IndexFlatIP(2)
    index.add(vectors)
    store = IndexStore(index, vectors, chunks, {})
    settings = Settings(
        embedding_dimensions=2,
        rag_top_k=4,
        rag_score_threshold=0.5,
        rag_max_chunks_per_document=1,
    )
    return Retriever(settings, _QueryEmbedder(), store)


def test_exact_preferred_and_fallback_are_prioritized_as_separate_lanes():
    plan = RagQueryPlan(
        should_retrieve=True,
        exact_document_ids=["GUIDE:d1"],
        preferred_categories=["campus_dorm"],
        fallback_categories=["platform_policy", "campus_lifecycle"],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "d1-1",
        "dorm-1",
        "platform-1",
        "lifecycle-1",
    ]


def test_fallback_never_adds_unselected_course_documents():
    plan = RagQueryPlan(
        should_retrieve=True,
        exact_document_ids=["GUIDE:d1"],
        fallback_categories=[
            "platform_policy",
            "campus_dorm",
            "campus_lifecycle",
        ],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    chunk_ids = [item.chunk_id for item in result]
    assert chunk_ids[0] == "d1-1"
    assert "d2-1" not in chunk_ids
    assert chunk_ids[1:] == ["platform-1", "dorm-1", "lifecycle-1"]


def test_missing_exact_document_can_still_use_non_course_fallback():
    plan = RagQueryPlan(
        should_retrieve=True,
        exact_document_ids=["GUIDE:missing"],
        fallback_categories=["campus_dorm"],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == ["dorm-1"]


def test_unscoped_plan_uses_faiss_and_keeps_document_cap():
    plan = RagQueryPlan(should_retrieve=True)

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "d2-1",
        "platform-1",
        "d1-1",
        "dorm-1",
    ]


def test_unready_or_disabled_plan_returns_no_context():
    settings = Settings(embedding_dimensions=2)
    unready = Retriever(settings, _QueryEmbedder(), None)

    assert asyncio.run(
        unready.retrieve("target", RagQueryPlan(should_retrieve=True))
    ) == []
    assert asyncio.run(
        _retriever().retrieve("target", RagQueryPlan(should_retrieve=False))
    ) == []
