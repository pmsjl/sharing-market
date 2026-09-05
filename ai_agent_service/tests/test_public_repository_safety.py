from __future__ import annotations

import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVALUATION_PREFIX = "ai_agent_service/evaluation/"
PUBLIC_EVALUATION_PREFIXES = (
    f"{EVALUATION_PREFIX}public/",
    f"{EVALUATION_PREFIX}schemas/",
)
PUBLIC_EVALUATION_FILES = {f"{EVALUATION_PREFIX}README.md"}
PUBLIC_EVALUATION_FILES.update({
    f"{EVALUATION_PREFIX}tools/build_golden_v1_2_single_v2_final_results.py",
    f"{EVALUATION_PREFIX}tools/compare_golden_runs.py",
    f"{EVALUATION_PREFIX}tools/course_question_quality.py",
    f"{EVALUATION_PREFIX}tools/golden_current_runtime_expectations.py",
    f"{EVALUATION_PREFIX}tools/golden_v1_1_round2_paths.py",
    f"{EVALUATION_PREFIX}tools/materialize_golden_v1_2_reviewed.py",
    f"{EVALUATION_PREFIX}tools/run_golden_pipeline.py",
    f"{EVALUATION_PREFIX}tools/run_golden_v1_1_answer_generation.py",
    f"{EVALUATION_PREFIX}tools/run_golden_v1_1_answer_judge.py",
    f"{EVALUATION_PREFIX}tools/run_golden_v1_1_retrieval_eval.py",
    f"{EVALUATION_PREFIX}tools/run_golden_v1_1_router_eval.py",
    f"{EVALUATION_PREFIX}tools/validate_public_evaluation.py",
})

FORBIDDEN_PRIVATE_DATA_PREFIXES = (
    "ai_agent_service/knowledge/documents/draft/",
    "ai_agent_service/knowledge/documents/reference/",
    "ai_agent_service/knowledge/normalized/",
    "ai_agent_service/knowledge/pipeline/",
    "ai_agent_service/knowledge/sources/",
    "tools/post_corpus/authoring/",
)
FORBIDDEN_PRIVATE_DATA_FILES = {
    "ai_agent_service/knowledge/acceptance_report.json",
    "ai_agent_service/knowledge/qa_source_answer_review.jsonl",
    "ai_agent_service/knowledge/requirements_traceability.json",
    "ai_agent_service/knowledge/review_report.json",
    "ai_agent_service/knowledge/snapshot_diff_report.json",
    "ai_agent_service/knowledge/source_coverage_report.json",
    "ai_agent_service/knowledge/unknown_information_report.json",
    "tools/build_commodity_description_cleanup.py",
    "tools/commodity_description_quality_report.json",
    "tools/commodity_descriptions.jsonl",
    "tools/commodity_search_regression_report.json",
    "tools/sync_commodity_seed_descriptions.py",
}

FORBIDDEN_DOC = re.compile(
    r"(?i)(^|/)(agents|claude|codex)\.md$|"
    r"[^/]*(plan|progress|implementation|code_analysis|"
    r"reproduction_guide)[^/]*\.md$"
)
SECRET_PATTERNS = (
    re.compile(r"sk-(?:proj-)?[A-Za-z0-9_-]{20,}"),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"),
    re.compile(r"(?:AKIA|ASIA)[A-Z0-9]{16}"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\."
               r"[A-Za-z0-9_-]{10,}"),
    re.compile(r"[A-Za-z][A-Za-z0-9+.-]*://[^/@\s]+:[^/@\s]+@"),
)
WINDOWS_ABSOLUTE_USER_PATH = re.compile(
    r"(?i)(?:[A-Z]:\\Users\\[^\\\s]+\\|[A-Z]:\\market\\)"
)
UNIX_ABSOLUTE_USER_PATH = re.compile(
    r"(?:^|[\s\"'(])(?:/home/[^/\s]+/|/Users/[^/\s]+/)",
    re.MULTILINE,
)
CREDENTIAL_KEY = re.compile(
    r"(?i)(api[_-]?key|access[_-]?key|secret[_-]?key|"
    r"client[_-]?secret|password|passwd|internal[_-]?token)"
)
PLACEHOLDER = re.compile(
    r"(?i)^(?:|\$\{.*\}|<.*>|your.*|example.*|change.*|replace.*|"
    r"dummy.*|placeholder.*|test[-_]?.*|x+)$"
)


def _candidate_files() -> list[str]:
    output = subprocess.check_output(
        [
            "git",
            "-c",
            f"safe.directory={ROOT.as_posix()}",
            "-C",
            str(ROOT),
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
        ]
    )
    return sorted(path for path in output.decode("utf-8").split("\0") if path)


def _read_text(path: Path) -> str | None:
    try:
        if path.stat().st_size > 5 * 1024 * 1024:
            return None
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def test_repository_contains_no_forbidden_public_paths() -> None:
    violations: list[str] = []
    for path in _candidate_files():
        normalized = path.replace("\\", "/")
        if FORBIDDEN_DOC.search(normalized):
            violations.append(normalized)
        if (normalized in FORBIDDEN_PRIVATE_DATA_FILES
                or normalized.startswith(FORBIDDEN_PRIVATE_DATA_PREFIXES)):
            violations.append(normalized)
        if normalized.startswith(EVALUATION_PREFIX) and not (
            normalized in PUBLIC_EVALUATION_FILES
            or normalized.startswith(PUBLIC_EVALUATION_PREFIXES)
        ):
            violations.append(normalized)

    assert not violations, sorted(set(violations))


def test_repository_contains_no_high_confidence_secrets_or_user_paths() -> None:
    violations: list[str] = []
    for relative in _candidate_files():
        if relative.endswith("test_public_repository_safety.py"):
            continue
        text = _read_text(ROOT / relative)
        if text is None:
            continue
        if (WINDOWS_ABSOLUTE_USER_PATH.search(text)
                or UNIX_ABSOLUTE_USER_PATH.search(text)):
            violations.append(f"absolute-user-path:{relative}")
        if any(pattern.search(text) for pattern in SECRET_PATTERNS):
            violations.append(f"secret-pattern:{relative}")

    assert not violations, violations


def test_example_configs_contain_only_placeholders_for_credentials() -> None:
    violations: list[str] = []
    for relative in _candidate_files():
        name = Path(relative).name.lower()
        if not (name == ".env.example" or name.startswith("application.example.")):
            continue
        text = _read_text(ROOT / relative)
        assert text is not None
        for number, line in enumerate(text.splitlines(), start=1):
            match = re.match(r"^\s*([A-Za-z][A-Za-z0-9_.-]*)\s*[:=]\s*"
                             r"(.*?)\s*(?:#.*)?$", line)
            if not match or not CREDENTIAL_KEY.search(match.group(1)):
                continue
            value = match.group(2).strip().strip("\"'")
            if not PLACEHOLDER.fullmatch(value):
                violations.append(f"{relative}:{number}:{match.group(1)}")

    assert not violations, violations
