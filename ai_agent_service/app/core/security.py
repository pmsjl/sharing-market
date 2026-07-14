"""Java 到 Python 的内部服务身份校验。"""

import secrets

from app.core.config import settings


class InternalAuthenticationError(Exception):
    """调用方缺少或提供了错误的内部 Token。"""


def verify_internal_token(received_token: str | None) -> None:
    """只允许持有共享内部 Token 的 Java 服务调用 Agent Run。"""
    if not settings.internal_token:
        raise RuntimeError("AI_AGENT_INTERNAL_TOKEN is not configured")
    if not received_token or not secrets.compare_digest(received_token, settings.internal_token):
        raise InternalAuthenticationError()
