"""与 Java internal AgentRunRequest / AgentRunResponse 一一对应的 Pydantic 模型。"""

from typing import Any

from pydantic import BaseModel, Field


class ShoppingContext(BaseModel):
    budgetMin: float | None = None
    budgetMax: float | None = None
    usageScene: str | None = None
    preferenceTags: list[str] = Field(default_factory=list)
    avoidances: list[str] = Field(default_factory=list)


class AgentHistoryMessage(BaseModel):
    role: str
    content: str


class AgentRecommendation(BaseModel):
    commodityId: int
    matchScore: int | None = None
    reason: str | None = None
    riskTip: str | None = None


class AgentSuggestedAction(BaseModel):
    type: str
    label: str
    commodityId: int | None = None
    keyword: str | None = None


class AgentSource(BaseModel):
    sourceType: str
    sourceId: int
    title: str
    excerpt: str


class AgentOutput(BaseModel):
    intent: str = "GENERAL_GUIDE"
    summary: str | None = None
    recommendations: list[AgentRecommendation] = Field(default_factory=list)
    purchaseAdvice: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    searchKeywords: list[str] = Field(default_factory=list)
    suggestedActions: list[AgentSuggestedAction] = Field(default_factory=list)
    # 第一阶段不实现 RAG，该数组必须返回空。
    sources: list[AgentSource] = Field(default_factory=list)


class AgentModelInfo(BaseModel):
    provider: str
    name: str


class AgentUsage(BaseModel):
    inputTokens: int | None = None
    outputTokens: int | None = None


class AgentToolTrace(BaseModel):
    toolName: str
    toolArguments: dict[str, Any] = Field(default_factory=dict)
    toolResultSummary: Any | None = None
    status: str
    latencyMs: int | None = None
    errorMessage: str | None = None


class AgentRunRequest(BaseModel):
    userId: int
    conversationId: int
    message: str = Field(min_length=1, max_length=1000)
    shoppingContext: ShoppingContext | None = None
    memorySummary: str | None = None
    history: list[AgentHistoryMessage] = Field(default_factory=list)


class AgentRunResponse(BaseModel):
    requestId: str
    answer: str
    output: AgentOutput
    model: AgentModelInfo
    usage: AgentUsage
    latencyMs: int
    traces: list[AgentToolTrace] = Field(default_factory=list)


class AgentErrorResponse(BaseModel):
    requestId: str | None = None
    # 例如 AI_MODEL_TIMEOUT；不使用 Java 项目的整数型 ErrorCode。
    agentErrorKey: str
    message: str
    retryable: bool
