from __future__ import annotations

import importlib.util
from pathlib import Path


def _load_retrieval_eval_module():
    script = (
        Path(__file__).resolve().parents[2]
        / "ai_agent_service" / "evaluation" / "tools"
        / "run_golden_v1_1_retrieval_eval.py"
    )
    spec = importlib.util.spec_from_file_location("golden_retrieval_eval", script)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_candidate_exposure_is_diagnostic_and_supports_frozen_legacy_rows() -> None:
    module = _load_retrieval_eval_module()
    rows = [
        {
            "domain": "course",
            "forbiddenDocumentPrefixes": ["GUIDE:course-repo-"],
            "ragDiagnostics": {"courseMatchMode": "constraints"},
            "forbiddenDocumentCandidateExposure": True,
        },
        {
            "domain": "course",
            "forbiddenDocumentPrefixes": ["GUIDE:course-repo-"],
            "ragDiagnostics": {"courseMatchMode": "none"},
            "forbiddenDocumentCandidateExposure": False,
        },
        {
            "domain": "boundary",
            "forbiddenDocumentPrefixes": ["GUIDE:", "POST:"],
            # Frozen result compatibility: old files retain the legacy field.
            "forbiddenDocumentViolation": True,
        },
    ]

    diagnostics = module.candidate_exposure_diagnostics(rows)

    assert diagnostics["metricRole"] == "diagnostic_only"
    assert diagnostics["countsAsFailure"] is False
    assert diagnostics["allConfiguredPrefixes"] == {
        "evaluatedCaseCount": 3,
        "exposedCaseCount": 2,
        "exposureRate": 0.666667,
    }
    assert diagnostics["courseRepoPrefixConfigured"] == {
        "evaluatedCaseCount": 2,
        "exposedCaseCount": 1,
        "exposureRate": 0.5,
    }
    assert diagnostics["constraintsLaneCrossCourseCandidates"]["evaluatedCaseCount"] == 1
    assert diagnostics["constraintsLaneCrossCourseCandidates"]["exposedCaseCount"] == 1
    assert diagnostics["otherCandidateExposure"] == {
        "evaluatedCaseCount": 2,
        "exposedCaseCount": 1,
        "exposureRate": 0.5,
    }
    assert diagnostics["answerSourceOrClaimMisuseMustBeAuditedSeparately"] is True


def test_aggregate_keeps_candidate_exposure_out_of_overall_quality_metrics() -> None:
    module = _load_retrieval_eval_module()
    row = {
        "domain": "course",
        "split": "test",
        "expectedRoute": "retrieve",
        "expectedKnowledgeState": "unknown_after_search",
        "rankingEligible": True,
        "recallAt1": False,
        "recallAt3": True,
        "recallAt5": True,
        "mrr": 0.5,
        "ndcgAt5": 0.75,
        "requiredQrelHit": True,
        "supportingChunkRecall": 1.0,
        "preferredSourceTop1": False,
        "courseEvidenceStateCorrect": True,
        "courseEvidenceStateCompatible": True,
        "retrievedDocumentIds": ["GUIDE:course-repo-neighbor"],
        "retrievalMs": 1.25,
        "ragRouteCorrect": True,
        "forbiddenDocumentPrefixes": ["GUIDE:course-repo-"],
        "ragDiagnostics": {"courseMatchMode": "constraints"},
        "forbiddenDocumentCandidateExposure": True,
    }

    report = module.aggregate([row], {"settings": {}})

    assert "forbiddenViolationRate" not in report["overall"]
    assert "candidateExposureRate" not in report["overall"]
    assert report["candidateExposureDiagnostics"]["countsAsFailure"] is False
    assert report["candidateExposureDiagnostics"]["allConfiguredPrefixes"]["exposedCaseCount"] == 1
