"""Canonical paths and version metadata for Golden v1.3 round two.

Evaluators may select a fresh directory with ``GOLDEN_V1_3_RUN_DIRECTORY``.
Defaults belong to v1.3 and do not write into historical run directories.
"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
AGENT_ROOT = ROOT / "ai_agent_service"
EVAL_ROOT = AGENT_ROOT / "evaluation"

VERSION_ID = "golden-v1.3-round2"
VERSION_DATE = "2026-09-12"
DIRECTORY_NAME = os.getenv(
    "GOLDEN_V1_3_RUN_DIRECTORY",
    "golden_v1_3_round2_20260912",
)
INDEX_BUILD_ID = "20260819T151857Z-b1c54bb0e56f49e89251135abebc4c71"

RUN_ROOT = EVAL_ROOT / "runs" / DIRECTORY_NAME
RESULTS_DIR = RUN_ROOT / "results"
REPORTS_DIR = RUN_ROOT / "reports"
README = RUN_ROOT / "README.md"
VERSION_MANIFEST = RUN_ROOT / "VERSION_MANIFEST.json"


def ensure_run_directories() -> None:
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)
