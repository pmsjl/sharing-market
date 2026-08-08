import asyncio

from app.core.config import Settings
from app.rag.course_relations import CourseRelationIndex
from app.rag.service import RagService


class _Retriever:
    def __init__(self, *, ready=True, result=None, error=None):
        self.ready = ready
        self.result = result or []
        self.error = error
        self.calls = []

    async def retrieve(self, query, plan):
        self.calls.append((query, plan))
        if self.error:
            raise self.error
        return self.result


def _service(retriever):
    return RagService(
        Settings(rag_enabled=True),
        relations=CourseRelationIndex(),
        retriever=retriever,
    )


def test_blank_query_is_normal_empty_result():
    retriever = _Retriever()
    context = asyncio.run(_service(retriever).get_context("   "))

    assert context.retrieved == []
    assert context.degraded is False
    assert retriever.calls == []


def test_empty_retrieval_is_not_degraded():
    context = asyncio.run(_service(_Retriever(result=[])).get_context(
        "这个东西在学校使用有什么限制？"
    ))

    assert context.retrieved == []
    assert context.degraded is False


def test_unready_or_failed_retriever_degrades_without_raising():
    unready = asyncio.run(_service(_Retriever(ready=False)).get_context(
        "离校前大件物品怎么处理？"
    ))
    failed = asyncio.run(_service(_Retriever(error=RuntimeError("boom"))).get_context(
        "宿舍使用小家电有什么限制？"
    ))

    assert unready.degraded is True
    assert failed.degraded is True
    assert unready.retrieved == []
    assert failed.retrieved == []
