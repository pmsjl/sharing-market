"""组合课程关系规划与向量检索，并提供可降级的单次 RAG 上下文。"""

from pathlib import Path

from app.core.config import Settings
from app.rag.course_relations import CourseRelationIndex
from app.rag.index_store import KNOWLEDGE_ROOT
from app.rag.models import RagContext
from app.rag.query_planner import plan_query
from app.rag.retriever import Retriever


class RagService:
    """进程级 RAG 服务；索引与关系表只在构造时加载一次。"""

    def __init__(
        self,
        settings: Settings,
        relations: CourseRelationIndex | None = None,
        retriever: Retriever | None = None,
        knowledge_root: Path = KNOWLEDGE_ROOT,
    ) -> None:
        self.settings = settings
        # 即使 RAG 关闭也保留空关系索引，让 Planner 可以安全地产生降级计划。
        self.relations = relations or CourseRelationIndex()
        self.relations_ready = relations is not None
        self.retriever = retriever

        if settings.rag_enabled and relations is None:
            try:
                self.relations = CourseRelationIndex.load(knowledge_root)
                self.relations_ready = True
            except (OSError, ValueError):
                self.relations = CourseRelationIndex()
                self.relations_ready = False
        if settings.rag_enabled and retriever is None:
            try:
                self.retriever = Retriever.load(settings)
            except Exception:
                # 缓存或本地 FAISS 环境异常也只能关闭可选 RAG 能力。
                self.retriever = None

    @property
    def ready(self) -> bool:
        return bool(
            self.settings.rag_enabled
            and self.relations_ready
            and self.retriever is not None
            and self.retriever.ready
        )

    async def get_context(self, query: str) -> RagContext:
        """规划并检索一次；可选增强失败时返回明确的降级状态。"""
        plan = plan_query(query, self.relations)
        if not plan.should_retrieve:
            return RagContext(
                query=query,
                plan=plan,
                retrieved=[],
                degraded=False,
            )
        if not self.ready:
            return RagContext(
                query=query,
                plan=plan,
                retrieved=[],
                degraded=True,
            )
        try:
            retrieved = await self.retriever.retrieve(query, plan)
            return RagContext(query=query, plan=plan, retrieved=retrieved)
        except Exception:
            # RAG 是可选增强；这里不能阻断既有商品 Tool 和普通回答。
            return RagContext(
                query=query,
                plan=plan,
                retrieved=[],
                degraded=True,
            )
