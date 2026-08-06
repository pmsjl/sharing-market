"""为每个非空问题生成带语义兜底的确定性检索计划。"""

from app.rag.course_relations import CourseRelationIndex
from app.rag.models import KnowledgeCategory, RagQueryPlan


NON_COURSE_CATEGORIES: tuple[KnowledgeCategory, ...] = (
    "platform_policy",
    "campus_dorm",
    "campus_lifecycle",
)

CATEGORY_TERMS: dict[KnowledgeCategory, set[str]] = {
    "platform_policy": {
        "付款", "支付", "退款", "取消", "纠纷", "举报", "申诉",
        "账号", "隐私", "禁售", "面交", "验货", "平台功能",
    },
    "campus_dorm": {
        "宿舍", "寝室", "床铺", "床垫", "床帘", "桌面", "电器",
        "功率", "冰箱", "洗衣机", "搬运", "大件",
    },
    "campus_lifecycle": {
        "新生", "报到", "开学", "假期", "毕业", "退寝", "搬宿舍",
        "教材什么时候买", "开课后", "毕业季",
    },
}

COURSE_CATALOG_TERMS = {
    "哪些课程", "有什么课程", "有什么课", "课程有哪些", "开哪些课",
    "开设什么课", "课表", "培养方案", "学什么课", "学什么",
}
COURSE_MATERIAL_TERMS = {
    "教材", "课程资料", "参考书", "参考资料", "软件环境", "实验器材",
    "用什么书", "买书", "教材购买", "课程资料购买", "课程怎么买",
    "二手教材", "二手书",
}
COURSE_INTENT_TERMS = COURSE_CATALOG_TERMS | COURSE_MATERIAL_TERMS | {
    "课程", "专业课", "必修课", "选修课",
}
COURSE_PURCHASE_TERMS = {"怎么买", "可以买二手", "要不要买", "需要买吗", "购买"}
PURCHASE_POLICY_DOCUMENT_ID = "GUIDE:course-purchase-policy"


def _extend_unique(target: list, values) -> None:
    for value in values:
        if value not in target:
            target.append(value)


def plan_query(query: str, relations: CourseRelationIndex) -> RagQueryPlan:
    """生成精确文档、优先类别和兜底类别三条检索通道。"""
    if not query.strip():
        return RagQueryPlan(should_retrieve=False)

    preferred_categories: list[KnowledgeCategory] = [
        category
        for category, terms in CATEGORY_TERMS.items()
        if any(term in query for term in terms)
    ]
    material_intent = any(term in query for term in COURSE_MATERIAL_TERMS)
    course_intent = any(term in query for term in COURSE_INTENT_TERMS)

    course_match = relations.match(
        query,
        allow_dimension_only=course_intent,
    )
    exact_document_ids = sorted(course_match.document_ids)
    purchase_intent = material_intent or (
        bool(course_match.document_ids)
        and any(term in query for term in COURSE_PURCHASE_TERMS)
    )

    if course_match.document_ids:
        # 关系表选出的父文档保持精确范围，不能再扩宽到全部课程资料。
        if purchase_intent:
            _extend_unique(
                exact_document_ids,
                [PURCHASE_POLICY_DOCUMENT_ID],
            )
    elif material_intent and course_match.mode == "none":
        # 泛课程资料问题没有具体别名或关系约束，可以语义检索全部课程资料。
        _extend_unique(
            preferred_categories,
            ["course_materials", "course_purchase_policy"],
        )
    elif material_intent and course_match.mode == "constraints_no_match":
        # 约束真实存在但关系表没有覆盖，不能扩宽到无关课程；统一购买边界仍可使用。
        _extend_unique(
            exact_document_ids,
            [PURCHASE_POLICY_DOCUMENT_ID],
        )

    return RagQueryPlan(
        should_retrieve=True,
        exact_document_ids=exact_document_ids,
        preferred_categories=preferred_categories,
        fallback_categories=list(NON_COURSE_CATEGORIES),
        course_relation_summaries=course_match.relation_summaries,
        course_match_mode=course_match.mode,
        matched_course_names=course_match.course_names,
        constraints_fallback=course_match.constraints_fallback,
    )
