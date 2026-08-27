"""与 Java internal AgentRunRequest / AgentRunResponse 一一对应的 Pydantic 模型。"""
from copy import deepcopy
from enum import Enum
from typing import Any, Literal

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
    model_config = ConfigDict(extra="forbid")

    role: Literal["USER", "ASSISTANT"]
    content: str = Field(
        min_length=1,
        max_length=8000,
    )


class AgentRecommendation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    #这里的是对类的配置约束，不允许出现规定以外的属性

    commodityId: str = Field(
        min_length=1,
        max_length=30,
        pattern=r"^\d+$",
        description="本轮商品搜索工具真实返回的十进制商品 ID。",
    )
    matchScore: int | None = Field(
        ge=0,
        le=100,
        description=(
            "商品与用户需求的匹配分数，范围为 0 到 100；"
            "无法合理评分时返回 null。"
        ),
    )
    reason: str | None = Field(
        max_length=500,
        description=(
            "商品匹配用户预算、用途和偏好的主要原因；"
            "没有时返回 null。"
        ),
    )
    riskTip: str | None = Field(
        max_length=500,
        description=(
            "购买该商品前需要额外确认的主要限制或风险；"
            "没有时返回 null。"
        ),
    )


class AgentCitation(BaseModel):
    model_config = ConfigDict(extra="forbid")

    chunkId: str = Field(min_length=1, max_length=200)
    section: str | None = Field(default=None, min_length=1, max_length=200)
    excerpt: str = Field(min_length=1, max_length=300)
    content: str = Field(min_length=1, max_length=1200)


class AgentSource(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sourceType: Literal["GUIDE", "POST"]
    sourceId: str = Field(min_length=1, max_length=150)
    documentId: str = Field(min_length=1, max_length=150)
    sourceVersion: str | None = Field(
        default=None,
        pattern=r"^[1-9]\d*$",
    )
    title: str = Field(min_length=1, max_length=200)
    # Retriever 对单个 GUIDE 文档最多保留 2 个 chunk；POST 最多保留 1 个。
    citations: list[AgentCitation] = Field(min_length=1, max_length=2)


class AgentRelatedPostCandidate(BaseModel):
    """由服务器按检索顺序生成，不暴露给模型的 Structured Output。"""

    model_config = ConfigDict(extra="forbid")

    postId: int = Field(gt=0)
    sourceVersion: str = Field(pattern=r"^[1-9]\d*$")


class AgentOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: AgentIntent = Field(
        description="本轮用户请求的主要业务意图。",
    )

    summary: str = Field(
        min_length=1,
        max_length=500,
        description=(
            "本轮回答的简短结论，只概括当前回答，"
            "不要复制完整 answer。"
        ),
    )

    memorySummary: str = Field(
        min_length=1,
        max_length=2000,
        description=(
            "截至本轮仍对后续对话有用的滚动会话摘要。"
            "保留用户预算、用途、偏好、避雷项、已介绍商品，"
            "以及用户作出的条件调整；"
            "不要保存寒暄、工具调用过程或完整回答原文。"
        ),
    )

    recommendations: list[AgentRecommendation] = Field(
        max_length=5,
        description=(
            "本轮实际推荐的商品列表。只能引用本轮商品搜索工具"
            "真实返回的商品 ID；没有推荐时返回空数组。"
        ),
    )

    purchaseAdvice: list[str] = Field(
        max_length=10,
        description=(
            "与当前需求直接相关的选购、比较、验货或使用建议；"
            "没有时返回空数组。"
        ),
    )

    warnings: list[str] = Field(
        max_length=10,
        description=(
            "当前商品或交易需要重点注意的风险；"
            "不要填入与本轮无关的通用提醒，没有时返回空数组。"
        ),
    )

    searchKeywords: list[str] = Field(
        max_length=5,
        description=(
            "适合用户继续搜索平台商品的简短关键词；"
            "没有时返回空数组。"
        ),
    )

    knowledgeChunkIds: list[str] = Field(
        max_length=8,
        description=(
            "本轮回答实际使用的知识 chunk ID；只能从本轮参考消息中选择，"
            "最多覆盖本轮 5 个 GUIDE 与 3 个 POST 候选；"
            "未使用时返回空数组。"
        ),
    )

    courseRelationIds: list[str] = Field(
        max_length=100,
        description=(
            "本轮回答实际使用的课程关系 ID；只能从本轮参考消息中选择，"
            "未使用时返回空数组。"
        ),
    )

    @model_validator(mode="after")
    def validate_recommendations(self):
        commodity_ids = [
            recommendation.commodityId
            for recommendation in self.recommendations
        ]

        if len(commodity_ids) != len(set(commodity_ids)):
            raise ValueError("recommendations 不能包含重复商品")

        if (self.recommendations
                and self.intent != AgentIntent.COMMODITY_RECOMMENDATION):
            raise ValueError("存在商品推荐时 intent 必须为 COMMODITY_RECOMMENDATION")

        return self


class AgentFinalResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    answer: str = Field(
        min_length=1,
        max_length=10000,
        description="直接展示给用户的完整中文回答，可以使用 Markdown。",
    )

    output: AgentOutput = Field(
        description=(
            "供 Java 落库、生成商品卡片和维护会话状态的"
            "结构化业务结果。"
        ),
    )


class AgentResponseOutput(AgentOutput):
    """返回 Java 的结构；展示来源不允许由模型直接生成。"""

    sources: list[AgentSource] = Field(default_factory=list, max_length=8)
    relatedPostCandidates: list[AgentRelatedPostCandidate] = Field(
        default_factory=list,
        max_length=3,
    )


def inline_local_schema_refs(schema: dict[str, Any]) -> dict[str, Any]:
    """展开 Pydantic 生成的本地 $defs/$ref，兼容不支持引用的中转服务。"""
    root_schema = deepcopy(schema)
    definitions = root_schema.get("$defs", {})

    def resolve(value: Any, resolving: tuple[str, ...] = ()) -> Any:
        if isinstance(value, list):
            return [resolve(item, resolving) for item in value]
        if not isinstance(value, dict):
            return value

        reference = value.get("$ref")
        if reference is not None:
            prefix = "#/$defs/"
            if not isinstance(reference, str) or not reference.startswith(prefix):
                raise ValueError(f"不支持的 JSON Schema 引用：{reference}")

            definition_name = reference.removeprefix(prefix)
            if definition_name not in definitions:
                raise ValueError(f"JSON Schema 引用不存在：{reference}")
            if definition_name in resolving:
                raise ValueError(f"JSON Schema 存在循环引用：{reference}")

            resolved_reference = resolve(
                deepcopy(definitions[definition_name]),
                resolving + (definition_name,),
            )
            resolved_siblings = {
                key: resolve(item, resolving)
                for key, item in value.items()
                if key != "$ref"
            }
            return {
                **resolved_reference,
                **resolved_siblings,
            }

        return {
            key: resolve(item, resolving)
            for key, item in value.items()
            if key != "$defs"
        }

    resolved_schema = resolve(root_schema)
    if not isinstance(resolved_schema, dict):
        raise ValueError("展开后的 JSON Schema 顶层必须是对象")
    return resolved_schema


_AGENT_FINAL_RESULT_SCHEMA = inline_local_schema_refs(
    AgentFinalResult.model_json_schema(mode="validation"),
)


AGENT_FINAL_RESULT_TEXT_FORMAT = {
    "type": "json_schema",
    "name": "agent_final_result",
    "description": "校园二手导购的用户答案和结构化业务结果",
    "strict": True,
    "schema": _AGENT_FINAL_RESULT_SCHEMA,
}

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
    history: list[AgentHistoryMessage] = Field(
        default_factory=list,
        max_length=10,
    )


class AgentRunResponse(BaseModel):
    requestId: str
    answer: str
    output: AgentResponseOutput
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
