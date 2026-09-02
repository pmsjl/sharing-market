"""评估混合式Guardrail + LLM Intent Router，不调用Embedding、RAG或业务工具。"""
from __future__ import annotations

import argparse
import asyncio
import json
import hashlib
import os
import statistics
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[3]
AGENT_ROOT = ROOT / "ai_agent_service"
sys.path.insert(0, str(AGENT_ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.clients.openai_responses import OpenAIResponsesClient
from app.core.config import Settings
from app.models.agent import AgentRunRequest
from app.rag.course_relations import CourseRelationIndex
from app.rag.index_store import KNOWLEDGE_ROOT as DEFAULT_KNOWLEDGE_ROOT
from app.routing.query_router import HybridQueryRouter
from golden_v1_1_round2_paths import RUN_ROOT

DATASET = AGENT_ROOT / "evaluation/dataset/golden_v1_2_1_reviewed_200.jsonl"
def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "".join(
            json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n"
            for row in rows
        ),
        encoding="utf-8",
    )


async def evaluate_case(
    case: dict[str, Any],
    router: HybridQueryRouter,
    relations: CourseRelationIndex,
    semaphore: asyncio.Semaphore,
) -> dict[str, Any]:
    request = AgentRunRequest(
        userId=1,
        conversationId=1,
        message=case["query"],
        history=case.get("history", []),
        shoppingContext=case.get("shoppingContext"),
        memorySummary=case.get("memorySummary"),
    )
    async with semaphore:
        course_match = relations.match(
            case["query"], allow_dimension_only=True)
        resolution = await router.resolve(request, course_match)
    decision = resolution.decision
    diagnostics = resolution.diagnostics
    actual_domains = set(
        decision.knowledge_domains
        if decision.route == "retrieve" else []
    )
    required_domains = set(case.get("expectedRequiredKnowledgeDomains", []))
    forbidden_domains = set(case.get("expectedForbiddenKnowledgeDomains", []))
    domain_contract_checked = bool(required_domains or forbidden_domains)
    domain_contract_correct = (
        required_domains.issubset(actual_domains)
        and actual_domains.isdisjoint(forbidden_domains)
        if domain_contract_checked else None
    )
    effective_expected = case["expectedRoute"]
    return {
        "caseId": case["caseId"],
        "split": case["split"],
        "domain": case["domain"],
        "query": case["query"],
        "datasetExpectedRoute": case["expectedRoute"],
        "expectedRoute": effective_expected,
        "expectationOverride": case.get("currentRuntimeReason"),
        "actualRoute": decision.route,
        "routeCorrect": decision.route == effective_expected,
        "expectedDecisionSource": case.get("expectedDecisionSource"),
        "decisionSourceCorrect": (
            diagnostics.decision_source == case["expectedDecisionSource"]
            if case.get("expectedDecisionSource") else None
        ),
        "expectedRequiredKnowledgeDomains": sorted(required_domains),
        "expectedForbiddenKnowledgeDomains": sorted(forbidden_domains),
        "domainContractCorrect": domain_contract_correct,
        "decision": decision.model_dump(mode="json"),
        "diagnostics": diagnostics.model_dump(mode="json"),
    }


def summarize(
    rows: list[dict[str, Any]], settings: Settings, dataset_name: str
) -> dict[str, Any]:
    latencies = [row["diagnostics"]["latency_ms"] for row in rows]
    by_source = Counter(
        row["diagnostics"]["decision_source"] for row in rows
    )
    misses = [row["caseId"] for row in rows if not row["routeCorrect"]]
    source_misses = [
        row["caseId"] for row in rows
        if row["decisionSourceCorrect"] is False
    ]
    domain_misses = [
        row["caseId"] for row in rows
        if row["domainContractCorrect"] is False
    ]
    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "dataset": dataset_name,
        "routerModel": settings.openai_router_model,
        "confidenceThreshold": settings.intent_router_confidence_threshold,
        "caseCount": len(rows),
        "routeCorrectCount": len(rows) - len(misses),
        "routeAccuracy": (
            (len(rows) - len(misses)) / len(rows) if rows else None
        ),
        "decisionSourceCounts": dict(sorted(by_source.items())),
        "inputTokens": sum(
            row["diagnostics"]["input_tokens"] for row in rows
        ),
        "outputTokens": sum(
            row["diagnostics"]["output_tokens"] for row in rows
        ),
        "latencyMs": {
            "mean": round(statistics.fmean(latencies), 2) if latencies else None,
            "median": round(statistics.median(latencies), 2) if latencies else None,
            "max": max(latencies) if latencies else None,
        },
        "missCaseIds": misses,
        "decisionSourceCorrectCount": sum(
            row["decisionSourceCorrect"] is True for row in rows
        ),
        "decisionSourceCheckedCount": sum(
            row["decisionSourceCorrect"] is not None for row in rows
        ),
        "decisionSourceMissCaseIds": source_misses,
        "domainContractCorrectCount": sum(
            row["domainContractCorrect"] is True for row in rows
        ),
        "domainContractCheckedCount": sum(
            row["domainContractCorrect"] is not None for row in rows
        ),
        "domainContractMissCaseIds": domain_misses,
    }


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--split", choices=["dev", "test"])
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--run-id", default="golden_v1_1_hybrid_router")
    parser.add_argument("--dataset", type=Path, default=DATASET)
    parser.add_argument(
        "--local-expectations",
        type=Path,
        help=("Local-only JSONL keyed by querySha256. This metadata is merged "
              "before scoring but is never included in the Router request."),
    )
    parser.add_argument("--case-id", action="append", default=[])
    parser.add_argument("--misses-from", type=Path)
    parser.add_argument("--require-llm", action="store_true")
    args = parser.parse_args()

    dataset_path = args.dataset.resolve()
    cases = read_jsonl(dataset_path)
    if args.local_expectations:
        expectations = {
            row["querySha256"]: row
            for row in read_jsonl(args.local_expectations.resolve())
        }
        merged_cases = []
        for public_row in cases:
            if set(public_row) - {"query", "history", "shoppingContext", "memorySummary"}:
                raise ValueError(
                    "privacy-minimal dataset may only contain Router-visible fields")
            query_hash = hashlib.sha256(
                public_row["query"].encode("utf-8")).hexdigest()
            metadata = expectations.get(query_hash)
            if metadata is None:
                raise ValueError(
                    f"missing local expectation for query hash {query_hash}")
            merged_cases.append({**metadata, **public_row})
        cases = merged_cases
    if args.split:
        cases = [case for case in cases if case["split"] == args.split]
    if args.case_id:
        selected = set(args.case_id)
        cases = [case for case in cases if case["caseId"] in selected]
    if args.misses_from:
        previous = read_jsonl(args.misses_from)
        selected = {
            row["caseId"] for row in previous if not row.get("routeCorrect")
        }
        cases = [case for case in cases if case["caseId"] in selected]
    settings = Settings()
    router = HybridQueryRouter(settings, OpenAIResponsesClient(settings))
    relations = CourseRelationIndex.load(Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT))))
    semaphore = asyncio.Semaphore(max(1, args.concurrency))
    rows = await asyncio.gather(*(
        evaluate_case(case, router, relations, semaphore)
        for case in cases
    ))
    rows = list(rows)
    suffix = f"_{args.split}" if args.split else ""
    result_path = RUN_ROOT / "results" / f"{args.run_id}{suffix}.jsonl"
    report_path = RUN_ROOT / "reports" / f"{args.run_id}{suffix}.json"
    write_jsonl(result_path, rows)
    if args.require_llm:
        invalid = [
            row["caseId"] for row in rows
            if row["diagnostics"].get("decision_source") != "llm"
            or (row["diagnostics"].get("input_tokens") or 0) <= 0
            or (row["diagnostics"].get("output_tokens") or 0) <= 0
        ]
        if invalid:
            raise RuntimeError(
                "strict LLM Router gate failed for: " + ", ".join(invalid)
            )
    summary = summarize(rows, settings, dataset_path.stem)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    asyncio.run(main())
