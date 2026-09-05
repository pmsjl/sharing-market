# AI Agent Service

`ai_agent_service` 是[智能 AI 校园二手交易平台](../README.md)的 **Python Agent 与 GUIDE + Post RAG 服务**。Java 通过内部 Token 调用该服务，浏览器不应直接访问它。

## 系统定位

```
Java Backend ──POST /agent/v1/runs──→  FastAPI Agent
         (X-Internal-Token)               │
                                          ├─ Router: HybridQueryRouter（Guardrail + LLM 意图路由）
                                          ├─ RAG:   FAISS 检索 GUIDE + Post
                                          ├─ Tools: 商品搜索 / 用户脱敏偏好（经 Java 只读内部接口）
                                          └─ LLM:   OpenAI-compatible Responses / Embedding API
```

- Java 负责会话流程、鉴权以及商品和 Post 的有效性校验；Python 只负责**模型调用流程与 RAG**。
- Python 不直接连接业务 MySQL，商品/偏好/Post 数据全部通过 Java 内部只读接口获取。
- 一次 `/agent/v1/runs` 是**同步**调用，返回结构化 JSON（回答 + 引用 + 工具调用记录）。

## 服务接口

| 接口 | 用途 |
| --- | --- |
| `POST /agent/v1/runs` | 接收 Java 整理好的会话上下文，返回同步导购结果 |
| `GET /live` | 进程存活检查 |
| `GET /ready` | 必要配置和可选 RAG 索引的就绪检查 |
| `GET /health` | 不包含密钥的运行状态摘要 |

Java 与 Python 必须配置相同的 `AI_AGENT_INTERNAL_TOKEN`。

## 一次导购请求的内部流程

```
AgentRunRequest(会话 + 工具结果)
   │
   ▼
① HybridQueryRouter.resolve()
   ├─ 安全规则（Guardrail）：拦截退款/订单/举报等"写操作"，改为说明系统能力边界并引导用户使用现有功能
   └─ LLM 意图路由：retrieve（走 RAG）/ skip_rag / clarify / out_of_scope / capability_redirect
   │
   ▼ retrieve 时
② RAG 检索
   ├─ CourseRelationIndex：课程关系匹配
   └─ Retriever：使用指定版本的 FAISS 索引检索 GUIDE 文档 + Post
   │
   ▼
③ AgentService 生成
   ├─ SYSTEM_PROMPT（购物导购）+ 检索上下文
   ├─ 工具调用：CommoditySearchTool / UserPreferenceTool（只读）
   └─ 输出：answer + structuredContent（recommendations/relatedPosts/sources）
   │
   ▼
④ 返回 AgentRunResponse → Java 校验商品和 Post 后整理为前端需要的格式
```

## 当前能力

- 识别商品搜索、推荐、平台知识、课程资料和用户偏好需求。
- 调用 Java 提供的商品搜索和当前用户脱敏偏好两个只读工具。
- 使用 OpenAI-compatible Responses API 生成回答，结构化输出（意图/知识点/引用）。
- 使用独立 Embedding 接口和 FAISS 检索 GUIDE 文档与社区 Post。
- 校验 Post 当前版本，并在检索或外部服务异常时降级。

订单、退款、投诉和举报等写操作没有 AI 工具。

## 源码结构

```
ai_agent_service/
├── app/
│   ├── main.py / __main__.py / container.py   启动与依赖注入容器
│   ├── api/                路由（/agent/v1/runs 等）
│   ├── routing/
│   │   └── query_router.py HybridQueryRouter（Guardrail + LLM 意图路由）
│   ├── rag/
│   │   ├── index_store.py  FAISS 索引加载与构建入口（rebuild_index）
│   │   ├── retriever.py    向量检索
│   │   ├── embedding_client.py  Embedding 客户端
│   │   ├── course_relations.py  课程关系索引
│   │   └── service.py      检索与证据状态解析
│   ├── services/
│   │   └── agent_service.py    Agent 主流程（生成/工具/重试）
│   ├── prompts/
│   │   └── shopping_guide.py   导购系统提示词
│   ├── models/             请求/响应/工具 Schema（Pydantic）
│   ├── clients/
│   │   └── openai_responses.py  OpenAI-compatible 客户端
│   ├── tools/              商品搜索、用户偏好等只读工具实现
│   └── core/config.py      配置（.env 绑定）
├── knowledge/
│   ├── documents/effective/  当前使用的 GUIDE 文档
│   └── runtime/              程序读取的文档信息与课程关系
└── evaluation/             公开评测集与评测脚本
```

## 本地运行

要求 Python 3.11。以下命令以 Windows PowerShell 为例：

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
# 编辑 .env 后启动服务。
python -m uvicorn app.main:app --host 0.0.0.0 --port 8103
```

本地默认端口为 `8103`。真实 API Key 只能写入已忽略的 `.env` 或系统环境变量。

## 配置

完整模板见 [`.env.example`](.env.example)。

| 配置组 | 主要变量 | 说明 |
| --- | --- | --- |
| Java 内部调用 | `AI_AGENT_INTERNAL_TOKEN`、`JAVA_BACKEND_BASE_URL`、`JAVA_BACKEND_TIMEOUT_SECONDS` | Token 必须与 Java 一致 |
| 生成模型 | `OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL` | 导购回答用 |
| Embedding | `EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、模型、维度和批大小 | RAG 检索用 |
| RAG | `RAG_ENABLED`、`RAG_INDEX_DIR`、GUIDE/Post Top K 与阈值 | 检索参数 |

`JAVA_BACKEND_BASE_URL` 填写 Java 服务根地址，**不要包含 `/api`**。生成模型和 Embedding 可使用不同的兼容服务，其 Base URL 应填写到 `/v1`。

## RAG 索引

配置 Embedding 服务后执行：

```powershell
python -m app.rag.rebuild_index
```

- 构建过程读取 `knowledge/documents/effective/` 中的 GUIDE 文档，以及 `knowledge/runtime/` 中的文档信息和课程关系；社区 Post 则通过 Java 获取。
- 每次构建都会生成一个新的 FAISS 索引版本，并分配唯一的 `indexBuildId`；构建完成的版本不会在原地修改，后续请求会加载新版本。
- 未准备索引时不要启用 `RAG_ENABLED=true`。
- GUIDE 文档、`runtime/` 中的运行数据、Embedding 模型或向量维度发生变化后，必须重建索引。

### GUIDE 与 Post

| 来源 | 特点 |
| --- | --- |
| GUIDE 文档 | 平台和学校的稳定资料，保存在 `knowledge/documents/effective/` 下 |
| 社区 Post | 动态内容，不直接保存在知识库目录中；构建索引时通过 Java 获取当前数据，生成回答前仍需校验版本 |

公开仓库只保留运行和重建索引所需的 GUIDE 文档与 `runtime/*.jsonl`。知识采集过程、草稿、来源核对材料、中间文件和检查报告由 `.gitignore` 排除。

详细说明见 [knowledge/README.md](knowledge/README.md)。

## 评测

`evaluation/` 是 Golden Test 评测体系：使用固定题目集，按 Router → Retrieval → Generation → Judge → Final 五个阶段进行端到端回归评测，用于检查改动是否影响 AI 导购质量。

- 代码全部在 `evaluation/tools/`；不随仓库发布的完整题目集在 `evaluation/dataset/`；脱敏后的公开评测集在 `evaluation/public/`。
- 推荐阅读 [evaluation/README.md](evaluation/README.md) 获取完整评测指南。

```powershell
# 校验公开评测包（不调用模型）
python ai_agent_service/evaluation/tools/validate_public_evaluation.py

# 跑评测（需配置好的 .env；示例为 5 问子集）
python ai_agent_service/evaluation/tools/run_golden_pipeline.py `
  --dataset <dataset.jsonl> --manifest <manifest.json> --run-name <run> --through final
```

## 依赖与测试

- `requirements.txt`：运行服务所需依赖。
- `requirements-dev.txt`：在运行依赖之上增加 pytest 和数据校验依赖。

```powershell
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

评测工具相关测试集中在 `tests/test_golden_*`、`tests/test_public_evaluation.py` 等。README 不固定测试通过数量，以当前命令输出为准。

## License

本模块中由 pmsjl 持有版权的内容采用根目录的 [MIT License](../LICENSE)。知识数据中的外部来源仍受其各自条款约束。
