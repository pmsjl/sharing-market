"""把 Router 的资料领域机械地转换成确定性检索计划。"""

from app.rag.course_relations import CourseMatch
from app.rag.models import KnowledgeCategory, RagQueryPlan
from app.routing.query_router import RetrieveRouteDecision

NON_COURSE_CATEGORIES: tuple[KnowledgeCategory, ...] = (
    "platform_policy",
    "campus_dorm",
    "campus_lifecycle",
)

DOMAIN_GUIDE_CATEGORIES: dict[str, KnowledgeCategory] = {
    "platform_policy": "platform_policy",
    "campus_dorm": "campus_dorm",
    "campus_lifecycle": "campus_lifecycle",
}


def resolve_course_match(
    course_match: CourseMatch,
    route_decision: RetrieveRouteDecision,
) -> CourseMatch:
    """Router选择课程领域时保留全部关系匹配，否则清除偶然命中。"""
    if "course" not in route_decision.knowledge_domains:
        return CourseMatch(set(), [], [], "none")
    return course_match


def plan_query(
    course_match: CourseMatch,
    route_decision: RetrieveRouteDecision,
) -> RagQueryPlan:
    """只按资料领域规划通道，原始Query留给向量检索排序。"""
    domains = set(route_decision.knowledge_domains)
    broad_fallback = route_decision.retrieval_strategy == "broad_fallback"
    course_requested = "course" in domains
    primary_categories: list[KnowledgeCategory] = [
        category for domain, category in DOMAIN_GUIDE_CATEGORIES.items()
        if domain in domains
    ]
    if course_requested:
        course_auxiliary_categories: list[KnowledgeCategory] = list(
            primary_categories)
        if broad_fallback:
            course_auxiliary_categories = list(NON_COURSE_CATEGORIES)
        return RagQueryPlan(
            post_retrieval_mode=("course_auxiliary" if broad_fallback
                                 or "transaction_experience" in domains else
                                 "none"),
            course_document_ids=sorted(course_match.document_ids),
            include_course_purchase_policy=True,
            course_auxiliary_categories=course_auxiliary_categories,
            course_relation_summaries=course_match.relation_summaries,
        )

    return RagQueryPlan(
        post_retrieval_mode=("primary" if broad_fallback or
                             "transaction_experience" in domains else "none"),
        primary_guide_categories=primary_categories,
        fallback_guide_categories=(list(NON_COURSE_CATEGORIES)
                                   if broad_fallback else []),
    )
