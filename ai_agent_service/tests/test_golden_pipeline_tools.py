from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, relative_path: str):
    path = ROOT / relative_path
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pipeline = load_module(
    "golden_pipeline_test",
    "ai_agent_service/evaluation/tools/run_golden_pipeline.py",
)
comparison = load_module(
    "golden_comparison_test",
    "ai_agent_service/evaluation/tools/compare_golden_runs.py",
)


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.write_text(
        "".join(json.dumps(row, sort_keys=True) + "\n" for row in rows),
        encoding="utf-8",
    )


def test_select_cases_preserves_requested_order() -> None:
    rows = [{"caseId": "a"}, {"caseId": "b"}, {"caseId": "c"}]

    assert pipeline.select_cases(rows, ["c", "a"], None) == [rows[2], rows[0]]
    assert pipeline.select_cases(rows, [], 2) == rows[:2]

    with pytest.raises(ValueError, match="unknown --case-id"):
        pipeline.select_cases(rows, ["missing"], None)


def test_prepare_inputs_keeps_source_manifest_and_binds_subset(tmp_path: Path) -> None:
    dataset = tmp_path / "source.jsonl"
    manifest = tmp_path / "source-manifest.json"
    rows = [
        {"caseId": "a", "query": "A"},
        {"caseId": "b", "query": "B"},
        {"caseId": "c", "query": "C"},
    ]
    write_jsonl(dataset, rows)
    manifest.write_text(
        json.dumps({
            "datasetVersion": "test-v1",
            "indexBuildIdAtFreeze": "build-1",
            "caseCount": 3,
        }),
        encoding="utf-8",
    )

    selected_path, selected_manifest_path, selected, prepared = pipeline.prepare_inputs(
        dataset,
        manifest,
        tmp_path / "run",
        ["b", "a"],
        None,
        "build-current",
    )

    assert [row["caseId"] for row in selected] == ["b", "a"]
    assert pipeline.read_jsonl(selected_path) == [rows[1], rows[0]]
    assert prepared["datasetVersion"] == "test-v1"
    assert prepared["indexBuildIdAtFreeze"] == "build-current"
    assert prepared["pipelineSelection"]["sourceIndexBuildIdAtFreeze"] == "build-1"
    assert prepared["pipelineSelection"]["indexBuildOverride"] == "build-current"
    assert prepared["caseCount"] == 2
    assert prepared["caseSha256"] == hashlib.sha256(selected_path.read_bytes()).hexdigest()
    assert json.loads(selected_manifest_path.read_text(encoding="utf-8")) == prepared


def test_command_plan_runs_existing_implementations_in_order(tmp_path: Path) -> None:
    commands, artifacts = pipeline.command_plan(
        "python",
        tmp_path / "run",
        tmp_path / "dataset.jsonl",
        tmp_path / "manifest.json",
        "build-1",
        2,
        True,
        5,
    )

    assert list(commands) == ["router", "retrieval", "generation", "judge", "final"]
    assert commands["router"][1] == str(pipeline.ROUTER_SCRIPT)
    assert "--require-llm" in commands["router"]
    assert commands["retrieval"][1] == str(pipeline.RETRIEVAL_SCRIPT)
    assert commands["generation"][1] == str(pipeline.GENERATION_SCRIPT)
    assert commands["judge"][1] == str(pipeline.JUDGE_SCRIPT)
    assert commands["final"][1] == str(pipeline.FINAL_SCRIPT)
    assert commands["final"][-2:] == ["--expected-case-count", "5"]
    assert artifacts["retrieval"].name == "pipeline_retrieval_build-1.jsonl"


def test_compare_stage_reports_nondeterministic_answer_difference(tmp_path: Path) -> None:
    baseline = tmp_path / "baseline.jsonl"
    candidate = tmp_path / "candidate.jsonl"
    common = {
        "caseId": "case-1",
        "status": "SUCCESS",
        "response": {
            "answer": "baseline",
            "output": {
                "intent": "RISK_CHECK",
                "knowledgeChunkIds": ["chunk-1"],
                "courseRelationIds": [],
            },
        },
        "sourceDocumentIds": ["doc-1"],
        "toolNames": [],
    }
    changed = json.loads(json.dumps(common))
    changed["response"]["answer"] = "candidate"
    write_jsonl(baseline, [common])
    write_jsonl(candidate, [changed])

    report = comparison.compare_stage("generation", baseline, candidate)

    assert report["caseSetEqual"] is True
    assert report["equivalentCaseCount"] == 0
    assert report["contractEquivalentCaseCount"] == 1
    assert report["cases"][0]["differingFields"] == ["answer"]
