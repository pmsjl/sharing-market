import json
import asyncio
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models.agent import AgentHistoryMessage, AgentRunRequest, ShoppingContext
from app.rag.course_relations import CourseRelationIndex
from app.rag.index_store import KNOWLEDGE_ROOT
from app.core.config import Settings
from app.routing.query_router import (
    CapabilityRedirectRouteDecision,
    ClarifyRouteDecision,
    GuardrailContinue,
    GuardrailStop,
    HybridQueryRouter,
    INTENT_ROUTE_TEXT_FORMAT,
    LLMRouteDecision,
    RetrieveRouteDecision,
    SkipRagRouteDecision,
    ToolPolicy,
    build_fallback_decision,
    evaluate_guardrail,
    validate_llm_decision,
)


def _request(message: str, **overrides) -> AgentRunRequest:
    values = {
        "userId": 1,
        "conversationId": 2,
        "message": message,
    }
    values.update(overrides)
    return AgentRunRequest(**values)


def _fallback(message: str, **overrides) -> RetrieveRouteDecision:
    return build_fallback_decision(_request(message, **overrides))


def test_deterministic_fallback_never_guesses_golden_clarify_or_scope(
) -> None:
    dataset = (Path(__file__).resolve().parents[1] /
               "evaluation/public/dev_v1_1.jsonl")
    cases = [
        json.loads(line)
        for line in dataset.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    relations = CourseRelationIndex.load(KNOWLEDGE_ROOT)

    assert len(cases) == 140
    semantic_terminal_cases = [
        case for case in cases
        if case["expectedRoute"] in {"clarify", "out_of_scope"}
    ]
    assert len(semantic_terminal_cases) == 8
    assert all(
        build_fallback_decision(
            _request(case["query"]),
            relations.match(case["query"], allow_dimension_only=True),
        ).route == "retrieve"
        for case in semantic_terminal_cases)


def test_semantic_terminal_requests_are_not_decided_by_fallback() -> None:
    unclear = _fallback("这本书二手能买吗？")
    unrelated = _fallback("明天宿舍会不会停电？")

    assert unclear.route == "retrieve"
    assert unrelated.route == "retrieve"
    assert unclear.knowledge_domains == []
    assert unrelated.knowledge_domains == []
    assert unclear.tool_policy.search_commodities == "forbidden"
    assert unrelated.tool_policy.get_my_preference_signals == "forbidden"


def test_live_search_and_preference_policies_are_independent() -> None:
    filtered = _fallback("帮我找500元以内的耳机")
    broad = _fallback("推荐一台二手电脑")
    personalized = _fallback("按我的偏好推荐一台二手电脑")
    knowledge = _fallback("二手电脑怎么验货？")
    mixed = _fallback("帮我找二手电脑，并告诉我怎么验货")

    assert isinstance(filtered, RetrieveRouteDecision)
    assert isinstance(broad, RetrieveRouteDecision)
    assert isinstance(personalized, RetrieveRouteDecision)
    assert isinstance(knowledge, RetrieveRouteDecision)
    assert isinstance(mixed, RetrieveRouteDecision)
    assert filtered.tool_policy.search_commodities == "required"
    assert filtered.tool_policy.get_my_preference_signals == "optional"
    assert broad.tool_policy.get_my_preference_signals == "optional"
    assert personalized.tool_policy.get_my_preference_signals == "required"
    assert personalized.tool_policy.search_commodities == "required"
    assert knowledge.route == "retrieve"
    assert knowledge.tool_policy.search_commodities == "forbidden"
    assert mixed.route == "retrieve"
    assert mixed.tool_policy.search_commodities == "required"


def test_preference_signal_does_not_hide_other_possible_needs() -> None:
    profile = _fallback("查看我的偏好")
    mixed = _fallback("总结我的偏好，再告诉我二手电脑怎么验货")

    assert profile.route == "retrieve"
    assert profile.tool_policy.get_my_preference_signals == "required"
    assert profile.knowledge_domains == []
    assert mixed.route == "retrieve"
    assert mixed.tool_policy.get_my_preference_signals == "required"
    assert mixed.knowledge_domains == []


def test_short_followups_use_recent_history_and_shopping_context() -> None:
    history_request = _request(
        "再找找",
        history=[
            AgentHistoryMessage(role="USER", content="帮我找一台二手电脑"),
            AgentHistoryMessage(role="ASSISTANT", content="你更看重性能还是续航？"),
        ],
    )
    preference_request = _request(
        "按我的偏好",
        shoppingContext=ShoppingContext(usageScene="上课记笔记"),
    )

    history_route = build_fallback_decision(history_request)
    preference_route = build_fallback_decision(preference_request)

    assert history_route.route == "retrieve"
    assert history_route.tool_policy.search_commodities == "required"
    assert preference_route.route == "retrieve"
    assert preference_route.tool_policy.get_my_preference_signals == "required"
    assert preference_route.tool_policy.search_commodities == "required"


def test_fallback_course_match_adds_to_product_search() -> None:
    relations = CourseRelationIndex.load(KNOWLEDGE_ROOT)

    decision = build_fallback_decision(
        _request("帮我找COMP2022的教材，并告诉我这门课怎么准备"),
        relations.match(
            "帮我找COMP2022的教材，并告诉我这门课怎么准备",
            allow_dimension_only=True,
        ),
    )

    assert decision.route == "retrieve"
    assert decision.tool_policy.search_commodities == "required"
    assert decision.tool_policy.get_my_preference_signals == "optional"
    assert decision.knowledge_domains == ["course"]
    assert decision.retrieval_strategy == "broad_fallback"


def _route_response(payload: dict, *, model: str = "router-model") -> dict:
    return {
        "model":
        model,
        "output": [{
            "type":
            "message",
            "content": [{
                "type": "output_text",
                "text": json.dumps(payload, ensure_ascii=False),
            }],
        }],
        "usage": {
            "input_tokens": 21,
            "output_tokens": 7
        },
    }


class _RouterClient:

    def __init__(self, response: dict):
        self.response = response
        self.calls = []

    async def create_router_response(self, input_items, text_format):
        self.calls.append((input_items, text_format))
        return self.response


def test_previous_golden_guardrail_cases_are_decided_by_llm() -> None:
    cases = [
        ("这本书二手能买吗？", "clarify", ["transaction_experience"],
         ["book_identity"]),
        ("这个电器宿舍能用吗？", "clarify", ["campus_dorm"],
         ["appliance_identity"]),
        ("这门课要买什么？", "clarify", ["course"],
         ["course_name"]),
        ("这个二手数码产品值不值？", "clarify", ["transaction_experience"],
         ["product_identity"]),
        ("今天学校食堂矿泉水卖多少钱？", "out_of_scope", [], []),
        ("明天晚上宿舍楼会不会停电？", "out_of_scope", [], []),
        ("寒暑假离校前，宿舍物品和电器要注意什么？", "out_of_scope", [], []),
        ("暑假只离校几周，可以让宿舍电器一直通电吗？", "out_of_scope", [], []),
        ("Docker、WSL2和数据库一起开，16GB内存够不够", "out_of_scope", [], []),
        ("准备买二手笔记本跑Docker、WSL2和数据库，16GB内存够不够？",
         "continue", ["transaction_experience"], []),
        ("往届新生报到安排今年能直接照搬吗？", "out_of_scope", [], []),
        ("往届攻略推荐的宿舍用品今年还能照着买吗？", "continue",
         ["campus_dorm"], []),
    ]

    for query, disposition, domains, missing_fields in cases:
        payload = {
            "disposition":
            disposition,
            "commodity_intents": [],
            "knowledge_domains":
            domains,
            "preference_mode":
            "not_needed",
            "missing_fields":
            missing_fields,
            "clarification_question":
            ("请补充具体对象或条件。" if disposition == "clarify" else None),
            "reason":
            "根据请求含义判断",
            "confidence":
            0.96,
        }
        client = _RouterClient(_route_response(payload))
        result = asyncio.run(
            HybridQueryRouter(_router_settings(),
                              client).resolve(_request(query)))

        expected_route = "retrieve" if disposition == "continue" else disposition
        assert result.decision.route == expected_route
        assert result.diagnostics.decision_source == "llm"
        assert len(client.calls) == 1


def test_router_prompt_requires_explicit_shopping_context_for_technical_and_campus_questions(
) -> None:
    payload = {
        "disposition": "out_of_scope",
        "commodity_intents": [],
        "knowledge_domains": [],
        "preference_mode": "not_needed",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "没有购买或交易上下文",
        "confidence": 0.99,
    }
    client = _RouterClient(_route_response(payload))
    result = asyncio.run(
        HybridQueryRouter(_router_settings(), client).resolve(
            _request("Docker、WSL2和数据库一起开，16GB内存够不够")))

    assert result.decision.route == "out_of_scope"
    input_items, text_format = client.calls[0]
    system_prompt = input_items[0]["content"]
    disposition_description = text_format["schema"]["properties"][
        "disposition"]["description"]
    required_contracts = [
        "不能自行补出购物意图",
        "单纯的新生报到手续、往届报到安排",
        "明确出现购买、二手商品选择、验货或具体商品使用决策",
    ]
    for contract in required_contracts:
        assert contract in system_prompt or contract in disposition_description


def test_public_scope_corrections_are_frozen_in_dev_dataset() -> None:
    dataset = (Path(__file__).resolve().parents[1] /
               "evaluation/public/dev_v1_1.jsonl")
    cases = {
        row["caseId"]: row
        for row in (
            json.loads(line)
            for line in dataset.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    }

    technical = cases["post-legacy-002"]
    assert technical["expectedRoute"] == "out_of_scope"
    assert technical["expectedKnowledgeState"] == "not_applicable"
    assert technical["qrels"] == []
    assert technical["allowedSourceTypes"] == []
    assert technical["preferredSourceType"] is None

    new_student = cases["campus-campus-lifecycle-new-student-01"]
    previous_guide = cases["campus-campus-lifecycle-new-student-02"]
    move_checkout = cases["campus-campus-lifecycle-dorm-move-checkout-01"]
    assert "宿舍用品" in new_student["query"]
    assert "学校是否提供" in new_student["query"]
    assert new_student["qrels"][0]["documentId"] == (
        "GUIDE:campus-dorm-new-student-supplies")
    assert "宿舍用品" in previous_guide["query"]
    assert previous_guide["qrels"][0]["documentId"] == (
        "GUIDE:campus-dorm-new-student-supplies")
    assert "转卖、转赠或清理" in move_checkout["query"]


def _router_settings(**overrides) -> Settings:
    values = {
        "intent_router_enabled": True,
        "intent_router_confidence_threshold": 0.75,
    }
    values.update(overrides)
    return Settings(**values)


def test_guardrail_blocks_business_actions_without_llm() -> None:
    client = _RouterClient({})
    router = HybridQueryRouter(_router_settings(), client)

    refund = asyncio.run(router.resolve(_request("帮我申请退款")))
    order = asyncio.run(router.resolve(_request("我的订单状态是什么")))

    assert refund.decision.route == "capability_redirect"
    assert order.decision.route == "capability_redirect"
    assert refund.diagnostics.decision_source == "guardrail"
    assert client.calls == []


def test_guardrail_uses_general_business_action_components() -> None:
    terminal_queries = [
        "订单给我取消掉",
        "马上替我举报这个卖家",
        "请帮我办理退款",
        "直接支付订单",
    ]
    informational_queries = [
        "怎么申请退款？",
        "投诉流程是什么？",
        "订单取消规则是什么？",
    ]

    for query in terminal_queries:
        result = evaluate_guardrail(_request(query))
        assert result.action == "stop"
        assert result.decision.route == "capability_redirect"
    for query in informational_queries:
        result = evaluate_guardrail(_request(query))
        assert result.action == "continue"


def test_guardrail_models_reject_unknown_rule_id() -> None:
    with pytest.raises(ValidationError):
        GuardrailContinue.model_validate({"rule_id": "typo_rule"})

    with pytest.raises(ValidationError):
        GuardrailStop.model_validate({
            "decision": {
                "route": "capability_redirect",
                "redirect_target": "orders",
            },
            "rule_id": "typo_rule",
        })


def test_route_models_reject_unknown_execution_constraint() -> None:
    with pytest.raises(ValidationError):
        GuardrailContinue.model_validate({
            "execution_constraints": ["no_bussiness_action"],
        })

    with pytest.raises(ValidationError):
        RetrieveRouteDecision.model_validate({
            "execution_constraints": ["unknown_constraint"],
            "retrieval_strategy": "targeted",
        })

    with pytest.raises(ValidationError):
        SkipRagRouteDecision.model_validate({
            "tool_policy": ToolPolicy().model_dump(),
            "execution_constraints": ["unknown_constraint"],
        })


def test_policy_plus_operation_keeps_constraint_and_uses_llm() -> None:
    payload = {
        "disposition": "continue",
        "commodity_intents": [],
        "knowledge_domains": ["platform_policy"],
        "preference_mode": "not_needed",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "需要解释退款规则",
        "confidence": 0.96,
    }
    client = _RouterClient(_route_response(payload))
    result = asyncio.run(
        HybridQueryRouter(_router_settings(),
                          client).resolve(_request("退款规则是什么，也帮我申请退款")))

    assert result.decision.route == "retrieve"
    assert result.decision.execution_constraints == ["no_business_action"]
    assert result.diagnostics.decision_source == "llm"
    assert len(client.calls) == 1


def test_llm_mixed_intent_controls_rag_and_tools() -> None:
    payload = {
        "disposition": "continue",
        "commodity_intents": ["recommend"],
        "knowledge_domains": ["transaction_experience"],
        "preference_mode": "eligible",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "推荐商品并说明验货方法",
        "confidence": 0.93,
    }
    client = _RouterClient(_route_response(payload))
    request = _request(
        "整点能上课用的本子呗，顺便教我到手咋瞅",
        history=[
            AgentHistoryMessage(role="USER", content=f"历史{index}")
            for index in range(6)
        ],
    )
    result = asyncio.run(
        HybridQueryRouter(_router_settings(), client).resolve(request))

    assert result.diagnostics.decision_source == "llm"
    assert isinstance(result.decision, RetrieveRouteDecision)
    assert result.decision.tool_policy.search_commodities == "required"
    assert result.decision.knowledge_domains == ["transaction_experience"]
    assert result.diagnostics.input_tokens == 21
    router_context = json.loads(client.calls[0][0][1]["content"])
    assert len(router_context["recentHistory"]) == 4


def test_low_confidence_and_invalid_policy_fall_back_to_p0() -> None:
    low = {
        "disposition": "continue",
        "commodity_intents": ["search"],
        "knowledge_domains": [],
        "preference_mode": "not_needed",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "实时搜索",
        "confidence": 0.40,
    }
    invalid = {
        **low,
        "confidence": 0.99,
        "preference_mode": "eligible",
    }
    for payload in (low, invalid):
        result = asyncio.run(
            HybridQueryRouter(
                _router_settings(),
                _RouterClient(_route_response(payload)),
            ).resolve(_request("帮我找500元以内的耳机")))
        assert result.diagnostics.decision_source == "deterministic_fallback"
        assert result.decision.route == "retrieve"
        assert result.decision.tool_policy.search_commodities == "required"


def test_guardrail_information_question_is_not_terminal() -> None:
    result = evaluate_guardrail(_request("平台退款规则是什么？"))
    assert result.action == "continue"
    assert result.execution_constraints == []


def test_guardrail_does_not_classify_normal_semantic_requests() -> None:
    queries = [
        "这本书二手能买吗？",
        "这个电器宿舍能用吗？",
        "这门课要买什么？",
        "这个二手数码产品值不值？",
        "今天学校食堂矿泉水卖多少钱？",
        "明天晚上宿舍楼会不会停电？",
        "寒暑假离校前，宿舍物品和电器要注意什么？",
        "暑假只离校几周，可以让宿舍电器一直通电吗？",
    ]

    assert all(evaluate_guardrail(_request(query)).action == "continue"
               for query in queries)


def test_course_clarification_is_preserved_when_course_identity_is_missing(
) -> None:
    model_decision = LLMRouteDecision(
        disposition="clarify",
        commodity_intents=[],
        knowledge_domains=["course"],
        preference_mode="not_needed",
        missing_fields=["course_name"],
        clarification_question="请补充课程名称。",
        reason="尚未说明是哪门课程",
        confidence=0.96,
    )

    decision = validate_llm_decision(model_decision)

    assert decision.route == "clarify"
    assert decision.missing_fields == ["course_name"]
    assert not hasattr(decision, "knowledge_domains")
    assert not hasattr(decision, "tool_policy")


def test_fixed_school_is_removed_without_erasing_course_request() -> None:
    model_decision = LLMRouteDecision(
        disposition="clarify",
        commodity_intents=[],
        knowledge_domains=["course"],
        preference_mode="not_needed",
        missing_fields=["school"],
        clarification_question="请补充学校。",
        reason="误把固定学校当成缺失信息",
        confidence=0.96,
    )

    decision = validate_llm_decision(model_decision)

    assert decision.route == "retrieve"
    assert decision.knowledge_domains == ["course"]
    assert not hasattr(decision, "missing_fields")


def test_final_decisions_reject_removed_diagnostic_fields() -> None:
    for model, values in (
        (ClarifyRouteDecision, {
            "missing_fields": ["message"],
            "clarification_question": "请补充问题。",
            "reason": "旧字段",
        }),
        (CapabilityRedirectRouteDecision, {
            "redirect_target": "orders",
            "decision_source": "guardrail",
        }),
    ):
        try:
            model.model_validate(values)
        except Exception:
            continue
        raise AssertionError("旧的诊断字段不应被最终决策模型接受")


def test_router_context_uses_fixed_hit_shenzhen_institution() -> None:
    payload = {
        "disposition": "continue",
        "commodity_intents": [],
        "knowledge_domains": ["course"],
        "preference_mode": "not_needed",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "检索本校课程供给证据",
        "confidence": 0.95,
    }
    client = _RouterClient(_route_response(payload))
    result = asyncio.run(
        HybridQueryRouter(_router_settings(),
                          client).resolve(_request("哈工深这门课实验室提供器材吗")))

    context = json.loads(client.calls[0][0][1]["content"])
    assert result.decision.route == "retrieve"
    assert context["trustedInstitution"]["canonicalName"] == "哈尔滨工业大学（深圳）"
    assert "哈工深" in context["trustedInstitution"]["aliases"]


def test_router_context_contains_only_typed_course_match_summary() -> None:
    query = "数据结构这门课要准备什么教材？"
    payload = {
        "disposition": "continue",
        "commodity_intents": [],
        "knowledge_domains": ["course"],
        "preference_mode": "not_needed",
        "missing_fields": [],
        "clarification_question": None,
        "reason": "查询明确课程的资料",
        "confidence": 0.95,
    }
    relations = CourseRelationIndex.load(KNOWLEDGE_ROOT)
    course_match = relations.match(query, allow_dimension_only=True)
    client = _RouterClient(_route_response(payload))

    asyncio.run(
        HybridQueryRouter(_router_settings(), client).resolve(
            _request(query),
            course_match,
        ))

    context = json.loads(client.calls[0][0][1]["content"])
    summary = context["courseMatchSummary"]
    assert set(summary) == {"courseNames", "hasExactCourseDocuments"}
    assert summary["courseNames"]
    assert summary["hasExactCourseDocuments"] is True


def test_router_schema_is_generated_from_described_pydantic_model() -> None:
    schema = INTENT_ROUTE_TEXT_FORMAT["schema"]
    schema_text = json.dumps(schema, ensure_ascii=False)

    assert set(schema["required"]) == {
        "disposition",
        "commodity_intents",
        "knowledge_domains",
        "preference_mode",
        "missing_fields",
        "clarification_question",
        "reason",
        "confidence",
    }
    assert schema["additionalProperties"] is False
    assert "$defs" not in schema_text
    assert "$ref" not in schema_text
    assert "continue" in schema["properties"]["disposition"]["description"]
    assert "clarify" in schema["properties"]["disposition"]["description"]
    assert "哈尔滨工业大学（深圳）" in (
        schema["properties"]["missing_fields"]["description"])
    assert "eligible" in (
        schema["properties"]["preference_mode"]["description"])
    assert "transaction_experience" in (
        schema["properties"]["knowledge_domains"]["description"])
    assert "course_question_type" not in schema["properties"]
