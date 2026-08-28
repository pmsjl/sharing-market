import asyncio
from typing import Literal

import faiss
import numpy as np
import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.rag.index_store import IndexStore
from app.rag.models import KnowledgeCategory, KnowledgeChunk, RagQueryPlan
from app.rag.retriever import Retriever


class _QueryEmbedder:

    async def embed_one(self, query: str) -> list[float]:
        assert query == "target"
        return [1.0, 0.0]


def _chunk(
    chunk_id: str,
    document_id: str,
    category: KnowledgeCategory,
    source_type: Literal["GUIDE", "POST"] = "GUIDE",
) -> KnowledgeChunk:
    return KnowledgeChunk(
        chunk_id=chunk_id,
        document_id=document_id,
        source_type=source_type,
        source_id=document_id.split(":", maxsplit=1)[-1],
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
        _chunk(
            "purchase-policy-1",
            "GUIDE:course-purchase-policy",
            "course_purchase_policy",
        ),
        _chunk("post-1a", "POST:1", "community_post", "POST"),
        _chunk("post-1b", "POST:1", "community_post", "POST"),
        _chunk("post-2", "POST:2", "community_post", "POST"),
        _chunk("post-3", "POST:3", "community_post", "POST"),
        _chunk("post-low", "POST:4", "community_post", "POST"),
    ]
    vectors = np.array(
        [
            [0.70, 0.0],
            [0.69, 0.0],
            [0.99, 0.0],
            [0.60, 0.0],
            [0.95, 0.0],
            [0.55, 0.0],
            [0.10, 0.0],
            [0.98, 0.0],
            [0.97, 0.0],
            [0.96, 0.0],
            [0.94, 0.0],
            [0.49, 0.0],
        ],
        dtype=np.float32,
    )
    index = faiss.IndexFlatIP(2)
    index.add(vectors)
    store = IndexStore(index, vectors, chunks, {})
    settings = Settings(
        embedding_dimensions=2,
        rag_guide_top_k=4,
        rag_score_threshold=0.5,
        rag_guide_max_chunks_per_document=1,
        rag_post_top_k=3,
        rag_course_auxiliary_post_top_k=3,
        rag_post_score_threshold=0.5,
        rag_post_max_chunks_per_document=1,
    )
    return Retriever(settings, _QueryEmbedder(), store)


def test_exact_preferred_and_fallback_are_prioritized_as_separate_lanes():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:d1"],
        include_course_purchase_policy=True,
        course_auxiliary_categories=[
            "campus_dorm", "platform_policy", "campus_lifecycle"
        ],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "d1-1",
        "purchase-policy-1",
        "platform-1",
        "dorm-1",
    ]


def test_fallback_never_adds_unselected_course_documents():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:d1"],
        include_course_purchase_policy=True,
        course_auxiliary_categories=[
            "platform_policy",
            "campus_dorm",
            "campus_lifecycle",
        ],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    chunk_ids = [item.chunk_id for item in result]
    assert chunk_ids[0] == "d1-1"
    assert "d2-1" not in chunk_ids
    assert chunk_ids[1:] == ["purchase-policy-1", "platform-1", "dorm-1"]


def test_missing_exact_document_can_still_use_non_course_fallback():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:missing"],
        include_course_purchase_policy=True,
        course_auxiliary_categories=["campus_dorm"],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "purchase-policy-1",
        "dorm-1",
    ]


def test_course_posts_have_three_slots_independent_of_c_guides():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:d1"],
        include_course_purchase_policy=True,
        course_auxiliary_categories=["platform_policy"],
        post_retrieval_mode="course_auxiliary",
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "d1-1",
        "purchase-policy-1",
        "platform-1",
        "post-1a",
        "post-2",
        "post-3",
    ]
    assert "d2-1" not in {item.chunk_id for item in result}


def test_course_fallback_keeps_a_b_c_guides_and_three_posts():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:d1"],
        include_course_purchase_policy=True,
        course_auxiliary_categories=[
            "platform_policy",
            "campus_dorm",
            "campus_lifecycle",
        ],
        post_retrieval_mode="course_auxiliary",
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "d1-1",
        "purchase-policy-1",
        "platform-1",
        "dorm-1",
        "post-1a",
        "post-2",
        "post-3",
    ]
    assert "d2-1" not in {item.chunk_id for item in result}


def test_missing_course_a_still_returns_reserved_purchase_policy_b():
    plan = RagQueryPlan(
        course_document_ids=["GUIDE:missing"],
        include_course_purchase_policy=True,
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == ["purchase-policy-1"]


def test_non_course_retrieval_does_not_load_course_a_or_b():
    plan = RagQueryPlan(
        primary_guide_categories=["platform_policy"],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == ["platform-1"]


def test_unscoped_plan_does_not_invent_a_guide_lane():
    plan = RagQueryPlan()

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert result == []


def test_posts_use_an_independent_quota_threshold_and_document_cap():
    plan = RagQueryPlan(
        post_retrieval_mode="primary",
        fallback_guide_categories=["platform_policy"],
    )

    result = asyncio.run(_retriever().retrieve("target", plan))

    assert [item.chunk_id for item in result] == [
        "platform-1",
        "post-1a",
        "post-2",
        "post-3",
    ]


def test_unready_retriever_returns_no_context():
    settings = Settings(embedding_dimensions=2)
    unready = Retriever(settings, _QueryEmbedder(), None)

    assert asyncio.run(
        unready.retrieve("target", RagQueryPlan())) == []


def test_rag_plan_rejects_removed_compatibility_fields():
    for field, value in (
        ("route", "retrieve"),
        ("should_retrieve", True),
        ("tool_policy", {}),
        ("include_posts", True),
        ("include_auxiliary_posts", True),
        ("extra_categories", []),
        ("fallback_categories", []),
        ("auxiliary_categories", []),
    ):
        with pytest.raises(ValidationError):
            RagQueryPlan.model_validate({field: value})
