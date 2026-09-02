"""Canonical paths and version metadata for Golden v1.1 round two.

The finalized 2026-08-21 run is immutable.  Evaluators may direct a later,
comparable rerun to a fresh directory with ``GOLDEN_V1_1_RUN_DIRECTORY``.
The default remains the historical location for backwards compatibility.
"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
AGENT_ROOT = ROOT / "ai_agent_service"
EVAL_ROOT = AGENT_ROOT / "evaluation"

VERSION_ID = "golden-v1.1-round2"
VERSION_DATE = "2026-08-21"
DIRECTORY_NAME = os.getenv(
    "GOLDEN_V1_1_RUN_DIRECTORY",
    "golden_v1_1_round2_20260821",
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
