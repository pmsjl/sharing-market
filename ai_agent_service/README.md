# AI Agent Service

第一阶段 Python Agent 服务。

- 启动入口：`app.main:app`
- 健康检查：`GET /health`
- Java 内部调用：`POST /agent/v1/runs`
- 当前能力：通过 OpenAI Responses 兼容中转调用 `gpt-5.6-terra`，并执行商品搜索工具后返回同步导购建议
- 当前 RAG：GUIDE 与社区 Post 独立配额检索、不可变 FAISS 索引、`CURRENT` 热加载、请求内 Post 版本校验、引用来源和相关帖子卡片，已接入 Agent 主调用链

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

## Java Post 快照

离线 RAG 重建通过 `JavaBackendClient.fetch_post_snapshot()` 分页读取
`GET /api/internal/ai/rag/posts`。`RAG_POST_SNAPSHOT_PAGE_SIZE` 允许 `1..200`，
默认 `200`。客户端只发送 `X-Internal-Token`，并会在重复 ID、游标停滞、
页内乱序、ID 越过游标窗口或响应结构错误时终止构建。

重建会把 GUIDE 与 Post 合并为一个不可变版本；Java 快照或 Embedding 任一失败时不会切换 `CURRENT`。成功后下一条 Agent 请求自动热加载，无需重启 Python 服务。Post 在进入模型前还会调用 `POST /api/internal/ai/tools/posts/validate` 核对当前版本，校验失败时只丢弃 Post，GUIDE 和普通 Agent 继续工作。

`GET /health` 会分别返回 `ragEnabled`、`ragReady`、`ragBuildId`、GUIDE/Post 文档数、Post 快照时间和最近一次热加载错误。
