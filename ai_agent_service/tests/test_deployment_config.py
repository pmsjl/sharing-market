import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi import Response, status

from app import __main__ as entrypoint
from app.api import health as health_module
from app.core.config import Settings


def _settings(**overrides) -> Settings:
    values = {
        "internal_token": "internal-token",
        "openai_api_key": "model-key",
        "openai_base_url": "https://relay.example/v1",
        "rag_enabled": False,
    }
    values.update(overrides)
    return Settings(**values)


class DeploymentHealthTests(unittest.IsolatedAsyncioTestCase):

    async def test_ready_returns_200_when_required_configuration_is_present(
            self):
        original_settings = health_module.settings
        try:
            health_module.settings = _settings()
            response = Response()

            result = await health_module.ready(response)

            self.assertEqual(response.status_code, status.HTTP_200_OK)
            self.assertEqual(result["status"], "UP")
        finally:
            health_module.settings = original_settings

    async def test_ready_returns_503_when_rag_is_enabled_but_not_ready(self):
        original_settings = health_module.settings
        original_service = health_module.agent_service
        try:
            health_module.settings = _settings(rag_enabled=True)
            health_module.agent_service = type(
                "Service",
                (),
                {"rag_service": type("Rag", (), {"ready": False})()},
            )()
            response = Response()

            result = await health_module.ready(response)

            self.assertEqual(response.status_code,
                             status.HTTP_503_SERVICE_UNAVAILABLE)
            self.assertEqual(result["status"], "NOT_READY")
        finally:
            health_module.settings = original_settings
            health_module.agent_service = original_service


class DeploymentEntrypointTests(unittest.TestCase):

    def test_entrypoint_uses_environment_derived_settings(self):
        fake_settings = SimpleNamespace(
            host="0.0.0.0",
            port=9123,
            forwarded_allow_ips="10.0.0.0/8",
        )
        with patch.object(entrypoint, "settings", fake_settings), patch.object(
                entrypoint.uvicorn, "run") as run:
            entrypoint.main()

        run.assert_called_once_with(
            "app.main:app",
            host="0.0.0.0",
            port=9123,
            proxy_headers=True,
            forwarded_allow_ips="10.0.0.0/8",
        )
