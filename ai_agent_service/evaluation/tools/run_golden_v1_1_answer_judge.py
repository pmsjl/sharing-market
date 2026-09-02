"""Judge Golden v1.1 answers with the human-adjudicated rubric and full evidence."""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import statistics
import sys
from collections import defaultdict
from dataclasses import replace
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parent))
AGENT_ROOT = ROOT / "ai_agent_service"
sys.path.insert(0, str(AGENT_ROOT))

from app.clients.openai_responses import (
    OpenAIResponsesClient,
    OpenAIResponsesClientError,
)
from app.core.config import Settings
from app.rag.course_relations import CourseRelationIndex
from app.rag.index_store import KNOWLEDGE_ROOT as DEFAULT_KNOWLEDGE_ROOT
from app.routing.query_router import DEFAULT_INSTITUTION
from app.prompts.shopping_guide import SYSTEM_PROMPT as AGENT_SYSTEM_PROMPT
from golden_v1_1_round2_paths import REPORTS_DIR, RESULTS_DIR
from app.services.agent_service import AgentService
from course_question_quality import course_metadata

EVAL = AGENT_ROOT / "evaluation"
DATASET = EVAL / "dataset/golden_v1_2_1_reviewed_200.jsonl"
ADJUDICATION = EVAL / "golden/golden_v1_to_v1_1_adjudication.jsonl"
ROUND1 = EVAL / "runs/golden_v1_round1_20260820/results/golden_v1_answer_generation.jsonl"
ROUND2 = RESULTS_DIR / "golden_v1_1_round2_answer_generation.jsonl"
EXPECTED_MODEL = "gpt-5.6-terra"
# 多 Case Judge 已实测发生跨 Case 答案串读：Case ID 顺序正确，但理由和
# 分数描述的是相邻 Case 的答案。单 Case 调用牺牲吞吐量，换取评分绑定可靠性。
BATCH_SIZE = 1
RUBRIC_VERSION = "v2_current_runtime"
BADCASES = REPORTS_DIR / f"golden_v1_1_round2_answer_evaluation_badcases_{RUBRIC_VERSION}.json"
FORMAT = {
    "type": "json_schema",
    "name": "golden_answer_judgment_single",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "required": ["items"],
        "properties": {
            "items": {
                "type": "array",
                "minItems": 1,
                "maxItems": BATCH_SIZE,
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "required": [
                        "caseId", "answerRelevance", "factCoverage",
                        "groundedness", "actionAppropriateness",
                        "citationAlignment", "observedKnowledgeState",
                        "auditedExpectedKnowledgeState", "knowledgeStateCorrect", "expectationIssue",
                        "expectationIssueReason", "expectedFactAssessments",
                        "evidenceMisrepresentation", "contradictsEvidence",
                        "criticalError", "criticalErrorTypes", "overallPass",
                        "unsupportedClaims", "reason",
                    ],
                    "properties": {
                        "caseId": {"type": "string"},
                        "answerRelevance": {"type": "integer", "minimum": 0, "maximum": 4},
                        "factCoverage": {"type": "integer", "minimum": 0, "maximum": 4},
                        "groundedness": {"type": "integer", "minimum": 0, "maximum": 4},
                        "actionAppropriateness": {"type": "integer", "minimum": 0, "maximum": 4},
                        "citationAlignment": {"type": "integer", "minimum": 0, "maximum": 4},
                        "observedKnowledgeState": {"enum": ["answerable", "unknown_after_search", "not_applicable", "contradictory_or_unclear"]},
                        "auditedExpectedKnowledgeState": {"enum": ["answerable", "unknown_after_search", "not_applicable", "contradictory_or_unclear"]},
                        "knowledgeStateCorrect": {"type": "boolean"},
                        "expectationIssue": {"type": "boolean"},
                        "expectationIssueReason": {"type": "string", "maxLength": 500},
                        "expectedFactAssessments": {
                            "type": "array",
                            "items": {
                                "type": "object", "additionalProperties": False,
                                "required": ["index", "covered", "criticalIfMissing", "reason"],
                                "properties": {
                                    "index": {"type": "integer", "minimum": 0},
                                    "covered": {"type": "boolean"},
                                    "criticalIfMissing": {"type": "boolean"},
                                    "reason": {"type": "string", "maxLength": 300},
                                },
                            },
                        },
                        "evidenceMisrepresentation": {"type": "boolean"},
                        "contradictsEvidence": {"type": "boolean"},
                        "criticalError": {"type": "boolean"},
                        "criticalErrorTypes": {
                            "type": "array", "maxItems": 4,
                            "items": {"enum": [
                                "wrong_object_or_query", "missing_core_answer",
                                "wrong_action_or_scope", "critical_fact_error",
                                "unsupported_decision_claim", "dangerous_advice", "other"
                            ]},
                        },
                        "overallPass": {"type": "boolean"},
                        "unsupportedClaims": {"type": "array", "maxItems": 6, "items": {"type": "string", "maxLength": 400}},
                        "reason": {"type": "string", "maxLength": 1200},
                    },
                },
            },
        },
    },
}

SYSTEM = """你是校园二手交易 Agent 的单 Case 审核员。每次输入只有一个 Case；所有分数和理由只能描述当前 Query 与当前 answer，不得借用同 Case ID 的旧问题，也不得引用其他 Case。

只使用输入中提供的当前 Query、核心 expectedFacts、expectedRoute、expectedKnowledgeState、runtimeSystemPrompt、trustedRuntimeContext、课程关系、检索 Chunk、referenceAudit、工具轨迹和最终答案。不得补充外部知识。

## 五维评分
五项均为 0-4：4 完整，3 基本满足且仅轻微不足，2 有明显但非必然致命的问题，1 严重不足，0 完全错误。

- answerRelevance：是否直接解决用户的当前核心决策。clarify 答案只需取得足以推进下一步的决定性条件，不要求一次穷举所有可能背景。
- factCoverage：只检查输入明确列出的 expectedFacts。必须为每个 expectedFact 按原顺序输出一条 expectedFactAssessments；index 从0开始且不得遗漏或增加。qrel、Chunk 和参考答案中的其他细节只能用于判断证据，不能被提升成新的必答项。理想增强项、非关键细节、可后续追问的信息或未穷举全部检查项，不应造成严重扣分。
- groundedness：关键且确定性的事实必须由输入证据支持。明确写成“建议、可以、通常、优先考虑、更稳妥、如果……再……”的低风险常识性延伸，不因来源没有同句而自动算 unsupported。冒充学校/平台规定、虚构课程当期安排、错误声称资料中出现某软件/型号、精确数字规则、危险技术结论或无依据保证，才属于实质无依据断言。
- actionAppropriateness：expectedRoute 是重要参考，但不是不可质疑的标签。若 Query 按 runtimeSystemPrompt 明显超出产品范围，或 retrieve/clarify 均有合理解释，可设置 expectationIssue=true，并按真实产品边界评价答案。不得因为选择合理 clarify 而机械判错。
- citationAlignment：评价答案实际提供的来源能否支持关键说法。产品契约不要求最终正文显示 K/C 短引用；缺少 K/C、未逐句内联引用、citationPrecision 低或未引用全部 qrel，均不得单独导致 overallPass=false。若存在 K/C，使用 referenceAudit.referenceMap 还原，不能把合法别名说成无法核验。

## 知识状态
answerable 表示证据足以回答核心决策；unknown_after_search 表示检索后课程指定版本、实时状态或隐藏事实仍未知；not_applicable 用于合理 clarify、out_of_scope 或 skip_rag。答案可在说明课程专属结论未知后给出通用核验路径。

## 整体 PASS 与关键错误
整体结论采用用户决策价值判断，不采用“五项全部>=3”的机械门槛。一个维度为2、轻微遗漏、谨慎建议、合理延伸或引用展示问题，不应自动 FAIL。

只有以下会改变用户决策或破坏任务完成的缺陷才设置 criticalError=true：
1. 回答了错误对象、课程、年级或另一问题；
2. 没有回答核心问题，或者把范围内问题错误拒答；
3. 关键动作/范围错误，且没有合理的标签歧义；
4. 关键事实错误、与证据矛盾、虚构资料内容或课程当期安排；若答案明确声称“资料/检索结果提到某软件、型号、版本或安排”，但输入证据并未出现，必须设置 evidenceMisrepresentation=true。只有答案与证据形成实际冲突时才设置 contradictsEvidence=true；比证据更谨慎、要求进一步确认、或没有复述全部细节，不属于冲突；
5. 无依据的关键购买结论、危险建议或会造成明显风险的误导。

非关键遗漏、未展示 K/C、引用不够贴近、常识性低风险建议、谨慎措辞和可选增强项不得列为 criticalError。missing_core_answer 只能来自 expectedFactAssessments 中 criticalIfMissing=true 且 covered=false 的项目；不得从 qrel 或 Chunk 临时创造核心遗漏。critical_fact_error 只能在 evidenceMisrepresentation=true 或 contradictsEvidence=true 时使用。

设置 overallPass=true 的条件是：核心问题得到解决，知识状态基本正确，没有 criticalError 或 evidenceMisrepresentation；允许最多一个核心维度（answerRelevance、factCoverage、groundedness、actionAppropriateness）为2。citationAlignment 不参与硬门槛。若 expectationIssue=true，说明原 Gold 的 route/state/facts 不能完整代表真实产品边界，此时不得再用原 factCoverage 作为硬门槛，但仍需检查答案是否真正回应 Query。

如果 expectationIssue=true，必须在 expectationIssueReason 中说明一般化的标签问题，例如“问题本身不涉及二手交易”或“缺少决定性条件时 clarify 与 retrieve 均合理”，不得仅凭 Case ID 宣告例外；同时把 auditedExpectedKnowledgeState 设置为按真实产品边界应有的状态。如果 expectationIssue=false，auditedExpectedKnowledgeState 必须等于输入 expectedKnowledgeState。

reason 必须说明真正决定 PASS/FAIL 的核心理由。不得把建议性表述歪曲为强制规定，也不得把缺少 K/C 当作 FAIL 的唯一原因。"""


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def extract(response: dict[str, Any]) -> dict[str, Any]:
    texts = []
    for item in AgentService._extract_output_items(response):
        if item.get("type") != "message": continue
        for content in item.get("content", []):
            if isinstance(content, dict) and content.get("type") == "output_text" and isinstance(content.get("text"), str): texts.append(content["text"])
    if not texts: raise ValueError("judge returned no output_text")
    value = json.loads("".join(texts))
    if not isinstance(value, dict) or not isinstance(value.get("items"), list): raise ValueError("invalid judge output")
    return value


def model_name(response: dict[str, Any]) -> str:
    value = response.get("model")
    if isinstance(value, dict): value = value.get("name")
    return value if isinstance(value, str) else ""


def relation_payload(row: dict[str, Any], relations: CourseRelationIndex) -> list[dict[str, Any]]:
    rag_value = row.get("rag")
    rag = rag_value if isinstance(rag_value, dict) else {}
    existing = rag.get("courseRelationSummaries")
    if isinstance(existing, list): return existing
    course_match = relations.match(row["query"], allow_dimension_only=True)
    return [item.model_dump(mode="json") for item in course_match.relation_summaries]


def effective_expectations(row: dict[str, Any], truth: dict[str, Any]) -> dict[str, Any]:
    """Resolve the current runtime truth without mutating the frozen dataset."""
    return {
        "expectedRoute": row.get("expectedRoute", truth["expectedRoute"]),
        "expectedKnowledgeState": row.get("expectedKnowledgeState", truth["expectedKnowledgeState"]),
        "expectedFacts": row.get("expectedFacts", truth["expectedFacts"]),
    }


def payload(row: dict[str, Any], truth: dict[str, Any], relations: CourseRelationIndex, round_name: str) -> dict[str, Any]:
    rag_value = row.get("rag")
    rag = rag_value if isinstance(rag_value, dict) else {}
    expected = effective_expectations(row, truth)
    if truth.get("domain") == "course":
        metadata = course_metadata({
            "question": truth["query"],
            "decision_dimension": truth.get("provenance", {}).get("decisionDimension"),
            "questionIntent": truth.get("questionIntent"),
            "questionQuality": truth.get("questionQuality"),
        })
        quality = metadata.get("questionQuality") or {}
        question_intent = (
            metadata.get("questionIntent")
            or quality.get("inferredIntent")
            or truth.get("provenance", {}).get("decisionDimension")
        )
        question_quality = quality.get("score", 0)
        natural_language_problem = metadata.get(
            "naturalLanguageProblem",
            quality.get("naturalLanguageProblem", False),
        )
    else:
        question_intent = None
        question_quality = 3
        natural_language_problem = False
    return {
        "caseId": row["caseId"], "query": truth["query"], "questionIntent": question_intent, "questionQuality": question_quality, "naturalLanguageProblem": natural_language_problem, "currentRuntimeExpectation": "自然语言课程问题应按用户真实购买决策评估；unknown_after_search允许明确未知后给出核验路径。", **expected,
        "runtimeSystemPrompt": row.get("runtimeSystemPrompt") or AGENT_SYSTEM_PROMPT,
        "trustedRuntimeContext": {
            "institution": DEFAULT_INSTITUTION,
            "courseEvidenceState": rag.get("courseEvidenceState"),
            "expectationOverride": row.get("expectationOverride"),
        },
        "systemCurrentDate": row.get("systemCurrentDate") or ("2026-08-20" if round_name == "round1" else "2026-08-21"),
        "courseRelationSummaries": relation_payload(row, relations),
        "evidence": [{"documentId": item["documentId"], "chunkId": item["chunkId"], "title": item["title"], "section": item.get("section"), "content": item["content"]} for item in rag.get("retrieved", [])],
        "answer": row["answer"],
        "referenceAudit": row.get("referenceAudit"),
        "sources": row.get("response", {}).get("output", {}).get("sources", []), "citationPrecision": row.get("citationPrecision"), "requiredCitationRecall": row.get("requiredCitationRecall"), "toolNames": row.get("toolNames", []), "toolTraces": row.get("response", {}).get("traces", []),
    }


def normalize_critical_errors(
    item: dict[str, Any], expected_fact_count: int,
) -> None:
    assessments = item["expectedFactAssessments"]
    indexes = [row["index"] for row in assessments]
    if indexes != list(range(expected_fact_count)):
        raise ValueError(
            "expectedFactAssessments must cover expectedFacts exactly in order"
        )
    critical_missing = any(
        not row["covered"] and row["criticalIfMissing"]
        for row in assessments
    )
    types = list(dict.fromkeys(item["criticalErrorTypes"]))
    if not critical_missing:
        types = [value for value in types if value != "missing_core_answer"]
    factual_critical = bool(
        item.get("evidenceMisrepresentation", False)
        or item.get("contradictsEvidence", False)
    )
    if not factual_critical:
        types = [value for value in types if value != "critical_fact_error"]
    if item.get("evidenceMisrepresentation", False) and "critical_fact_error" not in types:
        types.append("critical_fact_error")
    item["criticalErrorTypes"] = types
    item["criticalError"] = bool(types)
    if all(row["covered"] for row in assessments):
        item["factCoverage"] = max(item["factCoverage"], 3)


def calculate_knowledge_state_correct(
    item: dict[str, Any], expected_state: str,
) -> bool:
    audited_state = (
        item["auditedExpectedKnowledgeState"]
        if item.get("expectationIssue", False)
        else expected_state
    )
    return item["observedKnowledgeState"] == audited_state


def calculate_holistic_pass(item: dict[str, Any]) -> bool:
    """Apply the user-approved general pass policy without citation gating."""
    score_keys = ["answerRelevance", "groundedness", "actionAppropriateness"]
    if not item.get("expectationIssue", False):
        score_keys.insert(1, "factCoverage")
    core_scores = [item[key] for key in score_keys]
    return (
        bool(item["knowledgeStateCorrect"])
        and not bool(item["criticalError"])
        and not bool(item.get("evidenceMisrepresentation", False))
        and min(core_scores) >= 2
        and sum(score < 3 for score in core_scores) <= 1
    )


async def judge_batch(client: OpenAIResponsesClient, rows: list[dict[str, Any]], truths: dict[str, dict[str, Any]], relations: CourseRelationIndex, round_name: str) -> list[dict[str, Any]]:
    expected = [row["caseId"] for row in rows]
    data = [payload(row, truths[row["caseId"]], relations, round_name) for row in rows]
    for attempt in range(5):
        try:
            response = await client.create_response(input_items=[{"role": "system", "content": SYSTEM}, {"role": "user", "content": json.dumps(data, ensure_ascii=False)}], tools=[], text_format=FORMAT)
            model = model_name(response)
            if model != EXPECTED_MODEL: raise ValueError(f"unexpected judge model: {model}")
            items = extract(response)["items"]
            if [item["caseId"] for item in items] != expected: raise ValueError("judge case order mismatch")
            usage_value = response.get("usage")
            usage = usage_value if isinstance(usage_value, dict) else {}
            share_in = (usage.get("input_tokens") or 0) / len(items)
            share_out = (usage.get("output_tokens") or 0) / len(items)
            for item in items:
                row = next(value for value in rows if value["caseId"] == item["caseId"])
                truth = truths[item["caseId"]]
                expected_state = effective_expectations(row, truth)["expectedKnowledgeState"]
                item["knowledgeStateCorrect"] = calculate_knowledge_state_correct(
                    item, expected_state,
                )
                normalize_critical_errors(item, len(truth.get("expectedFacts", [])))
                item["overallPass"] = calculate_holistic_pass(item)
                item.update({"judgeModel": model, "judgeInputTokens": share_in, "judgeOutputTokens": share_out})
            return items
        except Exception as exc:
            if (
                isinstance(exc, OpenAIResponsesClientError)
                and exc.agent_error_key in {
                    "AI_MODEL_UNAVAILABLE",
                    "AI_MODEL_TIMEOUT",
                }
            ):
                if attempt >= 3:
                    raise RuntimeError(
                        "Judge model infrastructure unavailable after 3 retries"
                    ) from exc
                await asyncio.sleep(min(30, 2 ** (attempt + 1)))
                continue
            if attempt == 4: raise RuntimeError(f"judge failed after 5 attempts: {exc}") from exc
            await asyncio.sleep(min(30, 2 ** (attempt + 1)))
    raise AssertionError("unreachable")


def mean(rows: list[dict[str, Any]], key: str) -> float | None:
    values = [float(row[key]) for row in rows if row.get(key) is not None]
    return round(statistics.fmean(values), 6) if values else None


def grouped(rows: list[dict[str, Any]], key: str) -> dict[str, list[dict[str, Any]]]:
    result: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows: result[str(row[key])].append(row)
    return dict(sorted(result.items()))


def summary(generated: list[dict[str, Any]], judged: list[dict[str, Any]], truths: dict[str, dict[str, Any]], human: dict[str, bool | None] | None) -> tuple[dict[str, Any], dict[str, Any]]:
    generated_by_id = {row["caseId"]: row for row in generated}
    merged = [{**generated_by_id[item["caseId"]], "truth": truths[item["caseId"]], "judgment": item} for item in judged]
    keys = ["answerRelevance", "factCoverage", "groundedness", "actionAppropriateness", "citationAlignment"]
    def metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
        return {"caseCount": len(rows), "passRate": round(sum(row["judgment"]["overallPass"] for row in rows) / len(rows), 6) if rows else None, "knowledgeStateAccuracy": round(sum(row["judgment"]["knowledgeStateCorrect"] for row in rows) / len(rows), 6) if rows else None, **{key: mean([row["judgment"] for row in rows], key) for key in keys}, "meanCitationPrecision": mean(rows, "citationPrecision"), "meanRequiredCitationRecall": mean(rows, "requiredCitationRecall"), "toolSelectionAccuracy": mean([{"value": float(row["toolSelectionCorrect"])} for row in rows if row.get("searchToolExpected") or row.get("searchToolForbidden")], "value")}
    result = {
        "status": "COMPLETE", "datasetVersion": generated[0].get("datasetVersion", "golden-v1.1") if generated else "golden-v1.1", "rubricVersion": RUBRIC_VERSION, "judgeModel": EXPECTED_MODEL, "judgeUsesFullChunks": True, "judgeUsesRuntimeSystemPrompt": True, "judgeUsesTrustedInstitution": True, "sameModelAsGeneration": True,
        "overall": metrics(merged), "byDomain": {key: metrics(value) for key, value in grouped(merged, "domain").items()}, "byRoute": {key: metrics(value) for key, value in grouped([dict(row, expectedRoute=effective_expectations(row, row["truth"])["expectedRoute"]) for row in merged], "expectedRoute").items()}, "byKnowledgeState": {key: metrics(value) for key, value in grouped([dict(row, expectedKnowledgeState=effective_expectations(row, row["truth"])["expectedKnowledgeState"]) for row in merged], "expectedKnowledgeState").items()},
        "generation": {"caseCount": len(generated), "successCount": sum(row["status"] == "SUCCESS" for row in generated), "inputTokens": sum((row.get("response", {}).get("usage", {}).get("inputTokens") or 0) for row in generated), "outputTokens": sum((row.get("response", {}).get("usage", {}).get("outputTokens") or 0) for row in generated)},
        "judge": {"inputTokens": round(sum(row.get("judgeInputTokens", 0) for row in judged)), "outputTokens": round(sum(row.get("judgeOutputTokens", 0) for row in judged))},
    }
    if human is not None:
        comparable = [row for row in merged if human.get(row["caseId"]) is not None]
        agreement = sum(row["judgment"]["overallPass"] == human[row["caseId"]] for row in comparable) / len(comparable)
        human_fails = [row for row in comparable if human[row["caseId"]] is False]
        human_passes = [row for row in comparable if human[row["caseId"]] is True]
        fail_recall = sum(not row["judgment"]["overallPass"] for row in human_fails) / len(human_fails)
        pass_recall = sum(row["judgment"]["overallPass"] for row in human_passes) / len(human_passes)
        by_domain = {}
        for domain, rows in grouped(comparable, "domain").items(): by_domain[domain] = round(sum(row["judgment"]["overallPass"] == human[row["caseId"]] for row in rows) / len(rows), 6)
        result["humanCalibration"] = {"caseCount": len(comparable), "overallAgreement": round(agreement, 6), "humanFailRecall": round(fail_recall, 6), "humanPassRecall": round(pass_recall, 6), "byDomainAgreement": by_domain, "target": {"overallAgreement": 0.9, "humanFailRecall": 0.85, "minimumDomainAgreement": 0.8}, "status": "PASS" if agreement >= 0.9 and fail_recall >= 0.85 and min(by_domain.values()) >= 0.8 else "FAIL"}
    bad = [
        row for row in merged
        if not row["judgment"]["overallPass"]
        or not row.get("toolSelectionCorrect", True)
    ]
    badcases = {"count": len(bad), "items": [{"caseId": row["caseId"], "domain": row["domain"], "query": row["query"], "expectedRoute": row["truth"]["expectedRoute"], "expectedKnowledgeState": row["truth"]["expectedKnowledgeState"], "citationPrecision": row.get("citationPrecision"), "requiredCitationRecall": row.get("requiredCitationRecall"), "toolSelectionCorrect": row.get("toolSelectionCorrect"), "judgment": row["judgment"]} for row in bad]}
    return result, badcases


JUDGE_BINDING_FIELDS = (
    "query", "expectedRoute", "expectedKnowledgeState", "expectedFacts", "qrels",
)


def validate_dataset_generation_binding(
    truths: dict[str, dict[str, Any]],
    generated_all: list[dict[str, Any]],
) -> None:
    generated_ids = {row["caseId"] for row in generated_all}
    if generated_ids != set(truths):
        raise ValueError("dataset/generation Case set mismatch")
    mismatches: list[dict[str, Any]] = []
    for row in generated_all:
        truth = truths[row["caseId"]]
        fields = [
            field for field in JUDGE_BINDING_FIELDS
            if row.get(field) != truth.get(field)
        ]
        if fields:
            mismatches.append({"caseId": row["caseId"], "fields": fields})
    if mismatches:
        raise ValueError(
            "dataset/generation truth mismatch; pass the dataset used for "
            f"generation: {mismatches[:10]}"
        )


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--round", choices=["round1", "round2"], required=True)
    parser.add_argument("--dataset", type=Path, default=DATASET)
    parser.add_argument("--generation", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--badcases", type=Path)
    parser.add_argument(
        "--force-case-id",
        action="append",
        default=[],
        help="discard an existing judgment for this Case and judge it again",
    )
    args = parser.parse_args()
    generation_path = args.generation.resolve() if args.generation else (ROUND1 if args.round == "round1" else ROUND2)
    results_dir = EVAL / "runs/golden_v1_1_round1_rejudge_20260821/results" if args.round == "round1" else RESULTS_DIR
    reports_dir = EVAL / "runs/golden_v1_1_round1_rejudge_20260821/reports" if args.round == "round1" else REPORTS_DIR
    output = args.output.resolve() if args.output else results_dir / f"golden_v1_1_{args.round}_answer_judgments_{RUBRIC_VERSION}.jsonl"
    report = args.report.resolve() if args.report else reports_dir / f"golden_v1_1_{args.round}_answer_evaluation_summary_{RUBRIC_VERSION}.json"
    badcases_path = args.badcases.resolve() if args.badcases else BADCASES
    truths = {row["caseId"]: row for row in read_jsonl(args.dataset.resolve())}
    generated_all = read_jsonl(generation_path)
    validate_dataset_generation_binding(truths, generated_all)
    generated = [row for row in generated_all if row["status"] == "SUCCESS"]
    if not generated:
        raise ValueError("no successful generated rows to judge")
    existing = {row["caseId"]: row for row in read_jsonl(output)} if output.exists() else {}
    unknown_force_ids = set(args.force_case_id) - {row["caseId"] for row in generated}
    if unknown_force_ids:
        raise ValueError(f"unknown --force-case-id values: {sorted(unknown_force_ids)}")
    for case_id in args.force_case_id:
        existing.pop(case_id, None)
    pending = [row for row in generated if row["caseId"] not in existing]
    settings = replace(Settings(), openai_model=EXPECTED_MODEL, openai_timeout_seconds=240, openai_reasoning_effort="medium", openai_text_verbosity="low")
    client = OpenAIResponsesClient(settings)
    relations = CourseRelationIndex.load(Path(os.getenv("GOLDEN_KNOWLEDGE_ROOT", str(DEFAULT_KNOWLEDGE_ROOT))))
    for start in range(0, len(pending), BATCH_SIZE):
        batch = pending[start:start + BATCH_SIZE]
        items = await judge_batch(client, batch, truths, relations, args.round)
        for item in items: existing[item["caseId"]] = item
        ordered = [existing[row["caseId"]] for row in generated if row["caseId"] in existing]
        write_jsonl(output, ordered)
        print(f"judged {len(ordered)}/{len(generated)}", flush=True)
    judged = [existing[row["caseId"]] for row in generated]
    human = None
    if args.round == "round1": human = {row["caseId"]: row["humanOverallPass"] for row in read_jsonl(ADJUDICATION)}
    result, bad = summary(generated, judged, truths, human)
    write_json(report, result)
    write_json(badcases_path, bad)
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__": asyncio.run(main())
