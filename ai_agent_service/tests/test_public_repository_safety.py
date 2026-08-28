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
    if path.stat().st_size > 5 * 1024 * 1024:
        return None
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def test_repository_contains_no_forbidden_public_paths() -> None:
    violations: list[str] = []
    for path in _candidate_files():
        normalized = path.replace("\\", "/")
        if FORBIDDEN_DOC.search(normalized):
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
