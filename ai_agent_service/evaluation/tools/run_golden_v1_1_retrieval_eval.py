"""Run the Golden v1.1 P0 routed retrieval evaluation on the frozen index."""
from __future__ import annotations

import asyncio
import argparse
import json
import os
import math
import statistics
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parent))
AGENT_ROOT = ROOT / "ai_agent_service"
sys.path.insert(0, str(AGENT_ROOT))

from app.core.config import Settings
from app.clients.openai_responses import OpenAIResponsesClient
from app.models.agent import AgentRunRequest
from app.rag.course_relations import CourseRelationIndex
from app.rag.embedding_client import EmbeddingClient, l2_normalize
from app.rag.index_store import KNOWLEDGE_ROOT as DEFAULT_KNOWLEDGE_ROOT, IndexStore
from app.rag.query_planner import plan_query, resolve_course_match
from app.rag.service import resolve_course_evidence_state
from golden_v1_1_round2_paths import REPORTS_DIR, RESULTS_DIR
from app.rag.retriever import Retriever
from app.routing.query_router import (
    HybridQueryRouter,
    RetrieveRouteDecision,
    RouteResolution,
    SkipRagRouteDecision,
)

EVAL = AGENT_ROOT / "evaluation"
DATASET = EVAL / "dataset/golden_v1_2_1_reviewed_200.jsonl"
MANIFEST = EVAL / "dataset/golden_v1_2_1_reviewed_200_manifest.json"
RESULTS = RESULTS_DIR
REPORTS = REPORTS_DIR
RUN_ID = "golden_v1_1_p0_routed"
def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def first_rank(document_ids: list[str], qrels: list[dict[str, Any]], minimum: int) -> int | None:
    relevant = {row["documentId"] for row in qrels if row["relevance"] >= minimum}
    return next((rank for rank, document_id in enumerate(document_ids, 1) if document_id in relevant), None)


def hit_at_k(rank: int | None, k: int) -> bool | None:
    return None if rank is None else rank <= k


def dcg(values: list[int]) -> float:
    return sum(value / math.log2(rank + 1) for rank, value in enumerate(values, 1))


def ndcg(document_ids: list[str], qrels: list[dict[str, Any]], k: int) -> float | None:
    positive = [row for row in qrels if row["relevance"] > 0]
    if not positive: return None
    by_document = {row["documentId"]: row["relevance"] for row in qrels}
    actual = [by_document.get(document_id, 0) for document_id in document_ids[:k]]
    ideal = sorted((row["relevance"] for row in positive), reverse=True)[:k]
    denominator = dcg(ideal)
    return 0.0 if denominator == 0 else dcg(actual) / denominator


def mean(values: list[float]) -> float | None:
    return round(statistics.fmean(values), 6) if values else None


def numeric(values: list[float]) -> dict[str, float | int | None]:
    if not values: return {"count": 0, "mean": None, "median": None, "min": None, "max": None}
    return {"count": len(values), "mean": round(statistics.fmean(values), 6), "median": round(statistics.median(values), 6), "min": round(min(values), 6), "max": round(max(values), 6)}


def retrieve_with_vector(retriever: Retriever, vector: np.ndarray, plan: Any) -> list[Any]:
    return retriever.retrieve_with_vector(vector, plan)


async def run(
    split: str | None = None,
    dataset_path: Path = DATASET,
    manifest_path: Path = MANIFEST,
    router_results_path: Path | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    cases = read_jsonl(dataset_path)
    if split is not None:
        cases = [case for case in cases if case["split"] == split]
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    settings = Settings()
    relations = CourseRelationIndex.load(Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT))))
    frozen_root = Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT)))
    retriever = Retriever(settings, EmbeddingClient(settings), IndexStore.load(
        settings, knowledge_root=frozen_root,
        build_name=manifest["indexBuildIdAtFreeze"],
    ))
    if not retriever.ready: raise RuntimeError("frozen index unavailable")
    raw_matches = {case["caseId"]: relations.match(case["query"], allow_dimension_only=True) for case in cases}
    if router_results_path is not None:
        router_rows = {row["caseId"]: row for row in read_jsonl(router_results_path)}
        missing = [case["caseId"] for case in cases if case["caseId"] not in router_rows]
        if missing:
            raise RuntimeError("router results missing cases: " + ", ".join(missing))
        resolutions = {
            case["caseId"]: RouteResolution.model_validate({
                "decision": router_rows[case["caseId"]]["decision"],
                "diagnostics": router_rows[case["caseId"]]["diagnostics"],
            })
            for case in cases
        }
        invalid = [
            case_id for case_id, value in resolutions.items()
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
    else:
        router = HybridQueryRouter(settings, OpenAIResponsesClient(settings))
        semaphore = asyncio.Semaphore(8)
        async def resolve(case):
            async with semaphore:
                return await router.resolve(
                    AgentRunRequest(userId=1, conversationId=1, message=case["query"]),
                    raw_matches[case["caseId"]],
                )
        resolved = await asyncio.gather(*(resolve(case) for case in cases))
        resolutions = {case["caseId"]: value for case, value in zip(cases, resolved)}
    effective_matches = {}
    plans = {}
    for case in cases:
        decision = resolutions[case["caseId"]].decision
        if not isinstance(decision, RetrieveRouteDecision):
            continue
        match = resolve_course_match(raw_matches[case["caseId"]], decision)
        effective_matches[case["caseId"]] = match
        plans[case["caseId"]] = plan_query(match, decision)
    embedded_cases = [case for case in cases if case["caseId"] in plans]
    started = time.perf_counter()
    vectors = await EmbeddingClient(settings).embed_batch([case["query"] for case in embedded_cases])
    embedding_seconds = time.perf_counter() - started
    vector_by_id = {case["caseId"]: l2_normalize(vector) for case, vector in zip(embedded_cases, vectors)}
    rows, latencies = [], []
    for case in cases:
        resolution = resolutions[case["caseId"]]
        decision = resolution.decision
        plan = plans.get(case["caseId"])
        tick = time.perf_counter()
        retrieved = retrieve_with_vector(retriever, vector_by_id[case["caseId"]], plan) if case["caseId"] in vector_by_id and plan is not None else []
        elapsed = (time.perf_counter() - tick) * 1000
        latencies.append(elapsed)
        documents = [item.document_id for item in retrieved]
        unique_documents = unique(documents)
        chunks = [item.chunk_id for item in retrieved]
        qrels = case["qrels"]
        positive = [row for row in qrels if row["relevance"] >= 2]
        core = [row for row in qrels if row["relevance"] == 3]
        effective_expected_route = case["expectedRoute"]
        expected_should_retrieve = effective_expected_route == "retrieve"
        ranking_eligible = expected_should_retrieve and bool(positive)
        rank_positive = first_rank(unique_documents, qrels, 2) if ranking_eligible else None
        rank_core = first_rank(unique_documents, qrels, 3) if expected_should_retrieve and core else None
        required_ids = {row["documentId"] for row in qrels if row["required"]}
        supporting_ids = {chunk_id for row in qrels for chunk_id in row.get("supportingChunkIds", [])}
        # This is a candidate-exposure diagnostic, not an automatic failure.
        # In particular, the constraints course lane intentionally retrieves
        # nearby courses after exact/name/code matching cannot resolve the
        # requested course.  Whether such a candidate is misused must be
        # evaluated at the final-answer source/claim layer.
        forbidden_candidates = [
            document_id
            for document_id in documents
            if any(
                document_id.startswith(prefix)
                for prefix in case["forbiddenDocumentPrefixes"]
            )
        ]
        course_state = (
            resolve_course_evidence_state(
                "course" in decision.knowledge_domains,
                plan,
                retrieved,
            )
            if plan is not None
            else None
        )
        course_state_eligible = (
            case["domain"] == "course"
            and case["expectedKnowledgeState"] in {"answerable", "unknown_after_search"}
        )
        course_state_compatible = (
            course_state == case["expectedKnowledgeState"]
            or (
                case["expectedKnowledgeState"] == "unknown_after_search"
                and course_state == "clue_only"
            )
        )
        a_documents = set(plan.course_document_ids) if plan is not None else set()
        b_document = "GUIDE:course-purchase-policy"
        a_hits = [item.chunk_id for item in retrieved if item.document_id in a_documents]
        b_hits = [item.chunk_id for item in retrieved if item.document_id == b_document]
        c_hits = [item.chunk_id for item in retrieved if plan is not None and (a_documents or plan.include_course_purchase_policy) and item.document_id not in a_documents and item.document_id != b_document]
        tool_policy = (
            decision.tool_policy.model_dump(mode="json")
            if isinstance(decision, (
                RetrieveRouteDecision,
                SkipRagRouteDecision,
            )) else None
        )
        match = effective_matches.get(case["caseId"])
        plan_payload = None if plan is None else {
            "postRetrievalMode": plan.post_retrieval_mode,
            "courseDocumentIds": plan.course_document_ids,
            "courseAQuota": plan.course_a_quota,
            "includeCoursePurchasePolicy": plan.include_course_purchase_policy,
            "primaryGuideCategories": plan.primary_guide_categories,
            "fallbackGuideCategories": plan.fallback_guide_categories,
            "courseAuxiliaryCategories": plan.course_auxiliary_categories,
        }
        rows.append({
            "caseId": case["caseId"], "split": case["split"], "domain": case["domain"], "queryType": case["queryType"], "query": case["query"],
            "datasetExpectedRoute": case["expectedRoute"], "expectedRoute": effective_expected_route, "expectedKnowledgeState": case["expectedKnowledgeState"],
            "expectationOverride": case.get("currentRuntimeReason"),
            "actualRoute": decision.route, "routeCorrect": effective_expected_route == decision.route,
            "ragRouteCorrect": effective_expected_route == decision.route,
            "embeddingCalled": case["caseId"] in vector_by_id,
            "toolPolicy": tool_policy,
            "routeDiagnostics": resolution.diagnostics.model_dump(mode="json"),
            "rankingEligible": ranking_eligible, "qrels": qrels,
            "plan": plan_payload,
            "ragDiagnostics": {"courseMatchMode": match.mode if match else "none", "constraintsFallback": match.constraints_fallback if match else False},
            "courseEvidenceState": course_state,
            "courseEvidenceStateEligible": course_state_eligible,
            "courseEvidenceStateCorrect": None if not course_state_eligible else course_state == case["expectedKnowledgeState"],
            # Golden v1.1 尚无 clue_only；它是 unknown_after_search 的更细粒度子状态。
            "courseEvidenceStateCompatible": None if not course_state_eligible else course_state_compatible,
            "courseLanes": {"aChunkIds": a_hits, "bChunkIds": b_hits, "cChunkIds": c_hits},
            "retrieved": [{"rank": rank, "chunkId": item.chunk_id, "documentId": item.document_id, "sourceType": item.source_type, "title": item.title, "section": item.section, "score": round(float(item.score), 8)} for rank, item in enumerate(retrieved, 1)],
            "retrievedDocumentIds": documents, "retrievedUniqueDocumentIds": unique_documents, "retrievedChunkIds": chunks,
            "firstRelevantRank": rank_positive, "firstCoreRank": rank_core,
            "recallAt1": None if not ranking_eligible else bool(rank_positive and rank_positive <= 1),
            "recallAt3": None if not ranking_eligible else bool(rank_positive and rank_positive <= 3),
            "recallAt5": None if not ranking_eligible else bool(rank_positive and rank_positive <= 5),
            "mrr": None if not expected_should_retrieve or not core else (0.0 if rank_core is None else 1.0 / rank_core),
            "ndcgAt5": ndcg(unique_documents, qrels, 5) if expected_should_retrieve else None,
            "requiredQrelHit": None if not expected_should_retrieve or not required_ids else required_ids.issubset(set(unique_documents)),
            "supportingChunkRecall": None if not expected_should_retrieve or not supporting_ids else len(supporting_ids & set(chunks)) / len(supporting_ids),
            "preferredSourceTop1": None if not expected_should_retrieve or not case["preferredSourceType"] else bool(retrieved and retrieved[0].source_type == case["preferredSourceType"]),
            "forbiddenDocumentCandidateHits": forbidden_candidates,
            "forbiddenDocumentCandidateExposure": bool(forbidden_candidates),
            "retrievalMs": round(elapsed, 4),
            "forbiddenDocumentPrefixes": case["forbiddenDocumentPrefixes"],
        })
    meta = {
        "datasetVersion": manifest["datasetVersion"], "datasetCaseSha256": manifest["caseSha256"], "indexBuildId": retriever.build_name,
        "embeddingQueryCount": len(embedded_cases), "embeddingBatchElapsedSeconds": round(embedding_seconds, 4), "retrievalElapsedMilliseconds": numeric(latencies),
        "settings": {"embeddingModel": settings.embedding_model, "embeddingDimensions": settings.embedding_dimensions, "guideTopK": settings.rag_guide_top_k, "postTopK": settings.rag_post_top_k, "courseAuxiliaryPostTopK": settings.rag_course_auxiliary_post_top_k, "guideScoreThreshold": settings.rag_score_threshold, "postScoreThreshold": settings.rag_post_score_threshold, "guideMaxChunksPerDocument": settings.rag_guide_max_chunks_per_document, "postMaxChunksPerDocument": settings.rag_post_max_chunks_per_document},
    }
    return rows, meta


def metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    ranking = [row for row in rows if row["rankingEligible"]]
    return {
        "caseCount": len(rows), "rankingCaseCount": len(ranking),
        "recallAt1": mean([float(row["recallAt1"]) for row in ranking]), "recallAt3": mean([float(row["recallAt3"]) for row in ranking]), "recallAt5": mean([float(row["recallAt5"]) for row in ranking]),
        "mrr": mean([float(row["mrr"]) for row in ranking if row["mrr"] is not None]), "ndcgAt5": mean([float(row["ndcgAt5"]) for row in ranking if row["ndcgAt5"] is not None]),
        "requiredQrelHitRate": mean([float(row["requiredQrelHit"]) for row in rows if row["requiredQrelHit"] is not None]),
        "supportingChunkRecall": mean([float(row["supportingChunkRecall"]) for row in rows if row["supportingChunkRecall"] is not None]),
        "preferredSourceTop1Rate": mean([float(row["preferredSourceTop1"]) for row in rows if row["preferredSourceTop1"] is not None]),
        "courseEvidenceStateAccuracy": mean([float(row["courseEvidenceStateCorrect"]) for row in rows if row["courseEvidenceStateCorrect"] is not None]),
        "courseEvidenceStateCompatibleAccuracy": mean([float(row["courseEvidenceStateCompatible"]) for row in rows if row["courseEvidenceStateCompatible"] is not None]),
        "retrievalNonEmptyRate": mean([float(bool(row["retrievedDocumentIds"])) for row in rows]), "retrievalMs": numeric([row["retrievalMs"] for row in rows]),
    }


def aggregate(rows: list[dict[str, Any]], meta: dict[str, Any]) -> dict[str, Any]:
    retrieved_expected = [row for row in rows if row["expectedRoute"] == "retrieve"]
    exposure_diagnostics = candidate_exposure_diagnostics(rows)
    report = {
        "status": "PASS", "evaluationType": "golden_v1_1_p0_routed_retrieval", "rankingUnit": "document", "runMeta": meta,
        "overall": {**metrics(retrieved_expected), "allCaseCount": len(rows), "ragRouteAccuracy": mean([float(row["ragRouteCorrect"]) for row in rows]), "ragRouteCorrectCount": sum(row["ragRouteCorrect"] for row in rows)},
        "candidateExposureDiagnostics": exposure_diagnostics,
        "byDomain": {key: metrics(value) for key, value in group(rows, "domain").items()},
        "bySplit": {key: metrics(value) for key, value in group(rows, "split").items()},
        "byRoute": {key: metrics(value) for key, value in group(rows, "expectedRoute").items()},
        "byKnowledgeState": {key: metrics(value) for key, value in group(rows, "expectedKnowledgeState").items()},
        "retrievalConfig": meta["settings"],
    }
    r1, r3, r5 = report["overall"]["recallAt1"], report["overall"]["recallAt3"], report["overall"]["recallAt5"]
    if not (r1 is None or r3 is None or r5 is None or r1 <= r3 <= r5): raise ValueError("Recall@K monotonicity violated")
    return report


def candidate_exposure_diagnostics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    """Summarize diagnostic candidate exposure without treating it as FAIL.

    The legacy fields are accepted when aggregating an already-frozen JSONL,
    but newly generated rows use the candidate-exposure names above.
    """
    checked = [row for row in rows if row.get("forbiddenDocumentPrefixes")]

    def exposed(row: dict[str, Any]) -> bool:
        return bool(
            row.get(
                "forbiddenDocumentCandidateExposure",
                row.get("forbiddenDocumentViolation", False),
            )
        )

    course_repo_prefix_configured = [
        row
        for row in checked
        if row.get("domain") == "course"
        and "GUIDE:course-repo-" in row.get("forbiddenDocumentPrefixes", [])
    ]
    constraint_lane = [
        row
        for row in course_repo_prefix_configured
        if row.get("ragDiagnostics", {}).get("courseMatchMode") == "constraints"
    ]
    other = [row for row in checked if row not in constraint_lane]

    def summary(items: list[dict[str, Any]]) -> dict[str, Any]:
        count = sum(exposed(row) for row in items)
        return {
            "evaluatedCaseCount": len(items),
            "exposedCaseCount": count,
            "exposureRate": None if not items else round(count / len(items), 6),
        }

    return {
        "metricRole": "diagnostic_only",
        "countsAsFailure": False,
        "allConfiguredPrefixes": summary(checked),
        "courseRepoPrefixConfigured": summary(course_repo_prefix_configured),
        "constraintsLaneCrossCourseCandidates": {
            **summary(constraint_lane),
            "interpretation": (
                "Expected degraded retrieval after exact/name/code course matching "
                "does not resolve the requested course; not a failure by itself."
            ),
        },
        "otherCandidateExposure": summary(other),
        "answerSourceOrClaimMisuseMustBeAuditedSeparately": True,
    }


def group(rows: list[dict[str, Any]], key: str) -> dict[str, list[dict[str, Any]]]:
    output: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows: output[str(row[key])].append(row)
    return dict(sorted(output.items()))


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--split", choices=["dev", "test"])
    parser.add_argument("--run-id", default=RUN_ID)
    parser.add_argument("--dataset", type=Path, default=DATASET)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--router-results", type=Path)
    args = parser.parse_args()
    rows, meta = await run(
        args.split,
        args.dataset.resolve(),
        args.manifest.resolve(),
        args.router_results.resolve() if args.router_results else None,
    )
    report = aggregate(rows, meta)
    build_id = meta["indexBuildId"]
    result_path = RESULTS / f"{args.run_id}_{build_id}.jsonl"
    report_path = REPORTS / f"{args.run_id}_{build_id}.json"
    badcase_path = REPORTS / f"{args.run_id}_{build_id}_badcases.json"
    write_jsonl(result_path, rows)
    misses = [row for row in rows if row["recallAt5"] is False]
    routes = [row for row in rows if not row["ragRouteCorrect"]]
    write_json(report_path, report)
    write_json(badcase_path, {"missAt5Count": len(misses), "routeErrorCount": len(routes), "missAt5": misses, "routeErrors": routes})
    print(json.dumps({"resultPath": str(result_path), "reportPath": str(report_path), "badcasePath": str(badcase_path), "report": report}, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__": asyncio.run(main())
