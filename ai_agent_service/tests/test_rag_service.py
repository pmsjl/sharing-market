import asyncio

from app.core.config import Settings
from app.clients.java_backend import JavaBackendClientError
from app.rag.course_relations import CourseRelationIndex
from app.rag.models import RetrievedChunk
from app.rag.service import RagService
from app.rag import service as rag_service_module


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


def _chunk(source_type, source_id, *, source_version=None):
    metadata = {}
    if source_version is not None:
        metadata["sourceVersion"] = source_version
    return RetrievedChunk(
        chunk_id=f"{source_type}:{source_id}:chunk",
        document_id=f"{source_type}:{source_id}",
        source_type=source_type,
        source_id=source_id,
        category="community_post" if source_type == "POST" else "platform_policy",
        title=f"title-{source_id}",
        section=None,
        content="content",
        score=0.9,
        metadata=metadata,
    )


def test_blank_query_is_normal_empty_result():
    retriever = _Retriever()
    context = asyncio.run(_service(retriever).get_context("   ", "request-1"))

    assert context.retrieved == []
    assert context.degraded is False
    assert retriever.calls == []


def test_empty_retrieval_is_not_degraded():
    context = asyncio.run(_service(_Retriever(result=[])).get_context(
        "这个东西在学校使用有什么限制？",
        "request-1",
    ))

    assert context.retrieved == []
    assert context.degraded is False


def test_unready_or_failed_retriever_degrades_without_raising():
    unready = asyncio.run(_service(_Retriever(ready=False)).get_context(
        "离校前大件物品怎么处理？",
        "request-1",
    ))
    failed = asyncio.run(_service(_Retriever(error=RuntimeError("boom"))).get_context(
        "宿舍使用小家电有什么限制？",
        "request-1",
    ))

    assert unready.degraded is True
    assert failed.degraded is True
    assert unready.retrieved == []
    assert failed.retrieved == []


def test_post_chunks_are_pruned_by_java_version_validation():
    guide = _chunk("GUIDE", "guide-1")
    current = _chunk("POST", "101", source_version="1700000000000")
    stale = _chunk("POST", "102", source_version="1700000000001")
    java_client = _JavaClient(valid={("101", "1700000000000")})

    context = asyncio.run(
        _service(
            _Retriever(result=[guide, current, stale]),
            java_client,
        ).get_context("宿舍交易经验", "request-2")
    )

    assert context.retrieved == [guide, current]
    assert context.post_degraded is False
    assert java_client.calls[0][0] == "request-2"
    assert [item.post_id for item in java_client.calls[0][1]] == [101, 102]


def test_post_validation_failure_only_drops_posts():
    guide = _chunk("GUIDE", "guide-1")
    post = _chunk("POST", "101", source_version="1700000000000")
    java_client = _JavaClient(
        error=JavaBackendClientError("AI_JAVA_TOOL_TIMEOUT", "timeout", True)
    )

    context = asyncio.run(
        _service(_Retriever(result=[post, guide]), java_client).get_context(
            "宿舍交易经验",
            "request-3",
        )
    )

    assert context.retrieved == [guide]
    assert context.degraded is False
    assert context.post_degraded is True


def test_malformed_post_identity_is_not_sent_to_java():
    guide = _chunk("GUIDE", "guide-1")
    post = _chunk("POST", "101", source_version="01700000000000")
    java_client = _JavaClient()

    context = asyncio.run(
        _service(_Retriever(result=[guide, post]), java_client).get_context(
            "宿舍交易经验",
            "request-4",
        )
    )

    assert context.retrieved == [guide]
    assert context.post_degraded is True
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
    broken = _Retriever(ready=False, build_name="build-2")
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
        lambda settings, build_name=None: broken,
    )

    asyncio.run(service.reload_if_changed())

    assert service.retriever is original
    assert service.loaded_build_name == "build-1"
    assert service.reload_error == "新索引没有通过完整性校验"
