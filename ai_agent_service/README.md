# AI Agent Service

第一阶段 Python Agent 服务。

- 启动入口：`app.main:app`
- 健康检查：`GET /health`
- Java 内部调用：`POST /agent/v1/runs`
- 当前能力：通过 OpenAI Responses 兼容中转调用 `gpt-5.6-terra`，并执行商品搜索工具后返回同步导购建议
- 当前 RAG 基础层：有效语料加载、课程关系规划、OpenAI 兼容 Embedding、版本化 FAISS 索引和 Retriever；尚未接入 Agent 主调用链

运行配置从环境变量读取；可参考 `.env.example`。`OPENAI_BASE_URL`
必须包含中转服务的 `/v1` 前缀，真实 API Key 不得提交到仓库。

`OPENAI_REASONING_EFFORT` 控制模型分析和工具决策所投入的推理强度，
默认使用 `medium`；`OPENAI_TEXT_VERBOSITY` 控制最终回答的默认详细程度，
可选 `low`、`medium`、`high`，当前导购场景默认使用 `medium`。
回答仍会根据用户是否要求“详细一点”“怎么选”或“再多找找”动态调整，
因此 verbosity 不等同于固定回答长度。


## Embedding 配置

生成模型与 Embedding 可以使用不同服务。程序会固定读取本目录下未跟踪的 `.env`，系统环境变量优先于文件内容。参考 `.env.example` 配置 `EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL`、`EMBEDDING_DIMENSIONS` 和 `EMBEDDING_BATCH_SIZE`。

`EMBEDDING_BASE_URL` 只填写 OpenAI 兼容接口的 `/v1` 根路径，客户端自动追加 `/embeddings`。批大小必须不超过所选模型限制。模型、维度或服务发生变化后，必须重新执行：

```powershell
conda run -n fastapi python -m app.rag.rebuild_index
```

真实 API Key 只能保存在本地 `.env` 或系统环境变量中，不得写入 `.env.example`、Markdown 指南或 Git。
