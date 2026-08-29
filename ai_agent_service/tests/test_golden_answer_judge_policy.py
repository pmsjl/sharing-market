from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/run_golden_v1_1_answer_judge.py"
SPEC = importlib.util.spec_from_file_location("golden_answer_judge_policy_test", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
judge = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = judge
SPEC.loader.exec_module(judge)


def item(**overrides):
    value = {
        "answerRelevance": 4,
        "factCoverage": 4,
        "groundedness": 4,
        "actionAppropriateness": 4,
        "citationAlignment": 0,
        "knowledgeStateCorrect": True,
        "criticalError": False,
        "expectationIssue": False,
        "evidenceMisrepresentation": False,
        "contradictsEvidence": False,
        "expectedFactAssessments": [],
        "criticalErrorTypes": [],
    }
    value.update(overrides)
    return value


def test_citation_alignment_is_not_a_hard_gate():
    assert judge.calculate_holistic_pass(item(citationAlignment=0)) is True


def test_one_noncritical_core_dimension_at_two_can_pass():
    assert judge.calculate_holistic_pass(item(factCoverage=2)) is True


def test_two_core_dimensions_at_two_do_not_pass():
    assert judge.calculate_holistic_pass(item(factCoverage=2, groundedness=2)) is False


def test_critical_error_always_fails():
    assert judge.calculate_holistic_pass(item(criticalError=True)) is False


def test_wrong_knowledge_state_fails_without_label_issue_override():
    assert judge.calculate_holistic_pass(item(knowledgeStateCorrect=False)) is False


def test_dataset_generation_binding_rejects_changed_truth():
    truths = {
        "case-1": {
            "caseId": "case-1", "query": "new", "expectedRoute": "retrieve",
            "expectedKnowledgeState": "answerable", "expectedFacts": ["new"],
            "qrels": [],
        }
    }
    generated = [{
        "caseId": "case-1", "query": "old", "expectedRoute": "retrieve",
        "expectedKnowledgeState": "answerable", "expectedFacts": ["old"],
        "qrels": [],
    }]
    with pytest.raises(ValueError, match="dataset/generation truth mismatch"):
        judge.validate_dataset_generation_binding(truths, generated)


def test_expectation_issue_does_not_gate_on_obsolete_fact_coverage():
    assert judge.calculate_holistic_pass(
        item(expectationIssue=True, factCoverage=0)
    ) is True


def test_evidence_misrepresentation_always_fails():
    assert judge.calculate_holistic_pass(
        item(evidenceMisrepresentation=True, groundedness=2)
    ) is False


def test_label_issue_uses_audited_expected_knowledge_state():
    value = item(
        expectationIssue=True,
        observedKnowledgeState="not_applicable",
        auditedExpectedKnowledgeState="not_applicable",
    )
    assert judge.calculate_knowledge_state_correct(value, "answerable") is True


def test_without_label_issue_uses_dataset_expected_knowledge_state():
    value = item(
        expectationIssue=False,
        observedKnowledgeState="not_applicable",
        auditedExpectedKnowledgeState="not_applicable",
    )
    assert judge.calculate_knowledge_state_correct(value, "answerable") is False


def test_qrel_details_cannot_create_missing_core_error():
    value = item(
        criticalError=True,
        criticalErrorTypes=["missing_core_answer"],
        expectedFactAssessments=[{
            "index": 0, "covered": True, "criticalIfMissing": True,
            "reason": "explicit expected fact is covered",
        }],
    )
    judge.normalize_critical_errors(value, 1)
    assert value["criticalError"] is False
    assert value["factCoverage"] >= 3


def test_real_missing_expected_fact_remains_critical():
    value = item(
        criticalError=True,
        criticalErrorTypes=["missing_core_answer"],
        expectedFactAssessments=[{
            "index": 0, "covered": False, "criticalIfMissing": True,
            "reason": "core expected fact is absent",
        }],
    )
    judge.normalize_critical_errors(value, 1)
    assert value["criticalError"] is True


def test_more_cautious_wording_is_not_a_fact_error_without_contradiction():
    value = item(
        criticalError=True,
        criticalErrorTypes=["critical_fact_error"],
        expectedFactAssessments=[],
        contradictsEvidence=False,
        evidenceMisrepresentation=False,
    )
    judge.normalize_critical_errors(value, 0)
    assert value["criticalError"] is False
