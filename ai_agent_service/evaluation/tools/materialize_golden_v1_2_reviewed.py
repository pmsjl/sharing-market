"""Materialize the user-reviewed Golden v1.2 questions without rewriting them.

The authoritative wording lives in the two manual-review Markdown files.  This
script only binds those approved strings to the already audited Golden v1.1
metadata, using the same year/source reassignment that produced the accepted
course smoke input.  It never reads the obsolete template-draft JSONL.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from copy import deepcopy
from pathlib import Path
from typing import Any

from course_question_quality import question_quality
from golden_current_runtime_expectations import apply_current_runtime_truth


ROOT = Path(__file__).resolve().parents[3]
WORKSPACE = ROOT.parent
EVAL = ROOT / "ai_agent_service/evaluation"
# 一次性历史物化工具：v1.1 数据集已由 golden-v1.2.1-reviewed 取代，
# evaluation/golden/ 不再保留源文件；如需重跑需自行提供 v1.1 数据集。
SOURCE = EVAL / "golden/golden_dataset_v1_1.jsonl"
SOURCE_MANIFEST = EVAL / "golden/golden_dataset_v1_1_manifest.json"
REVIEW_ROOT = (
    WORKSPACE
    / "sharing-market-v1.0.pre-rewrite-20260828-180554"
    / "ai_agent_service/evaluation/human_review"
    / "golden_v1_2_course_question_draft_20260828"
)
ALL_REVIEW = REVIEW_ROOT / "all_200_questions_manual_review.md"
COURSE_REVIEW = REVIEW_ROOT / "course_70_manual_review.md"
ACCEPTED_SMOKE = (
    WORKSPACE
    / "sharing-market-v1.0.pre-rewrite-20260828-180554"
    / "ai_agent_service/evaluation/runs"
    / "golden_v1_2_course_smoke_35_rerun_20260828/input/course_smoke_35.jsonl"
)
DEFAULT_OUTPUT_DIR = (
    EVAL / "runs/golden_v1_2_current_router_20260829/input"
)
DATASET_VERSION = "golden-v1.2.1-reviewed-20260829"
DATASET_FILENAME = "golden_v1_2_1_reviewed_200.jsonl"
MANIFEST_FILENAME = "golden_v1_2_1_reviewed_200_manifest.json"

INTENTS = (
    "purchase_requirement",
    "purchase_timing",
    "version_or_model",
    "school_provided",
    "second_hand_fit",
    "compatibility_check",
    "reuse_or_resale",
)
YEAR_COUNTS = Counter({2025: 20, 2024: 20, 2023: 15, 2022: 8, 2021: 4, 2020: 2, 2019: 1})

# Reviewed course facts are still organized by intent, but route/state are no
# longer applied uniformly. Scope follows the current product contract:
# teaching-support-only questions are out of scope, while explicit purchasing,
# second-hand, resale, or physical-item fit decisions remain retrievable.
# The classifier below contains no Case-ID exceptions.
COURSE_OUT_OF_SCOPE_EXPECTED_FACTS = [
    "该问题只询问课程教学环境、软件、计算资源或器材供给，没有商品购买、二手取得、商品适配、验货、转卖或处置决策。",
    "应说明该问题超出校园二手交易咨询范围，并停止知识检索和商品工具调用。",
]
COURSE_TRANSACTION_MARKERS = (
    "买", "购买", "二手", "转卖", "出售", "采购", "下单", "自费",
    "升级电脑", "换电脑",
)
COURSE_PHYSICAL_FIT_OBJECTS = (
    "教材", "书", "开发板", "控制板", "机械部件", "机械臂", "传感器",
    "配套模块", "器材", "计算器",
)


COURSE_EXPECTED_FACTS = {
    "purchase_requirement": (
        "当前课程是否要求个人购买仍需以教师、课程组或实验室通知为准；"
        "不能仅凭资料提及推断必须自费。"
    ),
    "purchase_timing": (
        "可以给出谨慎的预习和准备顺序，但准确书单、版本和购买时间仍应以"
        "当前课程通知为准。"
    ),
    "version_or_model": (
        "公开资料只能作为书目或型号线索；购买前应核对教师要求、书名、作者、"
        "出版社、版次、ISBN、具体型号、附件和软件环境中与该问题有关的项目。"
    ),
    "school_provided": (
        "已记录的资料不能单独证明本学期统一发放、借用或自备安排；"
        "应向教师、课程组或实验室确认。"
    ),
    "second_hand_fit": (
        "二手是否适用取决于当前课程要求、版本、附件、设备状态和权属；"
        "没有证据时不能虚构账号或激活码要求。"
    ),
    "compatibility_check": (
        "应结合问题对象核对当前教师要求；教材重点核对书名、作者、版次和目录，"
        "设备或软件重点核对型号、接口、配件和环境，不把往期资料当成本学期强制要求。"
    ),
    "reuse_or_resale": (
        "后续复用或转卖取决于下一届课程要求、版本适用性、设备状态、个人权属和"
        "实际附件，不能保证下一届继续采用。"
    ),
}


def classify_course_truth(intent: str, query: str) -> dict[str, Any]:
    """Return the current product-scope truth without Case-ID exceptions."""
    has_transaction = any(marker in query for marker in COURSE_TRANSACTION_MARKERS)
    has_physical_fit_object = any(
        marker in query for marker in COURSE_PHYSICAL_FIT_OBJECTS
    )
    teaching_support_only = (
        intent == "school_provided" and not has_transaction
    )
    software_environment_only = (
        intent == "compatibility_check"
        and not has_transaction
        and not has_physical_fit_object
    )
    if teaching_support_only or software_environment_only:
        return {
            "expectedAction": "out_of_scope",
            "expectedRoute": "out_of_scope",
            "expectedKnowledgeState": "not_applicable",
            "expectedFacts": list(COURSE_OUT_OF_SCOPE_EXPECTED_FACTS),
            "qrels": [],
            "allowedSourceTypes": [],
            "preferredSourceType": None,
            "forbiddenDocumentPrefixes": ["GUIDE:", "POST:"],
            "currentRuntimeReason": (
                "问题仅涉及课程教学支持、软件/算力环境或器材供给，"
                "没有明确商品决策；按当前校园二手交易范围应终止。"
            ),
            "scopeClass": "teaching_support_out_of_scope",
        }
    return {
        "expectedAction": "retrieve",
        "expectedRoute": "retrieve",
        "expectedKnowledgeState": "unknown_after_search",
        "expectedFacts": [COURSE_EXPECTED_FACTS[intent]],
        "currentRuntimeReason": (
            "课程信息直接服务于购买、自费、二手、转卖或具体实物适配决策；"
            "应检索现有资料，并在不能确认本学期课程专属事实时明确未知。"
        ),
        "scopeClass": "commodity_decision_retrieve",
    }


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_jsonl(rows: list[dict[str, Any]]) -> str:
    return "".join(
        json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
        for row in rows
    )


def parse_all_review(path: Path) -> list[dict[str, str]]:
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(
        r"^##\s+(\d+)\.\s+([^\r\n]+)\r?\n\r?\n"
        r"- domain：`([^`]+)`\r?\n"
        r"- split：`([^`]+)`\r?\n"
        r"- query：([^\r\n]+)",
        re.MULTILINE,
    )
    rows = [
        {"number": number, "caseId": case_id.strip(), "domain": domain,
         "split": split, "query": query.strip()}
        for number, case_id, domain, split, query in pattern.findall(text)
    ]
    if len(rows) != 200 or len({row["caseId"] for row in rows}) != 200:
        raise ValueError(f"manual all-questions review must contain 200 unique cases: {len(rows)}")
    if [int(row["number"]) for row in rows] != list(range(1, 201)):
        raise ValueError("manual all-questions numbering is not contiguous")
    return rows


def parse_course_review(path: Path) -> list[dict[str, Any]]:
    text = path.read_text(encoding="utf-8")
    block_pattern = re.compile(
        r"^###\s+(\d+)\.\s+`([^`]+)`\r?\n(.*?)(?=^###\s+\d+\.|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    rows: list[dict[str, Any]] = []
    for number, case_id, block in block_pattern.findall(text):
        course = re.search(
            r"^- 课程：(.+?)\s*/\s*(.+?)（`([^`]+)`(?:，[^）]*)?）\s*$",
            block,
            re.MULTILINE,
        )
        knowledge = re.search(r"^- 当前知识：(.+?)\s*$", block, re.MULTILINE)
        intent = re.search(r"^- 意图：`([^`]+)`\s*$", block, re.MULTILINE)
        question = re.search(r"^- 新问题：(.+?)\s*$", block, re.MULTILINE)
        if not all((course, knowledge, intent, question)):
            raise ValueError(f"incomplete manual course block: {case_id}")
        assert course and knowledge and intent and question
        year_match = re.match(r"(\d{4})级", question.group(1).strip())
        if year_match is None:
            raise ValueError(f"manual course question has no entry year: {case_id}")
        rows.append({
            "number": int(number),
            "caseId": case_id,
            "major": course.group(1).strip(),
            "courseName": course.group(2).strip(),
            "courseCode": course.group(3).strip(),
            "currentKnowledge": knowledge.group(1).strip(),
            "intent": intent.group(1).strip(),
            "query": question.group(1).strip(),
            "entryYear": int(year_match.group(1)),
        })
    if len(rows) != 70 or len({row["caseId"] for row in rows}) != 70:
        raise ValueError(f"manual course review must contain 70 unique cases: {len(rows)}")
    if [row["number"] for row in rows] != list(range(1, 71)):
        raise ValueError("manual course numbering is not contiguous")
    return rows


def materialize() -> tuple[list[dict[str, Any]], dict[str, Any]]:
    source_rows = read_jsonl(SOURCE)
    source_by_id = {row["caseId"]: row for row in source_rows}
    all_review = parse_all_review(ALL_REVIEW)
    manual_courses = parse_course_review(COURSE_REVIEW)
    accepted_smoke = {row["caseId"]: row for row in read_jsonl(ACCEPTED_SMOKE)}
    all_review_by_id = {row["caseId"]: row for row in all_review}
    if {row["caseId"] for row in manual_courses} != {
        row["caseId"] for row in all_review if row["domain"] == "course"
    }:
        raise ValueError("the two approved review files disagree on course Case IDs")
    for row in manual_courses:
        if all_review_by_id[row["caseId"]]["query"] != row["query"]:
            raise ValueError(f"the two approved review files disagree on query: {row['caseId']}")

    old_courses = sorted(
        (row for row in source_rows if row["domain"] == "course"),
        key=lambda row: row["caseId"],
    )
    by_year: dict[int, list[dict[str, Any]]] = defaultdict(list)
    for row in old_courses:
        by_year[int(row["provenance"]["entryYear"])].append(row)
    counters: Counter[int] = Counter()
    rewritten: dict[str, dict[str, Any]] = {}
    source_bindings: dict[str, str] = {}
    for manual in manual_courses:
        year = manual["entryYear"]
        candidates = by_year[year]
        if not candidates:
            raise ValueError(f"no audited source courses for {year}")
        source = candidates[counters[year] % len(candidates)]
        counters[year] += 1
        provenance = source["provenance"]
        if provenance.get("courseCode") != manual["courseCode"]:
            raise ValueError(
                f"manual/source course mismatch {manual['caseId']}: "
                f"{manual['courseCode']} != {provenance.get('courseCode')}"
            )
        if provenance.get("major") != manual["major"]:
            raise ValueError(
                f"manual/source major mismatch {manual['caseId']}: "
                f"{manual['major']} != {provenance.get('major')}"
            )
        intent = manual["intent"]
        if intent not in INTENTS:
            raise ValueError(f"unsupported reviewed intent: {manual['caseId']}:{intent}")
        quality = question_quality(manual["query"], expected_intent=intent)
        if not quality["passed"] or quality["naturalLanguageProblem"]:
            raise ValueError(f"reviewed question failed quality gate: {manual['caseId']}:{quality}")
        row = deepcopy(source)
        row.update({
            "caseId": manual["caseId"],
            "version": DATASET_VERSION,
            "split": all_review_by_id[manual["caseId"]]["split"],
            "query": manual["query"],
            "queryType": "course_decision",
            "questionIntent": intent,
            "questionQuality": quality,
            "naturalLanguageProblem": False,
            "expectedAction": "retrieve",
            "expectedRoute": "retrieve",
            "sourceCaseId": source["caseId"],
            "sourceEntryYear": provenance["entryYear"],
            "proposedEntryYear": year,
            "entryYear": year,
            "major": manual["major"],
            "courseName": manual["courseName"],
            "courseCode": manual["courseCode"],
            "manualKnowledgeSummary": manual["currentKnowledge"],
        })
        row_provenance = deepcopy(provenance)
        row_provenance.update({
            "entryYear": year,
            "decisionDimension": intent,
            "questionIntent": intent,
            "questionQuality": quality,
            "reassignedFromCaseId": source["caseId"],
            "sourceEntryYear": provenance["entryYear"],
            "manualQuestionSource": "course_70_manual_review.md",
            "manualKnowledgeSummary": manual["currentKnowledge"],
        })
        row["provenance"] = row_provenance
        scope_truth = classify_course_truth(intent, manual["query"])
        row.update(scope_truth)
        row_provenance["scopeClass"] = scope_truth["scopeClass"]
        rewritten[manual["caseId"]] = row
        source_bindings[manual["caseId"]] = source["caseId"]

    rows: list[dict[str, Any]] = []
    for reviewed in all_review:
        case_id = reviewed["caseId"]
        if case_id in rewritten:
            row = rewritten[case_id]
        else:
            if case_id not in source_by_id:
                raise ValueError(f"reviewed Case ID missing from Golden v1.1: {case_id}")
            row = apply_current_runtime_truth(source_by_id[case_id])
            if row["query"] != reviewed["query"]:
                raise ValueError(f"non-course reviewed query changed unexpectedly: {case_id}")
            row = deepcopy(row)
            row["version"] = DATASET_VERSION
        if row["domain"] != reviewed["domain"] or row["split"] != reviewed["split"]:
            raise ValueError(f"review metadata drift: {case_id}")
        rows.append(row)

    domains = Counter(row["domain"] for row in rows)
    years = Counter(row["proposedEntryYear"] for row in rows if row["domain"] == "course")
    intents = Counter(row["questionIntent"] for row in rows if row["domain"] == "course")
    routes = Counter(row["expectedRoute"] for row in rows)
    knowledge_states = Counter(row["expectedKnowledgeState"] for row in rows)
    course_routes = Counter(
        row["expectedRoute"] for row in rows if row["domain"] == "course"
    )
    course_scope_classes = Counter(
        row["scopeClass"] for row in rows if row["domain"] == "course"
    )
    if len(rows) != 200 or len({row["caseId"] for row in rows}) != 200:
        raise ValueError("materialized dataset must have 200 unique cases")
    if domains != Counter({"boundary": 20, "campus": 20, "course": 70, "platform": 40, "post": 50}):
        raise ValueError(f"domain distribution drift: {domains}")
    if years != YEAR_COUNTS:
        raise ValueError(f"course year distribution drift: {years}")
    if intents != Counter({intent: 10 for intent in INTENTS}):
        raise ValueError(f"course intent distribution drift: {intents}")

    compare_fields = ("query", "sourceCaseId", "questionIntent")
    for case_id, accepted in accepted_smoke.items():
        actual = rewritten[case_id]
        for field in compare_fields:
            if actual.get(field) != accepted.get(field):
                raise ValueError(
                    f"accepted course smoke question/binding drift: {case_id}.{field}: "
                    f"{actual.get(field)!r} != {accepted.get(field)!r}"
                )

    return rows, {
        "domainCounts": dict(sorted(domains.items())),
        "courseYearCounts": {str(key): value for key, value in sorted(years.items(), reverse=True)},
        "courseIntentCounts": dict(sorted(intents.items())),
        "routeCounts": dict(sorted(routes.items())),
        "knowledgeStateCounts": dict(sorted(knowledge_states.items())),
        "courseRouteCounts": dict(sorted(course_routes.items())),
        "courseScopeClassCounts": dict(sorted(course_scope_classes.items())),
        "acceptedSmokeQuestionBindingsMatched": len(accepted_smoke),
        "sourceBindings": source_bindings,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()
    output_dir = args.output_dir.resolve()
    dataset_path = output_dir / DATASET_FILENAME
    manifest_path = output_dir / MANIFEST_FILENAME
    rows, validation = materialize()
    output_dir.mkdir(parents=True, exist_ok=True)
    dataset_path.write_text(canonical_jsonl(rows), encoding="utf-8", newline="\n")
    source_manifest = json.loads(SOURCE_MANIFEST.read_text(encoding="utf-8"))
    manifest = {
        "status": "REVIEWED_INPUT_FROZEN",
        "generatedAt": "2026-08-29",
        "datasetName": "sharing-market-golden-v1.2.1-reviewed",
        "datasetVersion": DATASET_VERSION,
        "supersedesDatasetVersion": "golden-v1.2-reviewed-20260829",
        "caseCount": len(rows),
        "caseSha256": sha256(dataset_path),
        "indexBuildIdAtFreeze": source_manifest["indexBuildIdAtFreeze"],
        "authoritativeQuestionSources": {
            str(ALL_REVIEW): sha256(ALL_REVIEW),
            str(COURSE_REVIEW): sha256(COURSE_REVIEW),
        },
        "sourceDataset": {str(SOURCE): sha256(SOURCE)},
        "acceptedSmokeReference": {str(ACCEPTED_SMOKE): sha256(ACCEPTED_SMOKE)},
        "courseTruthContract": {
            "classifier": "intent-and-query-scope-v1",
            "factsByIntent": COURSE_EXPECTED_FACTS,
            "outOfScopeFacts": COURSE_OUT_OF_SCOPE_EXPECTED_FACTS,
            "courseRouteCounts": validation["courseRouteCounts"],
            "courseScopeClassCounts": validation["courseScopeClassCounts"],
            "caseIdSpecificScopeRules": False,
            "legacyCaseIdMappingRead": False,
        },
        "obsoleteDraftJsonlRead": False,
        "formalGoldenV11Modified": False,
        "validation": validation,
    }
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
