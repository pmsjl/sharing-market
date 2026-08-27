"""组合课程关系规划与向量检索，并提供可降级的单次 RAG 上下文。"""

import asyncio
from pathlib import Path
from typing import Protocol

from app.clients.java_backend import JavaBackendClient
from app.core.config import Settings
from app.rag.course_relations import CourseMatch, CourseRelationIndex
from app.rag.index_store import KNOWLEDGE_ROOT, current_build_name
from app.rag.models import (
    CourseEvidenceState,
    PostVersionCandidate,
    RagContext,
    RagDiagnostics,
    RagResolution,
    RetrievedChunk,
)
from app.rag.query_planner import plan_query, resolve_course_match
from app.rag.retriever import Retriever
from app.routing.query_router import RetrieveRouteDecision


class PostVersionValidatorProtocol(Protocol):
    """RAG 服务用于校验 Post 版本的最小接口。"""

    async def validate_post_versions(
        self,
        request_id: str,
        candidates: list[PostVersionCandidate],
    ) -> set[tuple[str, str]]: ...


class RagService:
    """进程级 RAG 服务；索引与关系表只在构造时加载一次。"""

    def __init__(
        self,
        settings: Settings,
        relations: CourseRelationIndex | None = None,
        retriever: Retriever | None = None,
        java_backend_client: PostVersionValidatorProtocol | None = None,
        knowledge_root: Path = KNOWLEDGE_ROOT,
    ) -> None:
        self.settings = settings
        # 即使 RAG 关闭也保留空关系索引，让 Planner 可以安全地产生降级计划。
        self.relations = relations or CourseRelationIndex()
        self.relations_ready = relations is not None
        self.retriever = retriever
        self.java_backend_client = java_backend_client or JavaBackendClient(
            settings
        )
        self.reload_lock = asyncio.Lock()
        self.reload_error: str | None = None
        self.loaded_build_name = getattr(retriever, "build_name", None)
        self._reload_enabled = retriever is None

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
                self.loaded_build_name = self.retriever.build_name
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

    async def get_context(
        self,
        query: str,
        request_id: str,
        route_decision: RetrieveRouteDecision,
        course_match: CourseMatch,
    ) -> RagResolution:
        """规划并检索一次；可选增强失败时返回明确的降级状态。"""
        effective_match = resolve_course_match(course_match, route_decision)
        plan = plan_query(effective_match, route_decision)
        await self.reload_if_changed()
        if not self.ready:
            return RagResolution(
                context=RagContext(plan=plan, retrieved=[]),
                diagnostics=RagDiagnostics(
                    retrieval_status="unavailable",
                    failure_reason="RAG索引或课程关系尚未就绪",
                    course_match_mode=effective_match.mode,
                    constraints_fallback=effective_match.constraints_fallback,
                ),
            )
        try:
            assert self.retriever is not None
            retrieved = await self.retriever.retrieve(query, plan)
        except Exception as exception:
            # RAG 是可选增强；这里不能阻断既有商品 Tool 和普通回答。
            return RagResolution(
                context=RagContext(plan=plan, retrieved=[]),
                diagnostics=RagDiagnostics(
                    retrieval_status="failed",
                    failure_reason=str(exception)[:500],
                    course_match_mode=effective_match.mode,
                    constraints_fallback=effective_match.constraints_fallback,
                ),
            )

        course_evidence_state = resolve_course_evidence_state(
            "course" in route_decision.knowledge_domains,
            plan,
            retrieved,
        )

        guide_chunks = [
            item for item in retrieved if item.source_type == "GUIDE"
        ]
        post_chunks = [
            item for item in retrieved if item.source_type == "POST"
        ]
        if not post_chunks:
            return RagResolution(
                context=RagContext(
                    plan=plan,
                    retrieved=guide_chunks,
                    course_evidence_state=course_evidence_state,
                ),
                diagnostics=RagDiagnostics(
                    retrieval_status="success",
                    course_match_mode=effective_match.mode,
                    constraints_fallback=effective_match.constraints_fallback,
                ),
            )

        candidates = build_post_version_candidates(post_chunks)
        if not candidates:
            return RagResolution(
                context=RagContext(
                    plan=plan,
                    retrieved=guide_chunks,
                    course_evidence_state=course_evidence_state,
                ),
                diagnostics=RagDiagnostics(
                    retrieval_status="success",
                    post_validation_status="no_valid_candidates",
                    course_match_mode=effective_match.mode,
                    constraints_fallback=effective_match.constraints_fallback,
                ),
            )
        try:
            valid_versions = await self.java_backend_client.validate_post_versions(
                request_id,
                candidates,
            )
        except Exception as exception:
            return RagResolution(
                context=RagContext(
                    plan=plan,
                    retrieved=guide_chunks,
                    course_evidence_state=course_evidence_state,
                ),
                diagnostics=RagDiagnostics(
                    retrieval_status="success",
                    post_validation_status="failed",
                    failure_reason=str(exception)[:500],
                    course_match_mode=effective_match.mode,
                    constraints_fallback=effective_match.constraints_fallback,
                ),
            )

        valid_posts = [
            item
            for item in post_chunks
            if post_identity(item) in valid_versions
        ]
        return RagResolution(
            context=RagContext(
                plan=plan,
                retrieved=guide_chunks + valid_posts,
                course_evidence_state=course_evidence_state,
            ),
            diagnostics=RagDiagnostics(
                retrieval_status="success",
                post_validation_status="success",
                course_match_mode=effective_match.mode,
                constraints_fallback=effective_match.constraints_fallback,
            ),
        )

    def match_course_query(self, query: str):
        """解析一次课程别名与维度，供Router和Planner共同使用。"""
        return self.relations.match(query, allow_dimension_only=True)

    async def reload_if_changed(self) -> None:
        """CURRENT 切换后加载完整新版本；失败时继续保留旧 Retriever。"""
        if not self.settings.rag_enabled or not self._reload_enabled:
            return
        selected = current_build_name(self.settings)
        if not selected or selected == self.loaded_build_name:
            return

        async with self.reload_lock:
            selected = current_build_name(self.settings)
            if not selected or selected == self.loaded_build_name:
                return
            try:
                candidate = Retriever.load(self.settings, build_name=selected)
                self.retriever = candidate
                self.loaded_build_name = selected
                self.reload_error = None
            except Exception as exception:
                self.reload_error = str(exception)


def build_post_version_candidates(
    post_chunks: list[RetrievedChunk],
) -> list[PostVersionCandidate]:
    """按检索顺序去重并生成最多 10 个实时版本校验候选。"""
    results: list[PostVersionCandidate] = []
    seen: set[tuple[str, str]] = set()
    for item in post_chunks:
        identity = post_identity(item)
        if identity is None or identity in seen:
            continue
        seen.add(identity)
        results.append(
            PostVersionCandidate(
                postId=int(identity[0]),
                sourceVersion=identity[1],
            )
        )
        if len(results) >= 10:
            break
    return results


def post_identity(item: RetrievedChunk) -> tuple[str, str] | None:
    source_id = item.source_id.strip()
    source_version = item.metadata.get("sourceVersion")
    if (
        not source_id.isdigit()
        or int(source_id) <= 0
        or not isinstance(source_version, str)
        or not source_version.isdigit()
        or source_version.startswith("0")
    ):
        return None
    return source_id, source_version


def resolve_course_evidence_state(
    course_requested: bool,
    plan,
    retrieved: list[RetrievedChunk],
) -> CourseEvidenceState | None:
    """课程回答统一保守处理，不把关系或历史资料升级为当前要求。"""
    if not course_requested:
        return None
    a_documents = set(plan.course_document_ids)
    has_a_evidence = any(
        item.document_id in a_documents for item in retrieved
    )
    if has_a_evidence or plan.course_relation_summaries:
        return "clue_only"
    return "unknown_after_search"
