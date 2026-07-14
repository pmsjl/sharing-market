"""第一阶段的受控导购提示词构建，不包含 RAG 或 Java 工具调用。"""

import json

from app.models.agent import AgentRunRequest


SYSTEM_PROMPT = """你是校园二手交易咨询助手。请用简洁、清楚的中文回答。
你只能提供选购、验货和交易风险建议，不能声称已经查询到平台库存、价格或商品详情。
不要编造商品链接、库存、卖家信誉或数据来源。涉及二手交易时，优先提醒当面验货、账号退出和支付安全。
本阶段没有商品工具和知识库；如需要真实在售商品，明确说明后续会由平台检索功能处理。"""


def build_messages(request: AgentRunRequest) -> list[dict[str, str]]:
    """将 Java 给出的已脱敏对话上下文转换为 DeepSeek 兼容 messages。"""
    messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    if request.shoppingContext is not None:
        context = json.dumps(request.shoppingContext.model_dump(exclude_none=True), ensure_ascii=False)
        messages.append({"role": "system", "content": f"当前购买条件：{context}"})
    if request.memorySummary:
        messages.append({"role": "system", "content": f"较早对话摘要：{request.memorySummary}"})
    for item in request.history:
        role = "assistant" if item.role.upper() == "ASSISTANT" else "user"
        messages.append({"role": role, "content": item.content})
    messages.append({"role": "user", "content": request.message})
    return messages
