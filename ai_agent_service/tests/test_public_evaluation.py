from __future__ import annotations

import importlib.util
from pathlib import Path


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
    assert result["qrelCount"] == 178
