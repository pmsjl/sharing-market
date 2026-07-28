"""与 Java internal AgentRunRequest / AgentRunResponse 一一对应的 Pydantic 模型。"""
from enum import Enum
from typing import Any

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    model_validator,
)


class AgentIntent(str, Enum):
    COMMODITY_RECOMMENDATION = "COMMODITY_RECOMMENDATION"
    PURCHASE_ADVICE = "PURCHASE_ADVICE"
    RISK_CHECK = "RISK_CHECK"
    GENERAL_GUIDE = "GENERAL_GUIDE"


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
    model_config = ConfigDict(extra="forbid")
    #这里的是对类的配置约束，不允许出现规定以外的属性

    commodityId: str
    matchScore: int | None = Field(
        default=None,
        ge=0,
        le=100,
    )
    reason: str | None = Field(
        default=None,
        max_length=500,
    )
    riskTip: str | None = Field(
        default=None,
        max_length=500,
    )


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
    model_config = ConfigDict(extra="forbid")

    intent: AgentIntent

    summary: str = Field(
        min_length=1,
        max_length=300,
    )

    recommendations: list[AgentRecommendation] = Field(
        default_factory=list,
        max_length=5,
    )

    purchaseAdvice: list[str] = Field(
        default_factory=list,
        max_length=10,
    )

    warnings: list[str] = Field(
        default_factory=list,
        max_length=10,
    )

    searchKeywords: list[str] = Field(
        default_factory=list,
        max_length=5,
    )

    suggestedActions: list[AgentSuggestedAction] = Field(
        default_factory=list,
    )

    sources: list[AgentSource] = Field(
        default_factory=list,
    )

    @model_validator(mode="after")
    def validate_recommendations(self):
        commodity_ids = [
            recommendation.commodityId
            for recommendation in self.recommendations
        ]

        if len(commodity_ids) != len(set(commodity_ids)):
            raise ValueError("recommendations 不能包含重复商品")

        if (
            self.recommendations
            and self.intent != AgentIntent.COMMODITY_RECOMMENDATION
        ):
            raise ValueError(
                "存在商品推荐时 intent 必须为 COMMODITY_RECOMMENDATION"
            )

        return self


class AgentFinalResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    answer: str = Field(
        min_length=1,
        max_length=8000,
    )

    output: AgentOutput


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
