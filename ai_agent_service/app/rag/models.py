"""规划、索引和检索共同使用的 RAG 数据结构。"""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


KnowledgeCategory = Literal[
    "platform_policy",
    "campus_dorm",
    "campus_lifecycle",
    "course_materials",
    "course_purchase_policy",
]
CourseMatchMode = Literal[
    "alias",
    "constraints",
    "constraints_no_match",
    "none",
]


class DocumentMeta(BaseModel):
    model_config = ConfigDict(extra="ignore")

    document_id: str = Field(pattern=r"^GUIDE:[a-zA-Z0-9:-]+$")
    category: KnowledgeCategory
    status: Literal["effective"]
    title: str
    relative_path: str
    chunking: str
    source_ids: list[str]
    source_urls: list[str]
    invalidation_condition: str
    last_verified_at: str

    repo_id: str | None = None
    course_codes: list[str] = Field(default_factory=list)
    majors: list[str] = Field(default_factory=list)
    entry_years: list[int] = Field(default_factory=list)
    evidence_scope: str | None = None
    section_source_ids: dict[str, list[str]] = Field(default_factory=dict)
    topic: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class KnowledgeChunk(BaseModel):
    """可检索的证据块；``content`` 始终保留原始正文。"""

    model_config = ConfigDict(extra="forbid")

    chunk_id: str
    document_id: str
    source_type: Literal["GUIDE"] = "GUIDE"
    source_id: str
    category: KnowledgeCategory
    title: str
    section: str | None
    chunk_index: int = Field(ge=0)
    content: str
    embedding_text: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class CourseRelationSummary(BaseModel):
    """从真实课程关系记录聚合得到的去重课程事实。"""

    model_config = ConfigDict(extra="forbid")

    course_name: str
    course_code: str
    repo_id: str
    course_document_id: str
    semester: str
    majors: list[str]
    major_codes: list[str]
    entry_years: list[int]
    relation_ids: list[str]
    relation_group_ids: list[str]
    plan_ids: list[str]
    plan_source_ids: list[str]
    plan_source_urls: list[str]


class RagQueryPlan(BaseModel):
    """确定性的分层检索计划。

    精确父文档、优先类别和兜底类别是三条独立检索通道，
    不能再解释为一个全局交集。
    """

    model_config = ConfigDict(extra="forbid")

    should_retrieve: bool
    exact_document_ids: list[str] = Field(default_factory=list)
    preferred_categories: list[KnowledgeCategory] = Field(default_factory=list)
    fallback_categories: list[KnowledgeCategory] = Field(default_factory=list)
    course_relation_summaries: list[CourseRelationSummary] = Field(
        default_factory=list
    )
    course_match_mode: CourseMatchMode = "none"
    matched_course_names: list[str] = Field(default_factory=list)
    constraints_fallback: bool = False


class RetrievedChunk(BaseModel):
    """一次检索命中的原始 chunk 及其归一化相似度。"""

    model_config = ConfigDict(extra="forbid")

    chunk_id: str
    document_id: str
    source_type: Literal["GUIDE"]
    source_id: str
    category: KnowledgeCategory
    title: str
    section: str | None
    content: str
    score: float
    metadata: dict[str, Any]


class RagContext(BaseModel):
    """一条消息对应的 RAG 上下文；degraded 表示可选检索已降级。"""

    model_config = ConfigDict(extra="forbid")

    query: str
    plan: RagQueryPlan
    retrieved: list[RetrievedChunk] = Field(max_length=5)
    degraded: bool = False
