"""Merge reviewed-v1.2 generation and single-v2 judgment into final Case rows."""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def index(rows: list[dict[str, Any]], label: str) -> dict[str, dict[str, Any]]:
    result = {row["caseId"]: row for row in rows}
    if len(result) != len(rows):
        raise ValueError(f"duplicate caseId in {label}")
    return result


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(
    dataset: Path,
    generation_path: Path,
    judgment_path: Path,
    output: Path,
    expected_case_count: int = 200,
) -> dict[str, Any]:
    cases = read_jsonl(dataset)
    generations = index(read_jsonl(generation_path), "generation")
    judgments = index(read_jsonl(judgment_path), "judgment")
    case_ids = {row["caseId"] for row in cases}
    if expected_case_count <= 0:
        raise ValueError("expected_case_count must be positive")
    if len(cases) != expected_case_count or len(case_ids) != expected_case_count:
        raise ValueError(
            f"dataset must contain {expected_case_count} unique Cases"
        )
    if set(generations) != case_ids:
        raise ValueError("generation Case set mismatch")
    successful = {case_id for case_id, row in generations.items() if row.get("status") == "SUCCESS"}
    if set(judgments) != successful:
        raise ValueError("judgments must exactly match successful generations")

    rows: list[dict[str, Any]] = []
    for case in cases:
        case_id = case["caseId"]
        generated = generations[case_id]
        if generated.get("status") != "SUCCESS":
            rows.append({
                "caseId": case_id,
                "domain": case["domain"],
                "split": case["split"],
                "query": case["query"],
                "expectedRoute": case["expectedRoute"],
                "expectedKnowledgeState": case["expectedKnowledgeState"],
                "generationStatus": "ERROR",
                "generationErrorType": generated.get("errorType"),
                "generationError": generated.get("error"),
                "v2": None,
                "finalOutcome": "FAIL",
                "finalReason": "generation_error",
            })
            continue
        judged = judgments[case_id]
        rows.append({
            "caseId": case_id,
            "domain": case["domain"],
            "split": case["split"],
            "query": case["query"],
            "expectedRoute": case["expectedRoute"],
            "expectedKnowledgeState": case["expectedKnowledgeState"],
            "generationStatus": "SUCCESS",
            "generationErrorType": None,
            "generationError": None,
            "v2": {
                "answerRelevance": judged["answerRelevance"],
                "factCoverage": judged["factCoverage"],
                "groundedness": judged["groundedness"],
                "actionAppropriateness": judged["actionAppropriateness"],
                "citationAlignment": judged["citationAlignment"],
                "observedKnowledgeState": judged["observedKnowledgeState"],
                "knowledgeStateCorrect": judged["knowledgeStateCorrect"],
                "unsupportedClaims": judged.get("unsupportedClaims", []),
                "reason": judged.get("reason", ""),
                "overallPass": judged["overallPass"],
                "judgeModel": judged.get("judgeModel"),
            },
            "finalOutcome": "PASS" if judged["overallPass"] else "FAIL",
            "finalReason": "v2_current_runtime",
        })

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows),
        encoding="utf-8",
    )
    outcomes = Counter(row["finalOutcome"] for row in rows)
    domains: dict[str, dict[str, int]] = {}
    for domain in sorted({row["domain"] for row in rows}):
        subset = [row for row in rows if row["domain"] == domain]
        domain_outcomes = Counter(row["finalOutcome"] for row in subset)
        domains[domain] = {
            "caseCount": len(subset),
            "passCount": domain_outcomes["PASS"],
            "failCount": domain_outcomes["FAIL"],
        }
    manifest = {
        "status": "PASS",
        "caseCount": len(rows),
        "uniqueCaseCount": len({row["caseId"] for row in rows}),
        "datasetSha256": sha256(dataset),
        "generationSha256": sha256(generation_path),
        "judgmentSha256": sha256(judgment_path),
        "outputSha256": sha256(output),
        "passCount": outcomes["PASS"],
        "failCount": outcomes["FAIL"],
        "generationErrorCount": sum(row["generationStatus"] == "ERROR" for row in rows),
        "byDomain": domains,
        "legacyUsed": False,
        "consensusUsed": False,
        "unsureProduced": False,
    }
    manifest_path = output.with_name(output.stem + "_manifest.json")
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--generation", type=Path, required=True)
    parser.add_argument("--judgment", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-case-count", type=int, default=200)
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    print(json.dumps(
        build(
            args.dataset,
            args.generation,
            args.judgment,
            args.output,
            args.expected_case_count,
        ),
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    ))
