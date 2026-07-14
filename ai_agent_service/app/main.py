"""FastAPI 应用入口。"""

from fastapi import FastAPI

from app.api.agent import router as agent_router
from app.api.health import router as health_router


app = FastAPI(title="Sharing Market AI Agent", version="0.1.0")
app.include_router(health_router)
app.include_router(agent_router)
