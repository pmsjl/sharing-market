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
        "付款",
        "支付",
        "退款",
        "取消",
        "纠纷",
        "举报",
        "申诉",
        "账号",
        "隐私",
        "禁售",
        "面交",
        "验货",
        "平台功能",
        "下单",
        "订单",
        "站内余额",
        "收藏",
        "评价",
        "差评",
        "投诉",
        "权属",
        "处分权",
        "担保",
        "实名",
        "假货",
        "站外",
        "违禁",
        "受限物品",
        "个人信息",
        "检测",
        "能不能卖",
        "可以卖吗",
    },
    "campus_dorm": {
        "宿舍",
        "寝室",
        "床铺",
        "床垫",
        "床帘",
        "桌面",
        "电器",
        "功率",
        "冰箱",
        "洗衣机",
        "搬运",
        "大件",
        "床",
        "床位",
        "书桌",
        "衣柜",
        "收纳",
        "空调",
        "热水",
        "电费",
        "饮水机",
        "吹风机",
        "电梯",
        "楼栋",
        "快递",
        "邮寄",
        "退宿",
        "上床下桌",
        "违禁电器",
        "电饭煲",
        "微波炉",
    },
    "campus_lifecycle": {
        "新生",
        "报到",
        "开学",
        "假期",
        "毕业",
        "退寝",
        "搬宿舍",
        "教材什么时候买",
        "开课后",
        "毕业季",
        "校历",
        "寒假",
        "暑假",
        "寒暑假",
        "调寝",
        "退宿",
        "离校",
        "军训",
        "校园卡",
        "一卡通",
        "放假",
    },
}

COURSE_CATALOG_TERMS: set[str] = {
    "哪些课程",
    "有什么课程",
    "有什么课",
    "课程有哪些",
    "开哪些课",
    "开设什么课",
    "课表",
    "培养方案",
    "学什么课",
    "学什么",
    "上什么课",
    "修什么课",
    "开课",
    "选课",
    "课程设置",
    "教学计划",
    "专业课程",
    "课程目录",
}
COURSE_MATERIAL_TERMS: set[str] = {
    "教材",
    "课程资料",
    "参考书",
    "参考资料",
    "软件环境",
    "实验器材",
    "用什么书",
    "买书",
    "教材购买",
    "课程资料购买",
    "课程怎么买",
    "二手教材",
    "二手书",
    "教科书",
    "课本",
    "参考书目",
    "实验设备",
    "用什么软件",
    "需要什么软件",
    "装什么软件",
    "开发环境",
    "上机",
    "用什么教材",
    "有没有教材",
    "要买什么书",
    "需要买什么书",
}
COURSE_INTENT_TERMS: set[
    str] = COURSE_CATALOG_TERMS | COURSE_MATERIAL_TERMS | {
        "课程",
        "专业课",
        "必修课",
        "选修课",
        "选课",
        "上课",
        "教学",
        "学分",
        "授课",
        "学业",
    }
#注意set和dict都是使用大括号，因为这里不是键值对所以是set
#然后使用|就是取并集的意思
COURSE_PURCHASE_TERMS: set[str] = {
    "怎么买",
    "可以买二手",
    "要不要买",
    "需要买吗",
    "购买",
    "哪里买",
    "要买吗",
    "必须买吗",
    "一定要买吗",
    "需不需要买",
    "值不值得买",
    "要不要买新的",
    "买新书",
    "买旧书",
    "学校提供",
    "学校发",
    "需要自己买吗",
    "多少钱",
    "贵不贵",
}
PURCHASE_POLICY_DOCUMENT_ID = "GUIDE:course-purchase-policy"


def _extend_unique(target: list, values) -> None:
    for value in values:
        if value not in target:
            target.append(value)


def plan_query(query: str, relations: CourseRelationIndex) -> RagQueryPlan:
    """生成精确文档、优先类别和兜底类别三条检索通道。"""
    if not query.strip():
        return RagQueryPlan(should_retrieve=False)

    extra_categories: list[KnowledgeCategory] = [
        category for category, terms in CATEGORY_TERMS.items()
        if any(term in query for term in terms)
    ]
    material_intent = any(term in query for term in COURSE_MATERIAL_TERMS)
    course_intent = any(term in query for term in COURSE_INTENT_TERMS)

    course_match = relations.match(
        query,
        allow_dimension_only=course_intent,
    )
    course_document_ids = sorted(course_match.document_ids)
    purchase_intent = material_intent or (bool(
        course_match.document_ids) and any(term in query
                                           for term in COURSE_PURCHASE_TERMS))

    if course_match.document_ids:
        # 关系表选出的父文档保持精确范围，不能再扩宽到全部课程资料。
        if purchase_intent:
            _extend_unique(
                course_document_ids,
                [PURCHASE_POLICY_DOCUMENT_ID],
            )
    elif material_intent and course_match.mode == "none":
        # 泛课程资料问题没有具体别名或关系约束，可以语义检索全部课程资料。
        _extend_unique(
            extra_categories,
            ["course_materials", "course_purchase_policy"],
        )
    elif material_intent and course_match.mode == "constraints_no_match":
        # 约束真实存在但关系表没有覆盖，不能扩宽到无关课程；统一购买边界仍可使用。
        _extend_unique(
            course_document_ids,
            [PURCHASE_POLICY_DOCUMENT_ID],
        )

    return RagQueryPlan(
        should_retrieve=True,
        include_posts=True,
        course_document_ids=course_document_ids,
        extra_categories=extra_categories,
        fallback_categories=list(NON_COURSE_CATEGORIES),
        course_relation_summaries=course_match.relation_summaries,
        course_match_mode=course_match.mode,
        matched_course_names=course_match.course_names,
        constraints_fallback=course_match.constraints_fallback,
    )
