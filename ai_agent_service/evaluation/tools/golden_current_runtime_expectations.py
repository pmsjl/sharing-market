"""Current-runtime truth corrections for historical Golden v1.1 cases."""
from __future__ import annotations

from copy import deepcopy
from typing import Any


CURRENT_RUNTIME_TRUTH_OVERRIDES: dict[str, dict[str, Any]] = {
    "course-unknown_after_search-038": {
        "expectedRoute": "retrieve",
        "expectedKnowledgeState": "unknown_after_search",
        "expectedFacts": [
            "平台学校固定为哈尔滨工业大学（深圳），不应追问学校名称。",
            "当前检索仍不能确认该课程当期是否由学校或实验室提供，应明确未知并引导核对课程通知、教师或实验室。",
        ],
        "reason": "平台学校固定为哈尔滨工业大学（深圳），缺少学校名不再澄清。",
    },
    "campus-campus-lifecycle-new-student-01": {
        "query": "新生报到前，哪些宿舍用品应该先确认学校是否提供，再决定是否购买？",
        "expectedAction": "retrieve",
        "expectedRoute": "retrieve",
        "expectedKnowledgeState": "answerable",
        "expectedFacts": [
            "购买宿舍用品前应先核对学校和宿舍实际提供的物品，避免重复购买。",
            "未确认的用品应结合入住后的实际配置和个人需求再决定是否购买。",
        ],
        "qrels": [
            {
                "acceptableSections": [],
                "documentId": "GUIDE:campus-dorm-new-student-supplies",
                "reason": "该校园指南直接覆盖学校已提供物品、提前准备和到校后购买建议。",
                "relevance": 3,
                "required": True,
                "supportingChunkIds": [
                    "GUIDE:campus-dorm-new-student-supplies#c358a58ed22e",
                    "GUIDE:campus-dorm-new-student-supplies#04c8d47a72aa",
                ],
            },
        ],
        "allowedSourceTypes": ["GUIDE"],
        "preferredSourceType": "GUIDE",
        "forbiddenDocumentPrefixes": [],
        "reason": "将原混合的物品与报到手续问题收窄为明确的宿舍用品购买决策。",
    },
    "campus-campus-lifecycle-new-student-02": {
        "expectedAction": "out_of_scope",
        "expectedRoute": "out_of_scope",
        "expectedKnowledgeState": "not_applicable",
        "expectedFacts": [
            "该问题只询问往届新生攻略中的报到安排是否仍适用，没有商品购买、出售、验货或平台交易决策。",
            "应说明该问题超出校园二手交易咨询范围，并停止知识检索和商品工具调用。",
        ],
        "qrels": [],
        "allowedSourceTypes": [],
        "preferredSourceType": None,
        "forbiddenDocumentPrefixes": ["GUIDE:", "POST:"],
        "reason": "纯报到安排属于一般校园事务，不属于校园二手交易咨询范围。",
    },
    "post-legacy-002": {
        "expectedRoute": "out_of_scope",
        "expectedKnowledgeState": "not_applicable",
        "expectedFacts": [
            "该问题只询问Docker、WSL2和数据库并行运行所需的内存容量，没有二手商品、购买、出售、验货或平台交易语境。",
            "应说明该问题超出校园二手交易咨询范围，并停止检索和工具调用。",
        ],
        "qrels": [],
        "allowedSourceTypes": [],
        "preferredSourceType": None,
        "reason": "纯电脑技术问题且没有交易或购买语境；当前Agent范围要求将其判为out_of_scope。",
    },
}


def current_runtime_override(case_id: str) -> dict[str, Any] | None:
    value = CURRENT_RUNTIME_TRUTH_OVERRIDES.get(case_id)
    return deepcopy(value) if value is not None else None


def apply_current_runtime_truth(case: dict[str, Any]) -> dict[str, Any]:
    output = deepcopy(case)
    override = current_runtime_override(str(case["caseId"]))
    if override is None:
        return output
    override.pop("reason", None)
    output.update(override)
    return output


def current_runtime_reason(case_id: str) -> str | None:
    override = CURRENT_RUNTIME_TRUTH_OVERRIDES.get(case_id)
    return str(override["reason"]) if override is not None else None
