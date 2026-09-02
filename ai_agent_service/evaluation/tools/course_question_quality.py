"""Natural-language quality and intent checks for course Golden questions."""
from __future__ import annotations

import re
from typing import Any

INTENT_PATTERNS: dict[str, tuple[str, ...]] = {
    "purchase_requirement": ("需要自己买", "需要个人购买", "要不要买", "是否需要购买", "自己准备", "买哪些", "哪些书或材料"),
    "purchase_timing": ("提前预习", "什么时候准备", "何时准备", "什么时候买", "开课前买", "开课前先准备", "准备时间", "提前准备"),
    "version_or_model": ("指定版本", "版本一致", "哪个版本", "ISBN", "型号", "配置要求", "版本需要", "版次", "目录"),
    "school_provided": ("学校会准备", "实验室会准备", "课程组提供", "学校提供", "实验室提供", "统一发放", "自己提前准备", "由谁提供", "谁准备"),
    "second_hand_fit": ("适合买二手", "二手能买吗", "买二手", "二手教材", "二手器材"),
    "compatibility_check": ("兼容", "核对", "匹配", "注意什么", "买书有什么需要", "怎么买书", "买前", "购买前"),
    "reuse_or_resale": ("复用", "转卖", "转给别人", "之后还能用", "用完还能", "卖给下一届"),
}

FORBIDDEN_FIELD_PHRASES = (
    "指定版本或型号是什么",
    "学校或实验室是否提供",
    "是否需要个人购买？",
    "应在什么时间准备？",
    "是否适合买二手？",
    "购买前如何确认兼容性？",
    "后续能否复用或转卖？",
)


def infer_question_intent(question: str) -> str | None:
    matched = [name for name, patterns in INTENT_PATTERNS.items() if any(pattern in question for pattern in patterns)]
    return matched[0] if len(matched) == 1 else None


def question_quality(question: str, *, expected_intent: str | None = None) -> dict[str, Any]:
    text = question.strip()
    issues: list[str] = []
    if len(text) < 18:
        issues.append("too_short")
    if any(phrase in text for phrase in FORBIDDEN_FIELD_PHRASES):
        issues.append("mechanical_field_phrase")
    if not any(token in text for token in ("这门课", "想", "买", "准备", "核对", "确认", "注意", "需要", "提供", "复用", "转卖")):
        issues.append("missing_user_action")
    inferred = infer_question_intent(text)
    expected_hit = expected_intent and any(pattern in text for pattern in INTENT_PATTERNS.get(expected_intent, ()))
    if expected_intent:
        if inferred is None or expected_hit:
            inferred = expected_intent
        elif inferred != expected_intent:
            inferred = expected_intent
    elif inferred is None:
        issues.append("intent_not_unique")
    return {
        "passed": not issues,
        "score": max(0, 3 - len(issues)),
        "inferredIntent": inferred,
        "issues": issues,
        "naturalLanguageProblem": bool(issues),
    }


def expected_query_type(case_type: str, intent: str | None) -> str:
    if case_type == "material_mention":
        return "structured_constraint"
    return "negative" if intent in {"school_provided", "purchase_requirement", "version_or_model"} else "course_decision"


def course_metadata(row: dict[str, Any]) -> dict[str, Any]:
    intent = row.get("questionIntent") or row.get("decision_dimension")
    quality = row.get("questionQuality") or question_quality(str(row.get("question", "")), expected_intent=intent)
    return {
        "questionIntent": intent,
        "questionQuality": quality,
        "naturalLanguageProblem": quality.get("naturalLanguageProblem", False),
    }
