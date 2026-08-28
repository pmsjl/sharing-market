"""Regression checks for the user-reviewed Golden v1.2 input."""
from __future__ import annotations

import importlib.util
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/materialize_golden_v1_2_reviewed.py"
sys.path.insert(0, str(ROOT / "tools"))


def _load_module():
    spec = importlib.util.spec_from_file_location("golden_v1_2_materializer", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_reviewed_200_uses_one_course_truth_contract_without_legacy_id_rules():
    module = _load_module()
    rows, validation = module.materialize()
    assert len(rows) == 200
    assert len({row["caseId"] for row in rows}) == 200

    courses = [row for row in rows if row["domain"] == "course"]
    assert len(courses) == 70
    assert {row["expectedRoute"] for row in courses} == {"retrieve"}
    assert {row["expectedKnowledgeState"] for row in courses} == {
        "unknown_after_search"
    }
    assert all(
        row["expectedFacts"] == [module.COURSE_EXPECTED_FACTS[row["questionIntent"]]]
        for row in courses
    )
    assert all(
        row["courseCode"] == row["provenance"]["courseCode"]
        and row["entryYear"] == row["provenance"]["entryYear"]
        for row in courses
    )
    assert Counter(row["entryYear"] for row in courses) == module.YEAR_COUNTS
    assert Counter(row["questionIntent"] for row in courses) == Counter(
        {intent: 10 for intent in module.INTENTS}
    )
    assert validation["acceptedSmokeQuestionBindingsMatched"] == 35

    for case_id in (
        "course-unknown_after_search-038",
        "course-unknown_after_search-047",
    ):
        row = next(item for item in courses if item["caseId"] == case_id)
        assert row["expectedRoute"] == "retrieve"
        assert row["expectedKnowledgeState"] == "unknown_after_search"
