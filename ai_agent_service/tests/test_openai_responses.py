import copy
import json
import unittest
from unittest.mock import patch

import httpx
from pydantic import ValidationError

from app.api import health as health_module
from app.clients.openai_responses import (
    OpenAIResponsesClient,
    OpenAIResponsesClientError,
)
from app.core.config import Settings
from app.models.agent import (
    AGENT_FINAL_RESULT_TEXT_FORMAT,
    AgentRunRequest,
)
from app.prompts.shopping_guide import build_messages
from app.services.agent_service import AgentService, AgentServiceError


def make_settings(**overrides) -> Settings:
    values = {
        "internal_token": "internal-token",
        "openai_api_key": "test-key",
        "openai_base_url": "https://relay.example/v1",
        "openai_model": "gpt-5.6-terra",
        "openai_timeout_seconds": 30,
        "openai_reasoning_effort": "medium",
        "openai_text_verbosity": "medium",
        "java_backend_base_url": "http://127.0.0.1:8102",
        "java_backend_timeout_seconds": 10,
        "max_tool_rounds": 4,
    }
    values.update(overrides)
    return Settings(**values)


async def create_response_with_handler(
    client: OpenAIResponsesClient,
    handler,
    input_items=None,
    tools=None,
):
    async_client = httpx.AsyncClient(
        transport=httpx.MockTransport(handler),
    )
    with patch(
        "app.clients.openai_responses.httpx.AsyncClient",
        return_value=async_client,
    ):
        return await client.create_response(
            input_items or [],
            tools or [],
            AGENT_FINAL_RESULT_TEXT_FORMAT,
        )


class StubOpenAIClient:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    async def create_response(self, input_items, tools, text_format):
        self.calls.append({
            "input_items": copy.deepcopy(input_items),
            "tools": copy.deepcopy(tools),
            "text_format": copy.deepcopy(text_format),
        })
        return self.responses.pop(0)


class StubCommodityItem:
    def __init__(self, commodity_id):
        self.id = commodity_id


class StubToolResult:
    matchedCount = 1
    items = [StubCommodityItem("1001")]

    def model_dump_json(self):
        return json.dumps({
            "requestId": "request-1",
            "matchedCount": self.matchedCount,
            "items": [
                {"id": item.id}
                for item in self.items
            ],
        })


class StubJavaBackendClient:
    def __init__(self):
        self.calls = []

    async def search_commodities(self, request_id, arguments):
        self.calls.append((request_id, arguments))
        return StubToolResult()


def structured_output_message(
    answer="可以先确认预算和主要用途。",
    commodity_ids=None,
):
    commodity_ids = commodity_ids or []
    payload = {
        "answer": answer,
        "output": {
            "intent": (
                "COMMODITY_RECOMMENDATION"
                if commodity_ids
                else "GENERAL_GUIDE"
            ),
            "summary": "本轮回答摘要",
            "memorySummary": "用户正在咨询校园二手商品",
            "recommendations": [
                {
                    "commodityId": commodity_id,
                    "matchScore": 90,
                    "reason": "符合用户当前需求",
                    "riskTip": None,
                }
                for commodity_id in commodity_ids
            ],
            "purchaseAdvice": [],
            "warnings": [],
            "searchKeywords": [],
        },
    }
    return {
        "type": "message",
        "role": "assistant",
        "content": [{
            "type": "output_text",
            "text": json.dumps(payload, ensure_ascii=False),
        }],
    }


class OpenAIResponsesClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_request_uses_responses_payload(self):
        captured = {}

        def handler(request: httpx.Request) -> httpx.Response:
            captured["request"] = request
            captured["payload"] = json.loads(request.content)
            return httpx.Response(
                200,
                json={"output": [], "usage": {}},
                request=request,
            )

        client = OpenAIResponsesClient(make_settings())
        result = await create_response_with_handler(
            client,
            handler,
            [{"role": "user", "content": "推荐一台电脑"}],
            [{"type": "function", "name": "search_commodities"}],
        )

        self.assertEqual(result["output"], [])
        self.assertEqual(
            str(captured["request"].url),
            "https://relay.example/v1/responses",
        )
        self.assertEqual(
            captured["request"].headers["authorization"],
            "Bearer test-key",
        )
        self.assertEqual(
            captured["request"].headers["user-agent"],
            "sharing-market-ai-agent/0.1",
        )
        self.assertEqual(
            captured["request"].headers["accept"],
            "application/json",
        )
        self.assertEqual(captured["payload"]["model"], "gpt-5.6-terra")
        self.assertEqual(captured["payload"]["reasoning"], {"effort": "medium"})
        text_config = captured["payload"]["text"]
        self.assertEqual(text_config["verbosity"], "medium")
        self.assertEqual(text_config["format"]["type"], "json_schema")
        self.assertEqual(text_config["format"]["name"], "agent_final_result")
        self.assertIs(text_config["format"]["strict"], True)
        self.assertEqual(
            set(text_config["format"]["schema"]["required"]),
            {"answer", "output"},
        )
        self.assertIs(
            text_config["format"]["schema"]["additionalProperties"],
            False,
        )
        schema_text = json.dumps(
            text_config["format"]["schema"],
            ensure_ascii=False,
        )
        self.assertNotIn('"$defs"', schema_text)
        self.assertNotIn('"$ref"', schema_text)
        output_schema = (
            text_config["format"]["schema"]
            ["properties"]["output"]
        )
        self.assertEqual(
            set(output_schema["required"]),
            {
                "intent",
                "summary",
                "memorySummary",
                "recommendations",
                "purchaseAdvice",
                "warnings",
                "searchKeywords",
            },
        )
        self.assertIs(output_schema["additionalProperties"], False)
        self.assertIs(captured["payload"]["store"], False)
        self.assertIs(captured["payload"]["stream"], False)
        self.assertNotIn("temperature", captured["payload"])
        self.assertNotIn("messages", captured["payload"])

    async def test_sse_fallback_extracts_completed_response(self):
        completed_item = {
            "id": "msg_test",
            "type": "message",
            "status": "completed",
            "role": "assistant",
            "content": [{
                "type": "output_text",
                "text": "连接测试成功",
                "annotations": [],
            }],
        }
        completed_response = {
            "id": "resp_test",
            "object": "response",
            "status": "completed",
            "model": "gpt-5.6-terra",
            # 模拟兼容中转在最终 completed 事件中丢失 output。
            "output": [],
            "usage": {
                "input_tokens": 10,
                "output_tokens": 2,
            },
        }
        sse_body = "\n".join([
            "event: response.created",
            'data: {"type":"response.created","response":{"status":"in_progress"}}',
            "",
            "event: response.output_item.done",
            "data: " + json.dumps({
                "type": "response.output_item.done",
                "output_index": 0,
                "item": completed_item,
            }),
            "",
            "event: response.completed",
            "data: " + json.dumps({
                "type": "response.completed",
                "response": completed_response,
            }),
            "",
        ])

        def handler(request):
            return httpx.Response(
                200,
                text=sse_body,
                headers={"Content-Type": "text/event-stream"},
                request=request,
            )

        client = OpenAIResponsesClient(make_settings())
        result = await create_response_with_handler(client, handler)
        self.assertEqual(result["output"], [completed_item])
        self.assertEqual(result["status"], "completed")

    async def test_sse_fallback_reconstructs_function_call(self):
        function_call = {
            "id": "fc_test",
            "type": "function_call",
            "call_id": "call_test",
            "name": "search_commodities",
            "arguments": '{"keywords":["手机"]}',
            "status": "completed",
        }
        sse_body = "\n".join([
            "event: response.output_item.done",
            "data: " + json.dumps({
                "type": "response.output_item.done",
                "output_index": 0,
                "item": function_call,
            }),
            "",
            "event: response.completed",
            "data: " + json.dumps({
                "type": "response.completed",
                "response": {
                    "id": "resp_tool",
                    "object": "response",
                    "status": "completed",
                    "model": "gpt-5.6-terra",
                    "output": [],
                    "usage": {},
                },
            }),
            "",
        ])

        def handler(request):
            return httpx.Response(
                200,
                text=sse_body,
                headers={"Content-Type": "text/event-stream"},
                request=request,
            )

        client = OpenAIResponsesClient(make_settings())
        result = await create_response_with_handler(client, handler)
        self.assertEqual(result["output"], [function_call])

    async def test_incomplete_sse_is_rejected(self):
        def handler(request):
            return httpx.Response(
                200,
                text=(
                    "event: response.created\n"
                    'data: {"type":"response.created",'
                    '"response":{"status":"in_progress"}}\n\n'
                ),
                headers={"Content-Type": "text/event-stream"},
                request=request,
            )

        client = OpenAIResponsesClient(make_settings())
        with self.assertRaises(OpenAIResponsesClientError) as raised:
            await create_response_with_handler(client, handler)
        self.assertEqual(
            raised.exception.agent_error_key,
            "AI_MODEL_RESPONSE_INVALID",
        )

    async def test_http_statuses_are_mapped(self):
        cases = [
            (401, "AI_MODEL_AUTH_FAILED", False),
            (403, "AI_MODEL_AUTH_FAILED", False),
            (429, "AI_MODEL_RATE_LIMITED", True),
            (400, "AI_MODEL_REQUEST_REJECTED", False),
            (404, "AI_MODEL_REQUEST_REJECTED", False),
            (500, "AI_MODEL_UNAVAILABLE", True),
        ]
        for status, expected_key, retryable in cases:
            with self.subTest(status=status):
                def handler(request, response_status=status):
                    return httpx.Response(response_status, request=request)

                client = OpenAIResponsesClient(make_settings())
                with self.assertRaises(OpenAIResponsesClientError) as raised:
                    await create_response_with_handler(client, handler)
                self.assertEqual(raised.exception.agent_error_key, expected_key)
                self.assertEqual(raised.exception.retryable, retryable)

    async def test_timeout_and_invalid_json_are_mapped(self):
        def timeout_handler(request):
            raise httpx.ReadTimeout("timeout", request=request)

        timeout_client = OpenAIResponsesClient(make_settings())
        with self.assertRaises(OpenAIResponsesClientError) as timeout_error:
            await create_response_with_handler(timeout_client, timeout_handler)
        self.assertEqual(timeout_error.exception.agent_error_key, "AI_MODEL_TIMEOUT")

        def invalid_json_handler(request):
            return httpx.Response(200, content=b"not-json", request=request)

        invalid_client = OpenAIResponsesClient(make_settings())
        with self.assertRaises(OpenAIResponsesClientError) as invalid_error:
            await create_response_with_handler(invalid_client, invalid_json_handler)
        self.assertEqual(
            invalid_error.exception.agent_error_key,
            "AI_MODEL_RESPONSE_INVALID",
        )


class AgentServiceResponsesTests(unittest.IsolatedAsyncioTestCase):
    def make_request(self):
        return AgentRunRequest(
            userId=1,
            conversationId=2,
            message="推荐一台二手电脑",
        )

    async def test_direct_answer_returns_usage_and_model(self):
        openai_client = StubOpenAIClient([{
            "model": "gpt-5.6-terra",
            "output": [structured_output_message()],
            "usage": {
                "input_tokens": 20,
                "output_tokens": 8,
            },
        }])
        service = AgentService(
            make_settings(),
            openai_client=openai_client,
            java_backend_client=StubJavaBackendClient(),
        )

        result = await service.run("request-1", self.make_request())

        self.assertEqual(result.answer, "可以先确认预算和主要用途。")
        self.assertEqual(result.model.provider, "openai")
        self.assertEqual(result.model.name, "gpt-5.6-terra")
        self.assertEqual(result.usage.inputTokens, 20)
        self.assertEqual(result.usage.outputTokens, 8)

    async def test_invalid_text_verbosity_is_rejected_before_model_call(self):
        openai_client = StubOpenAIClient([])
        service = AgentService(
            make_settings(openai_text_verbosity="verbose"),
            openai_client=openai_client,
            java_backend_client=StubJavaBackendClient(),
        )

        with self.assertRaises(AgentServiceError) as invalid_config:
            await service.run("request-1", self.make_request())

        self.assertEqual(
            invalid_config.exception.agent_error_key,
            "AI_AGENT_CONFIG_INVALID",
        )
        self.assertEqual(openai_client.calls, [])

    async def test_function_call_output_is_replayed(self):
        function_call = {
            "type": "function_call",
            "call_id": "call-1",
            "name": "search_commodities",
            "arguments": json.dumps({
                "keywords": ["电脑"],
                "maxPrice": 3000,
                "limit": 5,
            }),
        }
        openai_client = StubOpenAIClient([
            {
                "output": [function_call],
                "usage": {"input_tokens": 10, "output_tokens": 3},
            },
            {
                "output": [
                    structured_output_message(
                        answer="找到一件符合预算的商品。",
                        commodity_ids=["1001"],
                    )
                ],
                "usage": {"input_tokens": 15, "output_tokens": 6},
            },
        ])
        java_client = StubJavaBackendClient()
        service = AgentService(
            make_settings(),
            openai_client=openai_client,
            java_backend_client=java_client,
        )

        result = await service.run("request-1", self.make_request())

        self.assertEqual(len(java_client.calls), 1)
        replayed_input = openai_client.calls[1]["input_items"]
        self.assertIn(function_call, replayed_input)
        tool_outputs = [
            item for item in replayed_input
            if item.get("type") == "function_call_output"
        ]
        self.assertEqual(tool_outputs[0]["call_id"], "call-1")
        self.assertEqual(len(result.traces), 1)
        self.assertEqual(result.usage.inputTokens, 25)
        self.assertEqual(result.usage.outputTokens, 9)

    async def test_multiple_tool_rounds_are_replayed(self):
        first_call = {
            "type": "function_call",
            "call_id": "call-1",
            "name": "search_commodities",
            "arguments": '{"keywords":["电脑"]}',
        }
        second_call = {
            "type": "function_call",
            "call_id": "call-2",
            "name": "search_commodities",
            "arguments": '{"keywords":["笔记本"],"maxPrice":3000}',
        }
        openai_client = StubOpenAIClient([
            {"output": [first_call], "usage": {}},
            {"output": [second_call], "usage": {}},
            {
                "output": [
                    structured_output_message(
                        answer="已完成两轮筛选。",
                        commodity_ids=["1001"],
                    )
                ],
                "usage": {},
            },
        ])
        java_client = StubJavaBackendClient()
        service = AgentService(
            make_settings(),
            openai_client=openai_client,
            java_backend_client=java_client,
        )

        result = await service.run("request-1", self.make_request())

        self.assertEqual(result.answer, "已完成两轮筛选。")
        self.assertEqual(len(java_client.calls), 2)
        final_input = openai_client.calls[2]["input_items"]
        self.assertEqual(
            [
                item["call_id"] for item in final_input
                if item.get("type") == "function_call_output"
            ],
            ["call-1", "call-2"],
        )

    async def test_invalid_tool_and_round_limit_are_rejected(self):
        invalid_tool_client = StubOpenAIClient([{
            "output": [{
                "type": "function_call",
                "call_id": "call-1",
                "name": "delete_commodity",
                "arguments": "{}",
            }],
            "usage": {},
        }])
        service = AgentService(
            make_settings(),
            openai_client=invalid_tool_client,
            java_backend_client=StubJavaBackendClient(),
        )
        with self.assertRaises(AgentServiceError) as unsupported:
            await service.run("request-1", self.make_request())
        self.assertEqual(
            unsupported.exception.agent_error_key,
            "AI_TOOL_NOT_SUPPORTED",
        )

        limited_client = StubOpenAIClient([{
            "output": [{
                "type": "function_call",
                "call_id": "call-1",
                "name": "search_commodities",
                "arguments": "{}",
            }],
            "usage": {},
        }])
        limited_service = AgentService(
            make_settings(max_tool_rounds=0),
            openai_client=limited_client,
            java_backend_client=StubJavaBackendClient(),
        )
        with self.assertRaises(AgentServiceError) as limited:
            await limited_service.run("request-1", self.make_request())
        self.assertEqual(
            limited.exception.agent_error_key,
            "AI_TOOL_ROUNDS_EXCEEDED",
        )

    async def test_invalid_arguments_and_output_are_rejected(self):
        invalid_args_client = StubOpenAIClient([{
            "output": [{
                "type": "function_call",
                "call_id": "call-1",
                "name": "search_commodities",
                "arguments": "{",
            }],
            "usage": {},
        }])
        service = AgentService(
            make_settings(),
            openai_client=invalid_args_client,
            java_backend_client=StubJavaBackendClient(),
        )
        with self.assertRaises(AgentServiceError) as invalid_args:
            await service.run("request-1", self.make_request())
        self.assertEqual(
            invalid_args.exception.agent_error_key,
            "AI_TOOL_ARGUMENTS_INVALID",
        )

        invalid_output_client = StubOpenAIClient([{
            "output": None,
            "usage": {},
        }])
        invalid_output_service = AgentService(
            make_settings(),
            openai_client=invalid_output_client,
            java_backend_client=StubJavaBackendClient(),
        )
        with self.assertRaises(AgentServiceError) as invalid_output:
            await invalid_output_service.run("request-1", self.make_request())
        self.assertEqual(
            invalid_output.exception.agent_error_key,
            "AI_MODEL_RESPONSE_INVALID",
        )

        empty_answer_client = StubOpenAIClient([{
            "output": [{
                "type": "message",
                "content": [{"type": "output_text", "text": "  "}],
            }],
            "usage": {},
        }])
        empty_answer_service = AgentService(
            make_settings(),
            openai_client=empty_answer_client,
            java_backend_client=StubJavaBackendClient(),
        )
        with self.assertRaises(AgentServiceError) as empty_answer:
            await empty_answer_service.run("request-1", self.make_request())
        self.assertEqual(
            empty_answer.exception.agent_error_key,
            "AI_MODEL_RESPONSE_INVALID",
        )

    async def test_rejects_recommendation_not_returned_by_tool(self):
        function_call = {
            "type": "function_call",
            "call_id": "call-1",
            "name": "search_commodities",
            "arguments": "{}",
        }
        openai_client = StubOpenAIClient([
            {"output": [function_call], "usage": {}},
            {
                "output": [
                    structured_output_message(
                        commodity_ids=["9999"],
                    )
                ],
                "usage": {},
            },
        ])
        service = AgentService(
            make_settings(),
            openai_client=openai_client,
            java_backend_client=StubJavaBackendClient(),
        )

        with self.assertRaises(AgentServiceError) as raised:
            await service.run("request-1", self.make_request())

        self.assertEqual(
            raised.exception.agent_error_key,
            "AI_MODEL_RESPONSE_INVALID",
        )
        self.assertFalse(raised.exception.retryable)

    async def test_model_refusal_is_mapped(self):
        openai_client = StubOpenAIClient([{
            "output": [{
                "type": "message",
                "content": [{
                    "type": "refusal",
                    "refusal": "无法处理当前请求",
                }],
            }],
            "usage": {},
        }])
        service = AgentService(
            make_settings(),
            openai_client=openai_client,
            java_backend_client=StubJavaBackendClient(),
        )

        with self.assertRaises(AgentServiceError) as raised:
            await service.run("request-1", self.make_request())

        self.assertEqual(
            raised.exception.agent_error_key,
            "AI_MODEL_REFUSED",
        )
        self.assertFalse(raised.exception.retryable)


class ShoppingGuidePromptTests(unittest.TestCase):
    def test_system_prompt_defines_out_of_scope_behavior(self):
        request = AgentRunRequest(
            userId=1,
            conversationId=2,
            message="在 Python 中 Literal 是什么意思",
        )

        system_prompt = build_messages(request)[0]["content"]

        self.assertIn("编程教学、数学、翻译、写作、新闻和闲聊", system_prompt)
        self.assertIn("完全超出范围时，只回复", system_prompt)
        self.assertIn("回复后立即结束，不调用商品搜索工具", system_prompt)
        self.assertIn("混合问题只回答其中与二手购买或交易相关的部分", system_prompt)
        self.assertIn("answer 面向用户并包含完整回答", system_prompt)
        self.assertIn("summary 只概括本轮结论", system_prompt)
        self.assertIn(
            "memorySummary 只保留对后续对话有用的累计事实",
            system_prompt,
        )

    def test_build_messages_preserves_context_history_and_current_turn_order(self):
        request = AgentRunRequest(
            userId=1,
            conversationId=2,
            message="再多找找，可以放宽一点要求",
            shoppingContext={
                "budgetMax": 100,
                "usageScene": "寝室",
            },
            memorySummary="用户想找寝室可用的生活用品",
            history=[
                {
                    "role": "USER",
                    "content": "我想看看100元内的生活用品",
                },
                {
                    "role": "ASSISTANT",
                    "content": "当前找到风扇和台灯。",
                },
            ],
        )

        messages = build_messages(request)

        self.assertEqual(
            [message["role"] for message in messages],
            ["system", "system", "system", "user", "assistant", "user"],
        )
        self.assertIn("根据问题复杂度调整详略", messages[0]["content"])
        self.assertIn("新候选优先", messages[0]["content"])
        self.assertEqual(
            messages[1]["content"],
            '当前购买条件：{"budgetMax":100.0,"usageScene":"寝室",'
            '"preferenceTags":[],"avoidances":[]}',
        )
        self.assertEqual(
            messages[2]["content"],
            "较早对话摘要：用户想找寝室可用的生活用品",
        )
        self.assertEqual(messages[-1]["content"], request.message)

    def test_history_rejects_invalid_role_and_more_than_ten_messages(self):
        with self.assertRaises(ValidationError):
            AgentRunRequest(
                userId=1,
                conversationId=2,
                message="继续推荐",
                history=[{
                    "role": "SYSTEM",
                    "content": "非法历史消息",
                }],
            )

        history = [
            {
                "role": "USER" if index % 2 == 0 else "ASSISTANT",
                "content": f"历史消息 {index}",
            }
            for index in range(11)
        ]
        with self.assertRaises(ValidationError):
            AgentRunRequest(
                userId=1,
                conversationId=2,
                message="继续推荐",
                history=history,
            )


class HealthTests(unittest.IsolatedAsyncioTestCase):
    async def test_health_reflects_model_and_internal_token_configuration(self):
        original_settings = health_module.settings
        try:
            health_module.settings = make_settings()
            self.assertEqual((await health_module.health())["status"], "UP")

            health_module.settings = make_settings(openai_api_key="")
            self.assertEqual((await health_module.health())["status"], "DEGRADED")

            health_module.settings = make_settings(internal_token="")
            self.assertEqual((await health_module.health())["status"], "DEGRADED")
        finally:
            health_module.settings = original_settings


if __name__ == "__main__":
    unittest.main()
