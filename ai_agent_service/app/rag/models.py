"""规划、索引和检索共同使用的 RAG 数据结构。"""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

KnowledgeCategory = Literal[
    "platform_policy",
    "campus_dorm",
    "campus_lifecycle",
    "course_materials",
    "course_purchase_policy",
    "community_post",
]
CourseMatchMode = Literal[
    "alias",
    "constraints",
    "constraints_no_match",
    "none",
]
CourseEvidenceState = Literal[
    "answerable",
    "clue_only",
    "unknown_after_search",
]
PostRetrievalMode = Literal["none", "primary", "course_auxiliary"]
RetrievalStatus = Literal["success", "unavailable", "failed"]
PostValidationStatus = Literal[
    "not_needed",
    "success",
    "no_valid_candidates",
    "failed",
]


class GuideDocumentMeta(BaseModel):
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
    source_type: Literal["GUIDE", "POST"] = "GUIDE"
    source_id: str
    category: KnowledgeCategory
    title: str
    section: str | None
    chunk_index: int = Field(ge=0)
    content: str
    embedding_text: str
    metadata: dict[str, Any] = Field(default_factory=dict)


class PostSnapshot(BaseModel):
    """Java 离线快照接口返回的一篇可索引 Post。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    id: int = Field(gt=0)
    title: str = Field(min_length=1, max_length=80)
    content: str = Field(min_length=1, max_length=8192)
    tags: list[str] = Field(min_length=1)
    create_time: str = Field(alias="createTime", min_length=1)
    update_time: str = Field(alias="updateTime", min_length=1)
    source_version: str = Field(
        alias="sourceVersion",
        pattern=r"^[1-9]\d*$",
    )

    @field_validator("title", "content", "create_time", "update_time")
    @classmethod
    def reject_blank_text(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Post 快照文本字段不能为空")
        return value

    @field_validator("tags")
    @classmethod
    def validate_tags(cls, tags: list[str]) -> list[str]:
        if any(not tag.strip() for tag in tags):
            raise ValueError("Post 快照标签不能为空")
        if len(tags) != len(set(tags)):
            raise ValueError("Post 快照标签不能重复")
        return tags


class PostSnapshotPage(BaseModel):
    """Java Post 快照接口的一页游标结果。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    items: list[PostSnapshot] = Field(default_factory=list)
    next_after_id: int = Field(alias="nextAfterId", ge=0)
    has_more: bool = Field(alias="hasMore")


class PostVersionCandidate(BaseModel):
    """请求内提交给 Java 的 Post ID 与索引版本。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    post_id: int = Field(alias="postId", gt=0)
    source_version: str = Field(
        alias="sourceVersion",
        pattern=r"^[1-9]\d*$",
    )


class PostVersionValidationResponse(BaseModel):
    """Java 实时校验后仍然有效的 Post 身份集合。"""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    request_id: str = Field(alias="requestId", min_length=1)
    valid_candidates: list[PostVersionCandidate] = Field(
        alias="validCandidates",
        default_factory=list,
        max_length=10,
    )


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

    post_retrieval_mode: PostRetrievalMode = "none"
    course_document_ids: list[str] = Field(default_factory=list)
    course_a_quota: int = Field(default=2, ge=0)
    include_course_purchase_policy: bool = False
    primary_guide_categories: list[KnowledgeCategory] = Field(
        default_factory=list)
    fallback_guide_categories: list[KnowledgeCategory] = Field(
        default_factory=list)
    course_auxiliary_categories: list[KnowledgeCategory] = Field(
        default_factory=list)
    course_relation_summaries: list[CourseRelationSummary] = Field(
        default_factory=list)


class RetrievedChunk(BaseModel):
    """一次检索命中的原始 chunk 及其归一化相似度。"""

    model_config = ConfigDict(extra="forbid")

    chunk_id: str
    document_id: str
    source_type: Literal["GUIDE", "POST"]
    source_id: str
    category: KnowledgeCategory
    title: str
    section: str | None
    content: str
    score: float
    metadata: dict[str, Any]


class RagContext(BaseModel):
    """回答阶段可使用的可信检索上下文。"""

    model_config = ConfigDict(extra="forbid")

    plan: RagQueryPlan
    retrieved: list[RetrievedChunk] = Field(
        max_length=8)  # 5 个 GUIDE + 3 个 POST
    course_evidence_state: CourseEvidenceState | None = None


class RagDiagnostics(BaseModel):
    """只用于日志和评测的检索诊断，不注入回答模型。"""

    model_config = ConfigDict(extra="forbid")

    retrieval_status: RetrievalStatus
    post_validation_status: PostValidationStatus = "not_needed"
    failure_reason: str | None = None
    course_match_mode: CourseMatchMode = "none"
    constraints_fallback: bool = False


class RagResolution(BaseModel):
    """分离回答证据与检索诊断。"""

    model_config = ConfigDict(extra="forbid")

    context: RagContext
    diagnostics: RagDiagnostics
