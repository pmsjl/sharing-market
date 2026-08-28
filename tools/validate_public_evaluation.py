from __future__ import annotations

import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]
EVALUATION = ROOT / "ai_agent_service" / "evaluation"
DATASET = EVALUATION / "public" / "dev_v1_1.jsonl"
MANIFEST = EVALUATION / "public" / "manifest.json"
SCHEMA = EVALUATION / "schemas" / "golden_case.schema.json"

FORBIDDEN_KEYS = {
    "reviewer",
    "notes",
    "requestid",
    "traces",
    "usage",
    "rawresponse",
    "raw_response",
}


def _walk_keys(value: Any) -> set[str]:
    if isinstance(value, dict):
        keys = {str(key).replace("-", "").replace("_", "").lower()
                for key in value}
        for nested in value.values():
            keys.update(_walk_keys(nested))
        return keys
    if isinstance(value, list):
        keys: set[str] = set()
        for nested in value:
            keys.update(_walk_keys(nested))
        return keys
    return set()


def validate() -> dict[str, Any]:
    raw = DATASET.read_bytes()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    rows = [
        json.loads(line)
        for line in raw.decode("utf-8").splitlines()
        if line.strip()
    ]

    assert len(rows) == 140, f"expected 140 Dev cases, got {len(rows)}"
    assert len({row["caseId"] for row in rows}) == 140
    assert {row["split"] for row in rows} == {"dev"}
    assert {row["version"] for row in rows} == {"golden-v1.1"}

    validator = Draft202012Validator(schema)
    errors = sorted(
        (error for row in rows for error in validator.iter_errors(row)),
        key=lambda error: list(error.path),
    )
    assert not errors, errors[0].message if errors else ""

    for row in rows:
        assert row["review"] == {"status": "frozen"}
        assert set(row["provenance"]) == {"source"}
        found = _walk_keys(row)
        assert not found.intersection(FORBIDDEN_KEYS), (
            row["caseId"], sorted(found.intersection(FORBIDDEN_KEYS)))

    digest = hashlib.sha256(raw).hexdigest()
    assert manifest["sha256"] == digest
    assert manifest["caseCount"] == 140
    assert manifest["split"] == "dev"
    assert sum(manifest["domainCounts"].values()) == 140
    assert Counter(row["domain"] for row in rows) == Counter(
        manifest["domainCounts"])

    return {
        "caseCount": len(rows),
        "qrelCount": sum(len(row["qrels"]) for row in rows),
        "sha256": digest,
    }


def main() -> None:
    print(json.dumps(validate(), ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
