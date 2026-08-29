"""Regression checks for the user-reviewed Golden v1.2.1 input."""
from __future__ import annotations

import importlib.util
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/materialize_golden_v1_2_reviewed.py"
sys.path.insert(0, str(ROOT / "tools"))

COURSE_OUT_OF_SCOPE_IDS = {
    "course-material_mention-005",
    "course-material_mention-015",
    "course-material_mention-025",
    "course-material_mention-033",
    "course-material_mention-034",
    "course-material_mention-045",
    "course-material_mention-052",
    "course-material_mention-054",
    "course-unknown_after_search-020",
    "course-unknown_after_search-047",
    "course-unknown_after_search-048",
    "course-unknown_after_search-066",
}
RETAINED_COURSE_RETRIEVE_IDS = {
    "course-unknown_after_search-007",
    "course-unknown_after_search-009",
    "course-unknown_after_search-060",
}


def _load_module():
    spec = importlib.util.spec_from_file_location("golden_v1_2_materializer", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_reviewed_200_applies_current_scope_without_case_id_classifier_rules():
    module = _load_module()
    rows, validation = module.materialize()
    assert len(rows) == 200
    assert len({row["caseId"] for row in rows}) == 200
    assert module.DATASET_VERSION == "golden-v1.2.1-reviewed-20260829"
    assert module.DATASET_FILENAME == "golden_v1_2_1_reviewed_200.jsonl"

    assert Counter(row["expectedRoute"] for row in rows) == Counter({
        "retrieve": 176,
        "out_of_scope": 18,
        "clarify": 4,
        "skip_rag": 2,
    })
    assert Counter(row["expectedKnowledgeState"] for row in rows) == Counter({
        "answerable": 116,
        "unknown_after_search": 60,
        "not_applicable": 24,
    })

    courses = [row for row in rows if row["domain"] == "course"]
    assert len(courses) == 70
    assert Counter(row["expectedRoute"] for row in courses) == Counter({
        "retrieve": 58,
        "out_of_scope": 12,
    })
    assert Counter(row["scopeClass"] for row in courses) == Counter({
        "commodity_decision_retrieve": 58,
        "teaching_support_out_of_scope": 12,
    })
    assert {
        row["caseId"] for row in courses if row["expectedRoute"] == "out_of_scope"
    } == COURSE_OUT_OF_SCOPE_IDS

    for row in courses:
        assert row["courseCode"] == row["provenance"]["courseCode"]
        assert row["entryYear"] == row["provenance"]["entryYear"]
        if row["expectedRoute"] == "out_of_scope":
            assert row["expectedAction"] == "out_of_scope"
            assert row["expectedKnowledgeState"] == "not_applicable"
            assert row["expectedFacts"] == module.COURSE_OUT_OF_SCOPE_EXPECTED_FACTS
            assert row["qrels"] == []
            assert row["allowedSourceTypes"] == []
            assert row["preferredSourceType"] is None
            assert row["forbiddenDocumentPrefixes"] == ["GUIDE:", "POST:"]
        else:
            assert row["expectedAction"] == "retrieve"
            assert row["expectedKnowledgeState"] == "unknown_after_search"
            assert row["expectedFacts"] == [
                module.COURSE_EXPECTED_FACTS[row["questionIntent"]]
            ]

    by_id = {row["caseId"]: row for row in rows}
    for case_id in RETAINED_COURSE_RETRIEVE_IDS:
        assert by_id[case_id]["expectedRoute"] == "retrieve"
        assert by_id[case_id]["expectedKnowledgeState"] == "unknown_after_search"

    campus_purchase = by_id["campus-campus-lifecycle-new-student-01"]
    assert campus_purchase["query"] == (
        "新生报到前，哪些宿舍用品应该先确认学校是否提供，再决定是否购买？"
    )
    assert campus_purchase["expectedRoute"] == "retrieve"
    assert campus_purchase["expectedKnowledgeState"] == "answerable"
    assert [qrel["documentId"] for qrel in campus_purchase["qrels"]] == [
        "GUIDE:campus-dorm-new-student-supplies"
    ]

    campus_registration = by_id["campus-campus-lifecycle-new-student-02"]
    assert campus_registration["expectedRoute"] == "out_of_scope"
    assert campus_registration["expectedKnowledgeState"] == "not_applicable"
    assert campus_registration["qrels"] == []
    assert campus_registration["allowedSourceTypes"] == []
    assert campus_registration["preferredSourceType"] is None
    assert campus_registration["forbiddenDocumentPrefixes"] == ["GUIDE:", "POST:"]

    assert Counter(row["entryYear"] for row in courses) == module.YEAR_COUNTS
    assert Counter(row["questionIntent"] for row in courses) == Counter(
        {intent: 10 for intent in module.INTENTS}
    )
    assert validation["acceptedSmokeQuestionBindingsMatched"] == 35
    assert validation["routeCounts"] == {
        "clarify": 4,
        "out_of_scope": 18,
        "retrieve": 176,
        "skip_rag": 2,
    }


def test_course_scope_classifier_uses_intent_and_query_semantics():
    module = _load_module()

    for intent, query in (
        ("school_provided", "这门课学校是否提供服务器和实验环境？"),
        ("school_provided", "开发板和传感器是实验室提供还是学生自带？"),
        ("compatibility_check", "MATLAB版本怎样与机房和老师代码保持一致？"),
    ):
        result = module.classify_course_truth(intent, query)
        assert result["expectedRoute"] == "out_of_scope"
        assert result["expectedKnowledgeState"] == "not_applicable"

    for intent, query in (
        ("school_provided", "学校不提供时需要自己买开发板吗？"),
        ("school_provided", "学校没有集群时是否需要升级电脑？"),
        ("compatibility_check", "准备开发板时怎样确认型号和接口适配课程项目？"),
        ("compatibility_check", "买二手教材前怎样核对版次和课程要求？"),
    ):
        result = module.classify_course_truth(intent, query)
        assert result["expectedRoute"] == "retrieve"
        assert result["expectedKnowledgeState"] == "unknown_after_search"
