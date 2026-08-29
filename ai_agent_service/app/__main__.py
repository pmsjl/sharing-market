"""Environment-driven production entry point for the AI Agent service."""

import uvicorn

from app.core.config import settings


def main() -> None:
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        proxy_headers=True,
        forwarded_allow_ips=settings.forwarded_allow_ips,
    )


if __name__ == "__main__":
    main()
