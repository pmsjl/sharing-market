"""进程级依赖组装；请求处理中不得重复加载 RAG 索引。"""

from app.core.config import settings
from app.services.agent_service import AgentService


agent_service = AgentService(settings)
