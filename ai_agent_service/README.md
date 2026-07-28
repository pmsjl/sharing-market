# AI Agent Service

第一阶段 Python Agent 服务。

- 启动入口：`app.main:app`
- 健康检查：`GET /health`
- Java 内部调用：`POST /agent/v1/runs`
- 当前能力：通过 OpenAI Responses 兼容中转调用 `gpt-5.6-terra`，并执行商品搜索工具后返回同步导购建议
- 当前不包含：Java 商品工具调用、RAG、向量库与知识库索引

运行配置从环境变量读取；可参考 `.env.example`。`OPENAI_BASE_URL`
必须包含中转服务的 `/v1` 前缀，真实 API Key 不得提交到仓库。
