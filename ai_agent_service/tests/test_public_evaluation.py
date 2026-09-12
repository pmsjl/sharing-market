from __future__ import annotations

import importlib.util
import copy
import json
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = (
    ROOT / "ai_agent_service" / "evaluation" / "tools"
    / "validate_public_evaluation.py"
)


def test_public_evaluation_bundle_is_valid() -> None:
    spec = importlib.util.spec_from_file_location(
        "validate_public_evaluation", VALIDATOR)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    result = module.validate()

    assert result["caseCount"] == 140
    assert result["qrelCount"] == 109


def test_public_schema_preserves_tool_only_unknown_and_rejects_private_fields() -> None:
    evaluation = ROOT / "ai_agent_service" / "evaluation"
    rows = [json.loads(line) for line in (
        evaluation / "public/dev_v1_3.jsonl").read_text(encoding="utf-8").splitlines()]
    validator = Draft202012Validator(json.loads((
        evaluation / "schemas/golden_case.schema.json").read_text(encoding="utf-8")))
    case = next(row for row in rows if row["expectedRoute"] == "skip_rag"
                and row["expectedKnowledgeState"] == "unknown_after_search")
    validator.validate(case)
    for field, value in [("split", "test"), ("review", {"status": "frozen", "notes": "private"}),
                         ("provenance", {"source": "manual", "construction": "private"})]:
        invalid = copy.deepcopy(case)
        invalid[field] = value
        assert list(validator.iter_errors(invalid)), field
