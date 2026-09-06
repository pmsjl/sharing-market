"""Merge Router retry artifacts without replacing a valid LLM decision by fallback."""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")


def source(row: dict[str, Any]) -> str | None:
    return row.get("diagnostics", {}).get("decision_source")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--retry", type=Path, action="append", default=[])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    base_rows = read_jsonl(args.base.resolve())
    order = [str(row["caseId"]) for row in base_rows]
    merged = {str(row["caseId"]): row for row in base_rows}
    if len(merged) != len(order):
        raise ValueError("base Router artifact contains duplicate Case IDs")

    attempts: list[dict[str, Any]] = []
    for retry_path in args.retry:
        path = retry_path.resolve()
        rows = read_jsonl(path)
        unknown = sorted(set(str(row["caseId"]) for row in rows) - set(merged))
        if unknown:
            raise ValueError(f"retry artifact contains unknown Case IDs: {unknown}")
        accepted = 0
        for row in rows:
            case_id = str(row["caseId"])
            if source(row) == "llm" and source(merged[case_id]) != "llm":
                merged[case_id] = row
                accepted += 1
        attempts.append({
            "path": str(path),
            "sha256": sha256(path),
            "caseCount": len(rows),
            "llmCount": sum(source(row) == "llm" for row in rows),
            "acceptedLlmCount": accepted,
        })

    output = args.output.resolve()
    write_jsonl(output, [merged[case_id] for case_id in order])
    counts = Counter(source(row) for row in merged.values())
    unresolved = [case_id for case_id in order if source(merged[case_id]) != "llm"]
    report = {
        "status": "PASS" if not unresolved else "INCOMPLETE",
        "base": str(args.base.resolve()),
        "baseSha256": sha256(args.base.resolve()),
        "attempts": attempts,
        "caseCount": len(order),
        "decisionSourceCounts": dict(sorted(counts.items())),
        "unresolvedCaseIds": unresolved,
        "output": str(output),
        "outputSha256": sha256(output),
    }
    report_path = args.report.resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
