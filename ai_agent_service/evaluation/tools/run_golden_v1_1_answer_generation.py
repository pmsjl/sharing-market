"""Generate fresh round-two answers for Golden Dataset v1.1."""
from __future__ import annotations

import asyncio
import argparse
import json
import os
import sys
import time
from contextvars import ContextVar
from dataclasses import replace
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[3]
AGENT_ROOT = ROOT / "ai_agent_service"
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(AGENT_ROOT))

from app.core.config import Settings
from app.prompts.shopping_guide import SYSTEM_PROMPT as AGENT_SYSTEM_PROMPT
from app.models.agent import (
    AgentModelOutput,
    AgentRunRequest,
    AgentRunResponse,
    AgentSource,
)
from app.models.tools import CommoditySearchToolResponse, PreferenceBehaviorStats, PreferenceConfidence, UserPreferenceToolResponse
from app.rag.course_relations import CourseRelationIndex
from app.rag.embedding_client import EmbeddingClient, l2_normalize
from app.rag.index_store import KNOWLEDGE_ROOT as DEFAULT_KNOWLEDGE_ROOT, IndexStore
from app.rag.models import RagContext, RagDiagnostics, RagQueryPlan, RagResolution
from app.rag.retriever import Retriever
from app.rag.service import resolve_course_evidence_state
from app.rag.query_planner import plan_query, resolve_course_match
from app.routing.query_router import RetrieveRouteDecision, RouteResolution
from app.services.agent_service import (
    AgentService,
    AgentServiceError,
    build_reference_maps,
)
from golden_v1_1_round2_paths import REPORTS_DIR, RESULTS_DIR

EVAL = AGENT_ROOT / "evaluation"
DATASET = EVAL / "dataset/golden_v1_2_1_reviewed_200.jsonl"
MANIFEST = EVAL / "dataset/golden_v1_2_1_reviewed_200_manifest.json"
OUTPUT = RESULTS_DIR / "golden_v1_1_round2_answer_generation.jsonl"
SUMMARY = REPORTS_DIR / "golden_v1_1_round2_answer_generation_summary.json"
CONCURRENCY = 6
EXPECTED_MODEL = "gpt-5.6-terra"

_REFERENCE_ATTEMPTS: ContextVar[list[dict[str, Any]] | None] = ContextVar(
    "golden_reference_attempts",
    default=None,
)


def reference_attempt(
    attempt: int,
    output: AgentModelOutput,
    knowledge_map: dict[str, str],
    course_map: dict[str, str],
    action: str,
) -> dict[str, Any]:
    return {
        "attempt": attempt,
        "allowedKnowledgeReferences": list(knowledge_map),
        "modelKnowledgeReferences": list(output.knowledgeReferences),
        "invalidKnowledgeReferences": sorted(
            set(output.knowledgeReferences) - set(knowledge_map)
        ),
        "allowedCourseReferences": list(course_map),
        "modelCourseReferences": list(output.courseReferences),
        "invalidCourseReferences": sorted(
            set(output.courseReferences) - set(course_map)
        ),
        "action": action,
    }


class GoldenAgentService(AgentService):
    """在 Golden 工具侧观察引用校验，不向生产 AgentService 注入评测状态。"""

    def _validate_model_references(
        self,
        output: AgentModelOutput,
        allowed_commodity_ids: set[str],
        rag_context: RagContext | None,
        knowledge_map: dict[str, str],
        course_map: dict[str, str],
    ) -> list[AgentSource]:
        attempts = _REFERENCE_ATTEMPTS.get()
        try:
            sources = super()._validate_model_references(
                output,
                allowed_commodity_ids,
                rag_context,
                knowledge_map,
                course_map,
            )
        except AgentServiceError as error:
            if attempts is not None:
                repairable = (
                    not attempts
                    and error.agent_error_key == "AI_MODEL_RESPONSE_INVALID"
                    and "RAG ID" in error.message
                    and self._uses_alias_references(output)
                )
                attempts.append(reference_attempt(
                    len(attempts) + 1,
                    output,
                    knowledge_map,
                    course_map,
                    "targeted_reference_repair" if repairable else "rejected",
                ))
            raise

        if attempts is not None:
            attempts.append(reference_attempt(
                len(attempts) + 1,
                output,
                knowledge_map,
                course_map,
                "accepted",
            ))
        return sources


def build_reference_audit(
    context: RagContext,
    response: AgentRunResponse,
    attempts: list[dict[str, Any]],
) -> dict[str, Any]:
    if not attempts:
        return {}
    knowledge_map, course_map = build_reference_maps(context)
    knowledge_chunk_ids = list(response.output.knowledgeChunkIds)
    course_relation_ids = list(response.output.courseRelationIds)
    final_attempt = attempts[-1]
    return {
        "referenceMap": {**knowledge_map, **course_map},
        "referenceAttempts": attempts,
        "targetedReferenceRepairCount": sum(
            item["action"] == "targeted_reference_repair"
            for item in attempts
        ),
        "finalKnowledgeReferences": list(
            final_attempt["modelKnowledgeReferences"]
        ),
        "finalCourseReferences": list(
            final_attempt["modelCourseReferences"]
        ),
        "finalKnowledgeChunkIds": knowledge_chunk_ids,
        "finalCourseRelationIds": course_relation_ids,
        "mappingSucceeded": True,
    }


def build_reference_error_diagnostics(
    context: RagContext,
    attempts: list[dict[str, Any]],
) -> dict[str, Any]:
    if not attempts:
        return {}
    knowledge_map, course_map = build_reference_maps(context)
    return {
        "referenceMap": {**knowledge_map, **course_map},
        "referenceAttempts": attempts,
    }


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


class FixedJavaClient:
    async def search_commodities(self, request_id: str, arguments: Any) -> CommoditySearchToolResponse:
        return CommoditySearchToolResponse(requestId=request_id, matchedCount=0, items=[])

    async def get_my_preference_signals(self, request_id: str, user_id: int) -> UserPreferenceToolResponse:
        return UserPreferenceToolResponse(requestId=request_id, behaviorStats=PreferenceBehaviorStats(distinctPurchaseCount=0, distinctFavoriteCount=0, distinctCategoryCount=0), preferredCategories=[], representativeInteractions=[], purchasePriceProfile=None, favoriteCurrentPriceProfile=None, preferredDegrees=[], recentCommodityIds=[], confidence=PreferenceConfidence.NONE, coldStart=True)


class FrozenRagService:
    def __init__(self, resolutions: dict[str, RagResolution], matches) -> None:
        self.resolutions = resolutions
        self.matches = matches
    def match_course_query(self, query: str): return self.matches[query]
    async def get_context(self, query: str, request_id: str, route_decision, course_match) -> RagResolution: return self.resolutions[query]


class FrozenQueryRouter:
    def __init__(self, resolutions: dict[str, RouteResolution]) -> None:
        self.resolutions = resolutions

    async def resolve(self, request: AgentRunRequest, course_match=None) -> RouteResolution:
        return self.resolutions[request.message]


def plan_payload(context: RagContext) -> dict[str, Any]:
    plan = context.plan
    relations = [item.model_dump(mode="json") if hasattr(item, "model_dump") else item for item in plan.course_relation_summaries]
    return {"postRetrievalMode": plan.post_retrieval_mode, "courseDocumentIds": plan.course_document_ids, "courseAQuota": plan.course_a_quota, "includeCoursePurchasePolicy": plan.include_course_purchase_policy, "primaryGuideCategories": plan.primary_guide_categories, "fallbackGuideCategories": plan.fallback_guide_categories, "courseAuxiliaryCategories": plan.course_auxiliary_categories, "courseEvidenceState": context.course_evidence_state, "courseRelationSummaries": relations}


def metrics(case: dict[str, Any], response: dict[str, Any], context: RagContext) -> dict[str, Any]:
    source_docs = [source["documentId"] for source in response["output"]["sources"]]
    cited_chunks = {citation["chunkId"] for source in response["output"]["sources"] for citation in source["citations"]}
    relevance = {row["documentId"]: row["relevance"] for row in case["qrels"]}
    relevant_qrels = {doc for doc, value in relevance.items() if value >= 1}
    required = {row["documentId"] for row in case["qrels"] if row["required"]}
    supporting = {chunk_id for row in case["qrels"] for chunk_id in row.get("supportingChunkIds", [])}
    retrieved_docs = {item.document_id for item in context.retrieved}
    cited_docs = set(source_docs)
    retrieved_required = required & retrieved_docs
    return {
        "sourceDocumentIds": source_docs,
        "citationPrecision": (len(relevant_qrels & cited_docs) / len(cited_docs)) if cited_docs and relevant_qrels else None,
        "requiredCitationRecall": (len(required & cited_docs) / len(required)) if required else None,
        "conditionalRequiredCitationRecall": (len(retrieved_required & cited_docs) / len(retrieved_required)) if retrieved_required else None,
        "supportingChunkCitationRecall": (len(supporting & cited_chunks) / len(supporting)) if supporting else None,
        "requiredDocuments": sorted(required), "retrievedRequiredDocuments": sorted(retrieved_required),
    }


def retrieve(retriever: Retriever, vector: Any, plan: Any) -> list[Any]:
    if not retriever.ready: return []
    normalized = l2_normalize(vector)
    return retriever.retrieve_with_vector(normalized, plan)


def select_embedding_cases(cases: list[dict[str, Any]], plans: dict[str, Any]) -> list[dict[str, Any]]:
    return [case for case in cases if plans.get(case["caseId"]) is not None]


async def build_contexts(
    cases: list[dict[str, Any]],
    settings: Settings,
    build_id: str,
    router_results_path: Path,
):
    relations = CourseRelationIndex.load(Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT))))
    frozen_root = Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT)))
    retriever = Retriever(settings, EmbeddingClient(settings), IndexStore.load(
        settings, knowledge_root=frozen_root, build_name=build_id,
    ))
    if not retriever.ready: raise RuntimeError("frozen index unavailable")
    router_rows = {row["caseId"]: row for row in read_jsonl(router_results_path)}
    missing = [case["caseId"] for case in cases if case["caseId"] not in router_rows]
    if missing:
        raise RuntimeError("router results missing cases: " + ", ".join(missing))
    route_resolutions = {
        case["caseId"]: RouteResolution.model_validate({
            "decision": router_rows[case["caseId"]]["decision"],
            "diagnostics": router_rows[case["caseId"]]["diagnostics"],
        })
        for case in cases
    }
    invalid = [
        case_id for case_id, value in route_resolutions.items()
        if value.diagnostics.decision_source not in {"llm", "deterministic_fallback"}
        or (
            value.diagnostics.decision_source == "llm"
            and (
                value.diagnostics.input_tokens <= 0
                or value.diagnostics.output_tokens <= 0
            )
        )
    ]
    if invalid:
        raise RuntimeError("strict Router handoff gate failed for: " + ", ".join(invalid))
    artifacts = {}
    for case in cases:
        raw_match = relations.match(case["query"], allow_dimension_only=True)
        decision = route_resolutions[case["caseId"]].decision
        effective_match = (
            resolve_course_match(raw_match, decision)
            if isinstance(decision, RetrieveRouteDecision)
            else None
        )
        artifacts[case["caseId"]] = (
            decision,
            raw_match,
            effective_match,
            (
                plan_query(effective_match, decision)
                if effective_match is not None
                else None
            ),
        )
    plans = {case_id: value[3] for case_id, value in artifacts.items()}
    embedded_cases = select_embedding_cases(cases, plans)
    started = time.perf_counter()
    vectors = await EmbeddingClient(settings).embed_batch([case["query"] for case in embedded_cases])
    elapsed = time.perf_counter() - started
    if len(vectors) != len(embedded_cases):
        raise RuntimeError(f"embedding result count mismatch: expected {len(embedded_cases)}, got {len(vectors)}")
    vector_by_id = {case["caseId"]: vector for case, vector in zip(embedded_cases, vectors)}
    contexts = {}
    resolutions = {}
    matches = {}
    for case in cases:
        plan = plans[case["caseId"]]
        raw_match = artifacts[case["caseId"]][1]
        matches[case["query"]] = raw_match
        if plan is None:
            contexts[case["query"]] = RagContext(
                plan=RagQueryPlan(),
                retrieved=[],
                course_evidence_state=None,
            )
            continue
        vector = vector_by_id.get(case["caseId"])
        retrieved = retrieve(retriever, vector, plan) if vector is not None else []
        effective_match = artifacts[case["caseId"]][2]
        if effective_match is None:
            raise RuntimeError("retrieve plan is missing its course match")
        decision = artifacts[case["caseId"]][0]
        context = RagContext(
            plan=plan,
            retrieved=retrieved,
            course_evidence_state=resolve_course_evidence_state(
                "course" in decision.knowledge_domains,
                plan,
                retrieved,
            ),
        )
        contexts[case["query"]] = context
        resolutions[case["query"]] = RagResolution(context=context, diagnostics=RagDiagnostics(retrieval_status="success", course_match_mode=effective_match.mode, constraints_fallback=effective_match.constraints_fallback))
    frozen_routes = {
        case["query"]: route_resolutions[case["caseId"]]
        for case in cases
    }
    return contexts, resolutions, matches, frozen_routes, elapsed, {
        case["caseId"] for case in embedded_cases
    }


async def generate_one(case: dict[str, Any], service: GoldenAgentService, context: RagContext, embedding_called: bool, semaphore: asyncio.Semaphore, system_date: str, dataset_version: str) -> dict[str, Any]:
    request_id = f"eval-v1-1-round2-{case['caseId']}"
    request = AgentRunRequest(userId=1, conversationId=1, message=case["query"], history=[])
    async with semaphore:
        last_error: Exception | None = None
        last_reference_attempts: list[dict[str, Any]] = []
        for attempt in range(5):
            started = time.perf_counter()
            reference_attempts: list[dict[str, Any]] = []
            token = _REFERENCE_ATTEMPTS.set(reference_attempts)
            try:
                try:
                    response = await service.run(request_id, request)
                finally:
                    _REFERENCE_ATTEMPTS.reset(token)
                reference_audit = build_reference_audit(
                    context,
                    response,
                    reference_attempts,
                )
                data = response.model_dump(mode="json")
                model_name = data.get("model", {}).get("name", "")
                effective_expected_route = case["expectedRoute"]
                if model_name not in {EXPECTED_MODEL, "deterministic-router-v1"}:
                    raise ValueError(f"unexpected generation model: {model_name}")
                tool_names = [trace["toolName"] for trace in data["traces"]]
                expects_search = effective_expected_route == "skip_rag"
                forbids_search = effective_expected_route in {"clarify", "out_of_scope"}
                return {
                    "caseId": case["caseId"], "split": case["split"], "domain": case["domain"], "queryType": case["queryType"], "query": case["query"],
                    "datasetVersion": dataset_version, "datasetExpectedRoute": case["expectedRoute"], "expectedRoute": effective_expected_route, "expectationOverride": case.get("currentRuntimeReason"), "expectedKnowledgeState": case["expectedKnowledgeState"], "expectedFacts": case["expectedFacts"], "qrels": case["qrels"],
                    "status": "SUCCESS", "attempt": attempt + 1, "elapsedSeconds": round(time.perf_counter() - started, 4), "systemCurrentDate": system_date,
                    "runtimeSystemPrompt": AGENT_SYSTEM_PROMPT,
                    "rag": {**plan_payload(context), "embeddingCalled": embedding_called, "retrievedChunkIds": [item.chunk_id for item in context.retrieved], "retrievedDocumentIds": [item.document_id for item in context.retrieved], "retrieved": [{"chunkId": item.chunk_id, "documentId": item.document_id, "sourceType": item.source_type, "title": item.title, "section": item.section, "score": round(float(item.score), 8), "content": item.content} for item in context.retrieved]},
                    "response": data, "answer": data["answer"], "toolNames": tool_names,
                    "referenceAudit": reference_audit,
                    "searchToolExpected": expects_search, "searchToolForbidden": forbids_search, "searchToolCalled": "search_commodities" in tool_names,
                    "toolSelectionCorrect": (("search_commodities" in tool_names) if expects_search else (("search_commodities" not in tool_names) if forbids_search else True)),
                    **metrics(case, data, context),
                }
            except Exception as exc:
                last_error = exc
                last_reference_attempts = reference_attempts
                if (
                    isinstance(exc, AgentServiceError)
                    and exc.agent_error_key in {
                        "AI_MODEL_UNAVAILABLE",
                        "AI_MODEL_TIMEOUT",
                    }
                ):
                    # Infrastructure outages are not answer-quality failures.
                    # The user explicitly allows at most three retries beyond
                    # the initial request; exhaustion aborts without writing a
                    # Golden Generation ERROR row.
                    if attempt >= 3:
                        raise RuntimeError(
                            f"model infrastructure unavailable after 3 retries "
                            f"at {case['caseId']}: {exc.agent_error_key}"
                        ) from exc
                    await asyncio.sleep(min(30, 2 ** (attempt + 1)))
                    continue
                invalid_rag_reference = (
                    isinstance(exc, AgentServiceError)
                    and exc.agent_error_key == "AI_MODEL_RESPONSE_INVALID"
                    and exc.message == "模型引用了本轮不可用的 RAG ID"
                )
                # AgentService already performs the sole targeted reference
                # repair. Never regenerate the whole answer for this failure.
                max_attempt_index = 0 if invalid_rag_reference else 4
                if attempt >= max_attempt_index:
                    break
                await asyncio.sleep(min(30, 2 ** (attempt + 1)))
        error_row = {
            "errorDiagnosticsVersion": 1,
            "attemptCount": attempt + 1,
            "caseId": case["caseId"],
            "split": case["split"],
            "domain": case["domain"],
            "queryType": case["queryType"],
            "query": case["query"],
            "datasetVersion": dataset_version,
            "expectedRoute": case["expectedRoute"],
            "expectedKnowledgeState": case["expectedKnowledgeState"],
            "expectedFacts": case["expectedFacts"],
            "qrels": case["qrels"],
            "status": "ERROR",
            "runtimeSystemPrompt": AGENT_SYSTEM_PROMPT,
            "errorType": type(last_error).__name__ if last_error else "Unknown",
            "error": str(last_error) if last_error else "Unknown",
            "agentErrorKey": (last_error.agent_error_key
                              if isinstance(last_error, AgentServiceError)
                              else None),
            "retryable": (last_error.retryable
                          if isinstance(last_error, AgentServiceError)
                          else None),
            # ERROR 也保存请求级候选，才能区分“索引存在”与“本轮允许引用”。
            "rag": {
                **plan_payload(context),
                "embeddingCalled": embedding_called,
                "retrievedChunkIds": [item.chunk_id for item in context.retrieved],
                "retrievedDocumentIds": [item.document_id for item in context.retrieved],
                "retrieved": [{
                    "chunkId": item.chunk_id,
                    "documentId": item.document_id,
                    "sourceType": item.source_type,
                    "title": item.title,
                    "section": item.section,
                    "score": round(float(item.score), 8),
                    "content": item.content,
                } for item in context.retrieved],
            },
        }
        error_row["diagnostics"] = build_reference_error_diagnostics(
            context,
            last_reference_attempts,
        )
        return error_row


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=DATASET)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--summary", type=Path, default=SUMMARY)
    parser.add_argument("--router-results", type=Path, required=True)
    args = parser.parse_args()
    dataset_path = args.dataset.resolve()
    manifest_path = args.manifest.resolve()
    output_path = args.output.resolve()
    summary_path = args.summary.resolve()
    cases = read_jsonl(dataset_path)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    settings = replace(Settings(), openai_model=EXPECTED_MODEL, openai_timeout_seconds=240, openai_reasoning_effort="medium", openai_text_verbosity="high")
    contexts, resolutions, matches, frozen_routes, embedding_seconds, embedded_case_ids = await build_contexts(
        cases,
        settings,
        manifest["indexBuildIdAtFreeze"],
        args.router_results.resolve(),
    )
    system_date = datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()
    service = GoldenAgentService(
        settings,
        java_backend_client=FixedJavaClient(),
        rag_service=FrozenRagService(resolutions, matches),
        query_router=FrozenQueryRouter(frozen_routes),
    )
    completed = {row["caseId"]: row for row in read_jsonl(output_path)} if output_path.exists() else {}
    pending = [case for case in cases if case["caseId"] not in completed or completed[case["caseId"]].get("status") != "SUCCESS"]
    semaphore = asyncio.Semaphore(CONCURRENCY)
    for start in range(0, len(pending), CONCURRENCY):
        batch = pending[start:start + CONCURRENCY]
        rows = await asyncio.gather(*(generate_one(case, service, contexts[case["query"]], case["caseId"] in embedded_case_ids, semaphore, system_date, manifest["datasetVersion"]) for case in batch))
        for row in rows: completed[row["caseId"]] = row
        ordered = [completed[case["caseId"]] for case in cases if case["caseId"] in completed]
        write_jsonl(output_path, ordered)
        print(f"generated {len(ordered)}/{len(cases)}", flush=True)
    rows = [completed[case["caseId"]] for case in cases if case["caseId"] in completed]
    success = [row for row in rows if row["status"] == "SUCCESS"]
    summary = {
        "status": "PASS" if len(success) == len(cases) else "PARTIAL", "generatedAt": system_date, "datasetVersion": manifest["datasetVersion"], "datasetCaseSha256": manifest["caseSha256"], "indexBuildId": manifest["indexBuildIdAtFreeze"],
        "model": EXPECTED_MODEL, "reasoningEffort": "medium", "textVerbosity": "high", "concurrency": CONCURRENCY, "timeoutSeconds": 240, "maxAttempts": 5, "invalidRagReferenceFullRegenerationAttempts": 0, "targetedReferenceRepairMaxAttempts": 1,
        "toolFixture": "empty commodity search and cold-start preferences", "caseCount": len(cases), "completedCount": len(rows), "successCount": len(success), "errorCount": len(cases) - len(success), "embeddingSeconds": round(embedding_seconds, 4),
        "embeddingQueryCount": len(embedded_case_ids),
        "inputTokens": sum((row["response"]["usage"].get("inputTokens") or 0) for row in success), "outputTokens": sum((row["response"]["usage"].get("outputTokens") or 0) for row in success),
        "meanLatencyMs": round(sum(row["response"]["latencyMs"] for row in success) / len(success), 2) if success else None,
        "errors": [{"caseId": row["caseId"], "errorType": row.get("errorType"), "error": row.get("error")} for row in rows if row["status"] == "ERROR"],
    }
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__": asyncio.run(main())
