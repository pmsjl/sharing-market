"""Compare two standardized Golden pipeline runs without hiding model variance."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


CONTRACT_FIELDS = {
    "router": ("actualRoute", "toolPolicy"),
    "retrieval": ("actualRoute", "courseEvidenceState"),
    "generation": ("status", "intent"),
    "judgment": ("overallPass", "knowledgeStateCorrect", "criticalError"),
}


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def by_case(path: Path) -> dict[str, dict[str, Any]]:
    return {str(row["caseId"]): row for row in read_jsonl(path)}


def nested(value: dict[str, Any], *keys: str) -> Any:
    current: Any = value
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def stage_projection(stage: str, row: dict[str, Any]) -> dict[str, Any]:
    if stage == "router":
        return {
            "actualRoute": row.get("actualRoute"),
            "knowledgeDomains": nested(row, "decision", "knowledge_domains"),
            "toolPolicy": nested(row, "decision", "tool_policy"),
        }
    if stage == "retrieval":
        return {
            "actualRoute": row.get("actualRoute"),
            "retrievedChunkIds": row.get("retrievedChunkIds"),
            "courseEvidenceState": row.get("courseEvidenceState"),
            "plan": row.get("plan"),
        }
    if stage == "generation":
        return {
            "status": row.get("status"),
            "intent": nested(row, "response", "output", "intent"),
            "answer": nested(row, "response", "answer") or row.get("answer"),
            "knowledgeChunkIds": (
                nested(row, "response", "output", "knowledgeChunkIds") or []
            ),
            "courseRelationIds": (
                nested(row, "response", "output", "courseRelationIds") or []
            ),
            "sourceDocumentIds": row.get("sourceDocumentIds") or [],
            "toolNames": row.get("toolNames") or [],
        }
    if stage == "judgment":
        return {
            key: row.get(key)
            for key in (
                "overallPass",
                "answerRelevance",
                "factCoverage",
                "groundedness",
                "actionAppropriateness",
                "citationAlignment",
                "knowledgeStateCorrect",
                "criticalError",
            )
        }
    raise ValueError(f"unknown stage: {stage}")


def compare_stage(
    stage: str,
    baseline_path: Path,
    candidate_path: Path,
) -> dict[str, Any]:
    baseline = by_case(baseline_path)
    candidate = by_case(candidate_path)
    case_ids = sorted(set(baseline) | set(candidate))
    rows = []
    for case_id in case_ids:
        left = stage_projection(stage, baseline.get(case_id, {}))
        right = stage_projection(stage, candidate.get(case_id, {}))
        differing_fields = sorted(
            key for key in set(left) | set(right) if left.get(key) != right.get(key)
        )
        rows.append({
            "caseId": case_id,
            "presentInBaseline": case_id in baseline,
            "presentInCandidate": case_id in candidate,
            "equivalent": not differing_fields and case_id in baseline and case_id in candidate,
            "differingFields": differing_fields,
            "baseline": left,
            "candidate": right,
        })
    contract_fields = CONTRACT_FIELDS[stage]
    contract_equivalent_count = sum(
        row["presentInBaseline"]
        and row["presentInCandidate"]
        and all(
            row["baseline"].get(field) == row["candidate"].get(field)
            for field in contract_fields
        )
        for row in rows
    )
    return {
        "baselinePath": str(baseline_path),
        "candidatePath": str(candidate_path),
        "caseSetEqual": set(baseline) == set(candidate),
        "caseCount": len(case_ids),
        "equivalentCaseCount": sum(row["equivalent"] for row in rows),
        "contractFields": list(contract_fields),
        "contractEquivalentCaseCount": contract_equivalent_count,
        "cases": rows,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-manifest", type=Path, required=True)
    parser.add_argument("--candidate-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    baseline = read_json(args.baseline_manifest.resolve())
    candidate = read_json(args.candidate_manifest.resolve())
    stages = {}
    for stage, artifact_name in (
        ("router", "router"),
        ("retrieval", "retrieval"),
        ("generation", "generation"),
        ("judgment", "judgment"),
    ):
        stages[stage] = compare_stage(
            stage,
            Path(baseline["artifacts"][artifact_name]),
            Path(candidate["artifacts"][artifact_name]),
        )

    implementation_equal = (
        baseline.get("implementationSha256") == candidate.get("implementationSha256")
    )
    case_ids_equal = baseline.get("selectedCaseIds") == candidate.get("selectedCaseIds")
    report = {
        "status": "COMPARED",
        "sameImplementation": implementation_equal,
        "sameSelectedCaseIds": case_ids_equal,
        "modelOutputsMayBeNondeterministic": True,
        "allStagesHaveSameCaseSet": all(value["caseSetEqual"] for value in stages.values()),
        "allStageContractsEquivalent": all(
            value["contractEquivalentCaseCount"] == value["caseCount"]
            for value in stages.values()
        ),
        "stages": stages,
    }
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
