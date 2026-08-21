# AI Agent Service

当前 Python Agent 与 GUIDE + Post RAG 服务（状态更新：2026-08-19）。

- 启动入口：`app.main:app`
- 健康检查：`GET /health`
- Java 内部调用：`POST /agent/v1/runs`
- 当前能力：通过 OpenAI Responses 兼容中转调用 `gpt-5.6-terra`，使用 Structured Outputs、滚动会话摘要、商品搜索与当前用户脱敏偏好工具返回同步导购建议
- 当前 RAG：GUIDE 与社区 Post 独立配额检索、不可变 FAISS 索引、`CURRENT` 热加载、请求内 Post 版本校验、引用来源和相关帖子卡片，已接入 Agent 主调用链
- 当前后置项：SSE、管理员 knowledge job、文档 upsert/delete 和独立 retrieve HTTP API
- 自动化基线：`109 passed`、`23 subtests passed`；在受限工作区可能仅出现 `.pytest_cache` 不可写警告

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

## Retrieval Golden Dataset v1

第一版检索真值集位于 `evaluation/golden/golden_dataset_v1.jsonl`，包含
200 条单轮 Case、238 个分级 qrel，以及按文档/场景分组隔离的 140 条 Dev
和 60 条 Test。它覆盖平台规则、课程资料、校园指南、260 篇 Post 和无证据、
澄清、实时工具边界。

从仓库根目录重建固定资产：

```powershell
conda run -n fastapi python tools/build_golden_dataset_v1.py
```

只验证现有 Dataset、Manifest Hash、当前索引文档兼容性和分层门禁：

```powershell
conda run -n fastapi python tools/build_golden_dataset_v1.py --check-only
```

数据结构、标注规则、已知限制和后续版本规则见 `evaluation/README.md`。
独立人工复核完成前，不得把该数据集后续跑出的指标作为对外简历数字。
