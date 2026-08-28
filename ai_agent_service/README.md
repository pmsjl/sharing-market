# AI Agent Service

当前 Python Agent 与 GUIDE + Post RAG 服务（状态更新：2026-08-23）。

- 启动入口：`app.main:app`
- 健康检查：`GET /health`
- Java 内部调用：`POST /agent/v1/runs`
- 当前能力：通过 OpenAI Responses 兼容中转调用 `gpt-5.6-terra`，使用 Structured Outputs、滚动会话摘要、商品搜索与当前用户脱敏偏好工具返回同步导购建议
- 当前路由：高精度 Guardrail → Strict Structured Output LLM Intent Router → 确定性 Orchestrator；LLM 只输出 disposition、商品动作、知识域和偏好模式，程序在 Embedding 前派生 `retrieve / clarify / out_of_scope / skip_rag` 及工具策略，业务操作边界使用内部 `capability_redirect`
- 当前工具：仅开放商品搜索和当前用户脱敏偏好；两个工具分别使用 required/optional/forbidden，订单、退款、投诉和举报没有 AI 工具
- 当前 RAG：非课程问题沿用既有类别；具体课程按 A（精确课程父文档）+ B（统一购买政策固定槽）+ C（最多两个通用 GUIDE Chunk）检索，并为经验 Post 保留独立的 3 个位置，向生成阶段注入课程证据状态
- 当前后置项：SSE、管理员 knowledge job、文档 upsert/delete 和独立 retrieve HTTP API
- 自动化基线：Python `187 passed` 加 `27 subtests`；Golden v1.1 全量 200 条和新增 40 条长尾路由集 Route accuracy 均为 `100%`
- P0 代码导读：[`../docs/AI_AGENT_P0_CODE_ANALYSIS.md`](../docs/AI_AGENT_P0_CODE_ANALYSIS.md)

运行配置从环境变量读取；可参考 `.env.example`。`OPENAI_BASE_URL`
必须包含中转服务的 `/v1` 前缀，真实 API Key 不得提交到仓库。

`OPENAI_REASONING_EFFORT` 控制模型分析和工具决策所投入的推理强度，
默认使用 `medium`；`OPENAI_TEXT_VERBOSITY` 控制最终回答的默认详细程度，
可选 `low`、`medium`、`high`，当前导购场景默认使用 `medium`。
回答仍会根据用户是否要求“详细一点”“怎么选”或“再多找找”动态调整，
因此 verbosity 不等同于固定回答长度。

## Intent Router 配置

混合式 Router 默认启用。Guardrail 只处理空消息、明确要求代办的受限业务操作
和当前个人订单数据查询；普通澄清与范围判断交给独立的结构化模型，并同时识别实时搜索、推荐、偏好、
需要查询哪些资料以及课程问题类型。最终 route 和工具策略由程序派生；Router 不暴露任何
工具，也不能选择具体知识文档。

`OPENAI_ROUTER_MODEL` 默认与 `OPENAI_MODEL` 相同，但可单独替换为兼容
Strict JSON Schema 的模型。默认使用 `low` 推理强度、`low` 详细度和 45 秒
超时。置信度低于 `INTENT_ROUTER_CONFIDENCE_THRESHOLD`、结构非法、模型超时
或策略冲突时，系统自动进入保守降级：不猜测澄清或范围外结论，只保留明确商品搜索和偏好读取规则，其他请求继续检索和回答。

紧急情况下可设置 `INTENT_ROUTER_ENABLED=false` 完全关闭 LLM Router；
Guardrail 和保守降级路径仍然有效。


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

## Public RAG Evaluation

仓库公开脱敏后的 Golden v1.1 Dev 分区，共 140 条单轮 Case。
独立的 60 条 Test、原始模型输出、Trace、用量和人工复核记录不在
Public 仓库中发布。

从仓库根目录验证公开 Dataset、Schema 和 Manifest Hash：

```powershell
python tools/validate_public_evaluation.py
```

数据结构、标注语义、聚合基线与公开范围见 `evaluation/README.md`。
