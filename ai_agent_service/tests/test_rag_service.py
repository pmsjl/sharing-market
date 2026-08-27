import asyncio
import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.clients.java_backend import JavaBackendClientError
from app.rag.course_relations import CourseRelationIndex
from app.rag.models import RagContext, RagQueryPlan, RetrievedChunk
from app.rag.service import RagService, resolve_course_evidence_state
from app.rag import service as rag_service_module
from app.routing.query_router import RetrieveRouteDecision


class _Retriever:

    def __init__(
        self,
        *,
        ready=True,
        result=None,
        error=None,
        build_name="build-1",
    ):
        self.ready = ready
        self.result = result or []
        self.error = error
        self.calls = []
        self.build_name = build_name

    async def retrieve(self, query, plan):
        self.calls.append((query, plan))
        if self.error:
            raise self.error
        return self.result


class _JavaClient:

    def __init__(self, *, valid=None, error=None):
        self.valid = valid or set()
        self.error = error
        self.calls = []

    async def validate_post_versions(self, request_id, candidates):
        self.calls.append((request_id, candidates))
        if self.error:
            raise self.error
        return self.valid


def _service(retriever, java_client=None):
    return RagService(
        Settings(rag_enabled=True),
        relations=CourseRelationIndex(),
        retriever=retriever,
        java_backend_client=java_client or _JavaClient(),
    )


def _get(service: RagService, query: str, request_id: str,
         decision: RetrieveRouteDecision | None = None):
    decision = decision or RetrieveRouteDecision(
        retrieval_strategy="broad_fallback")
    course_match = service.match_course_query(query)
    return asyncio.run(service.get_context(
        query, request_id, decision, course_match))


def _chunk(source_type, source_id, *, source_version=None):
    metadata = {}
    if source_version is not None:
        metadata["sourceVersion"] = source_version
    return RetrievedChunk(
        chunk_id=f"{source_type}:{source_id}:chunk",
        document_id=f"{source_type}:{source_id}",
        source_type=source_type,
        source_id=source_id,
        category="community_post"
        if source_type == "POST" else "platform_policy",
        title=f"title-{source_id}",
        section=None,
        content="content",
        score=0.9,
        metadata=metadata,
    )


def test_empty_retrieval_is_not_degraded():
    resolution = _get(
        _service(_Retriever(result=[])),
        "这个东西在学校使用有什么限制？",
        "request-1",
    )

    assert resolution.context.retrieved == []
    assert resolution.diagnostics.retrieval_status == "success"


def test_course_evidence_state_respects_fact_scope() -> None:
    course_chunk = RetrievedChunk(
        chunk_id="GUIDE:course-repo-COMP2052#material",
        document_id="GUIDE:course-repo-COMP2052",
        source_type="GUIDE",
        source_id="course-repo-COMP2052",
        category="course_materials",
        title="课程资料",
        section="教材",
        content="历史审核资料提到一本教材。",
        score=0.9,
        metadata={},
    )
    policy_chunk = RetrievedChunk(
        chunk_id="GUIDE:course-purchase-policy#policy",
        document_id="GUIDE:course-purchase-policy",
        source_type="GUIDE",
        source_id="course-purchase-policy",
        category="course_purchase_policy",
        title="购买政策",
        section=None,
        content="当前要求应向课程组确认。",
        score=0.8,
        metadata={},
    )
    course_plan = RagQueryPlan(
        course_document_ids=["GUIDE:course-repo-COMP2052"],
        include_course_purchase_policy=True,
    )
    missing = RagQueryPlan(include_course_purchase_policy=True)

    assert resolve_course_evidence_state(
        True, course_plan, [course_chunk]) == "clue_only"
    assert resolve_course_evidence_state(
        True, course_plan, [course_chunk, policy_chunk]) == "clue_only"
    assert resolve_course_evidence_state(
        True, missing, [policy_chunk]) == "unknown_after_search"
    assert resolve_course_evidence_state(
        False, course_plan, [course_chunk]) is None


def test_unready_or_failed_retriever_degrades_without_raising():
    unready = _get(_service(_Retriever(ready=False)),
                   "离校前大件物品怎么处理？", "request-1")
    failed = _get(_service(_Retriever(error=RuntimeError("boom"))),
                  "宿舍使用小家电有什么限制？", "request-1")

    assert unready.diagnostics.retrieval_status == "unavailable"
    assert failed.diagnostics.retrieval_status == "failed"
    assert unready.context.retrieved == []
    assert failed.context.retrieved == []


def test_post_chunks_are_pruned_by_java_version_validation():
    guide = _chunk("GUIDE", "guide-1")
    current = _chunk("POST", "101", source_version="1700000000000")
    stale = _chunk("POST", "102", source_version="1700000000001")
    java_client = _JavaClient(valid={("101", "1700000000000")})

    resolution = _get(
        _service(
            _Retriever(result=[guide, current, stale]),
            java_client,
        ), "宿舍交易经验", "request-2")

    assert resolution.context.retrieved == [guide, current]
    assert resolution.diagnostics.post_validation_status == "success"
    assert java_client.calls[0][0] == "request-2"
    assert [item.post_id for item in java_client.calls[0][1]] == [101, 102]


def test_post_validation_failure_only_drops_posts():
    guide = _chunk("GUIDE", "guide-1")
    post = _chunk("POST", "101", source_version="1700000000000")
    java_client = _JavaClient(
        error=JavaBackendClientError("AI_JAVA_TOOL_TIMEOUT", "timeout", True))

    resolution = _get(
        _service(_Retriever(result=[post, guide]), java_client),
        "宿舍交易经验", "request-3")

    assert resolution.context.retrieved == [guide]
    assert resolution.diagnostics.retrieval_status == "success"
    assert resolution.diagnostics.post_validation_status == "failed"


def test_malformed_post_identity_is_not_sent_to_java():
    guide = _chunk("GUIDE", "guide-1")
    post = _chunk("POST", "101", source_version="01700000000000")
    java_client = _JavaClient()

    resolution = _get(
        _service(_Retriever(result=[guide, post]), java_client),
        "宿舍交易经验", "request-4")

    assert resolution.context.retrieved == [guide]
    assert resolution.diagnostics.post_validation_status == "no_valid_candidates"
    assert java_client.calls == []


def test_current_change_hot_loads_complete_retriever(monkeypatch):
    original = _Retriever(build_name="build-1")
    replacement = _Retriever(build_name="build-2")
    service = _service(original)
    service._reload_enabled = True
    monkeypatch.setattr(
        rag_service_module,
        "current_build_name",
        lambda settings: "build-2",
    )
    monkeypatch.setattr(
        rag_service_module.Retriever,
        "load",
        lambda settings, build_name=None: replacement,
    )

    asyncio.run(service.reload_if_changed())

    assert service.retriever is replacement
    assert service.loaded_build_name == "build-2"
    assert service.reload_error is None


def test_broken_new_build_keeps_previous_retriever(monkeypatch):
    original = _Retriever(build_name="build-1")
    service = _service(original)
    service._reload_enabled = True

    def fail_to_load(settings, build_name=None):
        raise ValueError("索引文件损坏")

    monkeypatch.setattr(
        rag_service_module,
        "current_build_name",
        lambda settings: "build-2",
    )
    monkeypatch.setattr(
        rag_service_module.Retriever,
        "load",
        fail_to_load,
    )

    asyncio.run(service.reload_if_changed())

    assert service.retriever is original
    assert service.loaded_build_name == "build-1"
    assert service.reload_error == "索引文件损坏"


def test_rag_context_rejects_removed_query_and_degraded_fields():
    for field, value in (
        ("query", "测试"),
        ("degraded", True),
        ("post_degraded", True),
    ):
        with pytest.raises(ValidationError):
            RagContext.model_validate({
                "plan": RagQueryPlan().model_dump(),
                "retrieved": [],
                field: value,
            })
