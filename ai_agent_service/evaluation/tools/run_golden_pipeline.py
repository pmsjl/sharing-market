"""Run the existing Golden evaluation stages through one reproducible entry point.

This module deliberately contains no Router, retrieval, generation, or Judge
logic.  It prepares a selected dataset and invokes the established evaluators
without importing or modifying their runtime behavior.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
AGENT_ROOT = REPO_ROOT / "ai_agent_service"
EVALUATION_ROOT = AGENT_ROOT / "evaluation"
EVAL_TOOLS = EVALUATION_ROOT / "tools"

DEFAULT_DATASET = EVALUATION_ROOT / "dataset" / "golden_v1_2_1_reviewed_200.jsonl"
DEFAULT_MANIFEST = EVALUATION_ROOT / "dataset" / "golden_v1_2_1_reviewed_200_manifest.json"

ROUTER_SCRIPT = EVAL_TOOLS / "run_golden_v1_1_router_eval.py"
RETRIEVAL_SCRIPT = EVAL_TOOLS / "run_golden_v1_1_retrieval_eval.py"
GENERATION_SCRIPT = EVAL_TOOLS / "run_golden_v1_1_answer_generation.py"
JUDGE_SCRIPT = EVAL_TOOLS / "run_golden_v1_1_answer_judge.py"
FINAL_SCRIPT = EVAL_TOOLS / "build_golden_v1_2_single_v2_final_results.py"

SCRIPT_PATHS = {
    "router": ROUTER_SCRIPT,
    "retrieval": RETRIEVAL_SCRIPT,
    "generation": GENERATION_SCRIPT,
    "judge": JUDGE_SCRIPT,
    "final": FINAL_SCRIPT,
}
STAGE_ORDER = ("prepare", "router", "retrieval", "generation", "judge", "final")
RUN_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "".join(
            json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n"
            for row in rows
        ),
        encoding="utf-8",
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def select_cases(
    rows: list[dict[str, Any]],
    case_ids: list[str],
    limit: int | None,
) -> list[dict[str, Any]]:
    if case_ids:
        by_id = {str(row["caseId"]): row for row in rows}
        duplicate_ids = sorted(
            case_id for case_id in set(case_ids) if case_ids.count(case_id) > 1
        )
        if duplicate_ids:
            raise ValueError(f"duplicate --case-id values: {duplicate_ids}")
        missing = [case_id for case_id in case_ids if case_id not in by_id]
        if missing:
            raise ValueError(f"unknown --case-id values: {missing}")
        return [by_id[case_id] for case_id in case_ids]
    if limit is not None:
        if limit <= 0:
            raise ValueError("--limit must be positive")
        return rows[:limit]
    return rows


def prepare_inputs(
    dataset: Path,
    manifest: Path,
    run_root: Path,
    case_ids: list[str],
    limit: int | None,
    index_build_id: str | None = None,
) -> tuple[Path, Path, list[dict[str, Any]], dict[str, Any]]:
    rows = select_cases(read_jsonl(dataset), case_ids, limit)
    if not rows:
        raise ValueError("selected Golden dataset is empty")

    source_manifest = json.loads(manifest.read_text(encoding="utf-8"))
    selected_dataset = run_root / "input" / "selected_cases.jsonl"
    selected_manifest = run_root / "input" / "selected_manifest.json"
    write_jsonl(selected_dataset, rows)

    prepared_manifest = dict(source_manifest)
    prepared_manifest.update({
        "caseCount": len(rows),
        "caseSha256": sha256(selected_dataset),
        "pipelineSelection": {
            "sourceDataset": str(dataset),
            "sourceDatasetSha256": sha256(dataset),
            "sourceManifest": str(manifest),
            "sourceManifestSha256": sha256(manifest),
            "caseIds": [str(row["caseId"]) for row in rows],
        },
    })
    if index_build_id is not None:
        prepared_manifest["pipelineSelection"]["sourceIndexBuildIdAtFreeze"] = (
            source_manifest.get("indexBuildIdAtFreeze")
        )
        prepared_manifest["pipelineSelection"]["indexBuildOverride"] = index_build_id
        prepared_manifest["indexBuildIdAtFreeze"] = index_build_id
    write_json(selected_manifest, prepared_manifest)
    return selected_dataset, selected_manifest, rows, prepared_manifest


def command_plan(
    python: str,
    run_root: Path,
    dataset: Path,
    manifest: Path,
    build_id: str,
    router_concurrency: int,
    require_llm_router: bool,
    case_count: int,
) -> tuple[dict[str, list[str]], dict[str, Path]]:
    results = run_root / "results"
    reports = run_root / "reports"
    artifacts = {
        "router": results / "pipeline_router.jsonl",
        "routerReport": reports / "pipeline_router.json",
        "retrieval": results / f"pipeline_retrieval_{build_id}.jsonl",
        "retrievalReport": reports / f"pipeline_retrieval_{build_id}.json",
        "generation": results / "pipeline_answer_generation.jsonl",
        "generationReport": reports / "pipeline_answer_generation.json",
        "judgment": results / "pipeline_answer_judgments.jsonl",
        "judgmentReport": reports / "pipeline_answer_judgment.json",
        "judgmentBadcases": reports / "pipeline_answer_badcases.json",
        "final": results / "pipeline_final_results.jsonl",
    }
    router = [
        python,
        str(ROUTER_SCRIPT),
        "--dataset",
        str(dataset),
        "--run-id",
        "pipeline_router",
        "--concurrency",
        str(max(1, router_concurrency)),
    ]
    if require_llm_router:
        router.append("--require-llm")

    commands = {
        "router": router,
        "retrieval": [
            python,
            str(RETRIEVAL_SCRIPT),
            "--dataset",
            str(dataset),
            "--manifest",
            str(manifest),
            "--router-results",
            str(artifacts["router"]),
            "--run-id",
            "pipeline_retrieval",
        ],
        "generation": [
            python,
            str(GENERATION_SCRIPT),
            "--dataset",
            str(dataset),
            "--manifest",
            str(manifest),
            "--router-results",
            str(artifacts["router"]),
            "--output",
            str(artifacts["generation"]),
            "--summary",
            str(artifacts["generationReport"]),
        ],
        "judge": [
            python,
            str(JUDGE_SCRIPT),
            "--round",
            "round2",
            "--dataset",
            str(dataset),
            "--generation",
            str(artifacts["generation"]),
            "--output",
            str(artifacts["judgment"]),
            "--report",
            str(artifacts["judgmentReport"]),
            "--badcases",
            str(artifacts["judgmentBadcases"]),
        ],
        "final": [
            python,
            str(FINAL_SCRIPT),
            "--dataset",
            str(dataset),
            "--generation",
            str(artifacts["generation"]),
            "--judgment",
            str(artifacts["judgment"]),
            "--output",
            str(artifacts["final"]),
            "--expected-case-count",
            str(case_count),
        ],
    }
    return commands, artifacts


def validate_static_inputs(dataset: Path, manifest: Path) -> None:
    missing = [path for path in (dataset, manifest, *SCRIPT_PATHS.values()) if not path.is_file()]
    if missing:
        raise FileNotFoundError("missing Golden pipeline inputs: " + ", ".join(map(str, missing)))


def run_stage(command: list[str], env: dict[str, str], cwd: Path) -> None:
    subprocess.run(command, cwd=cwd, env=env, check=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--run-name", required=True)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--case-id", action="append", default=[])
    selection.add_argument("--limit", type=int)
    parser.add_argument("--through", choices=STAGE_ORDER, default="final")
    parser.add_argument("--router-concurrency", type=int, default=1)
    parser.add_argument("--require-llm-router", action="store_true")
    parser.add_argument(
        "--index-build-id",
        help=(
            "explicitly run against an available build instead of the source "
            "Manifest's frozen build; both values are recorded"
        ),
    )
    parser.add_argument("--python", default=sys.executable)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not RUN_NAME_RE.fullmatch(args.run_name):
        raise ValueError(
            "--run-name must contain only letters, digits, dot, underscore, or hyphen"
        )

    dataset = args.dataset.resolve()
    manifest = args.manifest.resolve()
    validate_static_inputs(dataset, manifest)
    run_root = EVALUATION_ROOT / "runs" / args.run_name
    selected_dataset, selected_manifest, rows, prepared_manifest = prepare_inputs(
        dataset,
        manifest,
        run_root,
        args.case_id,
        args.limit,
        args.index_build_id,
    )
    build_id = str(prepared_manifest["indexBuildIdAtFreeze"])
    commands, artifacts = command_plan(
        args.python,
        run_root,
        selected_dataset,
        selected_manifest,
        build_id,
        args.router_concurrency,
        args.require_llm_router,
        len(rows),
    )
    pipeline_manifest_path = run_root / "PIPELINE_MANIFEST.json"
    pipeline_manifest = {
        "formatVersion": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "runName": args.run_name,
        "selectedCaseIds": [str(row["caseId"]) for row in rows],
        "dataset": str(selected_dataset),
        "manifest": str(selected_manifest),
        "indexBuildId": build_id,
        "through": args.through,
        "implementationSha256": {
            name: sha256(path) for name, path in SCRIPT_PATHS.items()
        },
        "commands": commands,
        "artifacts": {name: str(path) for name, path in artifacts.items()},
        "completedStages": ["prepare"],
    }
    write_json(pipeline_manifest_path, pipeline_manifest)

    if args.through == "prepare":
        print(json.dumps(pipeline_manifest, ensure_ascii=False, indent=2, sort_keys=True))
        return

    env = os.environ.copy()
    env["GOLDEN_V1_1_RUN_DIRECTORY"] = args.run_name
    target_index = STAGE_ORDER.index(args.through)
    for stage in STAGE_ORDER[1 : target_index + 1]:
        run_stage(commands[stage], env, AGENT_ROOT)
        pipeline_manifest["completedStages"].append(stage)
        write_json(pipeline_manifest_path, pipeline_manifest)

    print(json.dumps(pipeline_manifest, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
