# Golden Test 评估体系（Public RAG Evaluation）

本目录保存 [AI Agent Service](../README.md) 的 **Golden Test 评测体系**：一个围绕"意图路由 + GUIDE/Post 检索 + 答案生成 + 自动裁判"四阶段的端到端回归评测，以及可公开、可复现的 Dev 评测包。

## 为什么需要它

平台 AI 导购依赖 LLM 路由、向量检索与生成，单点改动（Prompt、路由阈值、索引重建、Embedding 模型）都可能悄悄改变回答质量。Golden Test 用**人工评审过的固定题目集**做全链路回归：每次改动后跑同一批题，对比各阶段结果，回答"这次改动有没有让系统变好或变坏"。

评测目标：
- **可复现**：同一题目集 + 同一冻结索引 + 同一脚本，跑出的结果可对比。
- **分阶段定位**：Router、Retrieval、Generation、Judge 各阶段独立产出，出问题能定位到环节。
- **承认非确定性**：生成与裁判结果存在模型波动，对照时区分"契约字段"（路由、状态、PASS/FAIL）与"逐字输出"。

## 目录结构

```
evaluation/
├── dataset/            ★ 私有：完整 Golden 题目集（200 题）+ Manifest（Git 忽略，不提交）
├── public/             公开：脱敏后的 Dev 140 题包 + Manifest + 标注规范 + 基准摘要
├── schemas/            Case 的 JSON Schema
├── tools/              全部评测脚本（编排入口 + 4 个执行器 + 对照器 + 校验器）
├── runs/               本地运行产物（结果/报告/对照报告，Git 忽略）
├── human_review/       人工评审材料（Git 忽略）
└── README.md
```

| 子目录 | 是否进 Git | 说明 |
| --- | --- | --- |
| `public/`、`schemas/`、`tools/`、`README.md` | 是 | 代码与公开数据 |
| `dataset/`、`runs/`、`human_review/` | 否（忽略） | 私有题目集、运行产物、评审材料 |

## 数据集

评测数据经历"人工编写 → 人工评审 → 物化冻结"三步，最终产物是 `dataset/` 下的完整集与 `public/` 下的脱敏子集。

### 完整集（私有，`dataset/golden_v1_2_1_reviewed_200.jsonl`）

当前冻结版本 **golden-v1.2.1-reviewed-20260829**，共 **200 题**：

| 领域 | 题数 | 覆盖内容 |
| --- | ---: | --- |
| course（课程资料） | 70 | 教材版本、课件获取、课程决策 |
| post（二手商品） | 50 | 各类商品购买/转卖决策 |
| platform（平台规则） | 40 | 账号、交易规则、边界 |
| boundary（合规边界） | 20 | 能否上架、正品、禁售 |
| campus（校园生活） | 20 | 宿舍电器、生活决策 |

- **split**：dev 140（开发调试用）+ test 60（扣留考核集，模型开发不接触）。
- 每条含 `expectedRoute`（retrieve/clarify/out_of_scope/skip_rag）、`expectedKnowledgeState`、`expectedFacts`、`qrels`（文档级相关性标注）、`provenance`、`review`（评审记录）等字段。
- **数据集本身不进 Git**：含人工评审记录，属私有资产。仓库只提交执行代码，完整评测需调用方自备 Dataset 与 Manifest。

### 公开集（`public/dev_v1_2_1.jsonl`）

从完整集 dev 140 脱敏后发布：剥离评审记录、内部构造字段与扣留的 test 60。

- 140 题、172 条 qrels。
- 准确版本、数量、Hash 以 `public/manifest.json` 为准。
- 用途：开发调试、数据格式验证、公开可复现基线。

### 完整集 ↔ 公开集

| | 完整集（私有） | 公开集（public/） |
| --- | --- | --- |
| 题数 | 200（dev 140 + test 60） | 140（仅 dev） |
| review 评审记录 | 有 | 剥离，仅留 `{"status":"frozen"}` |
| provenance | 含内部构造细节 | 仅留 `source` |
| 索引绑定 | Manifest 记录 `indexBuildIdAtFreeze` | Manifest 记录 `indexSnapshot` |

## 评测流程（五阶段）

```
        ┌──────────────────────────────────────────────────────────┐
        │            run_golden_pipeline.py（编排入口）             │
        │  选 Case → 绑定索引 → 依序调 4 个执行器 → 合并结果        │
        └──────────────────────────────────────────────────────────┘
  ① Router              ② Retrieval          ③ Generation
  HybridQueryRouter      Retriever             AgentService
  意图路由 + Guardrail   向量检索 GUIDE/Post    生成带引用答案
        │                    │                     │
        ▼                    ▼                     ▼
   pipeline_router.jsonl   pipeline_retrieval_*.jsonl  pipeline_answer_generation.jsonl
        │                    │                     │
        ▼                    ▼                     ▼
  ④ Judge               ⑤ Final
  自动裁判 v2           合并结果判定
  (单 Case 逐条)          (PASS/FAIL + 按领域汇总)
```

| 阶段 | 执行器脚本（`tools/` 下） | 输入 | 输出 | 检查什么 |
| --- | --- | --- | --- | --- |
| ① Router | `run_golden_v1_1_router_eval.py` | Case 的 query/history | `pipeline_router.jsonl` | 路由是否命中 `expectedRoute` |
| ② Retrieval | `run_golden_v1_1_retrieval_eval.py` | query + 冻结索引 | `pipeline_retrieval_<build>.jsonl` | Recall@k、MRR、qrel 命中 |
| ③ Generation | `run_golden_v1_1_answer_generation.py` | Router 结果 + 检索结果 | `pipeline_answer_generation.jsonl` | 答案生成成功、引用完整 |
| ④ Judge | `run_golden_v1_1_answer_judge.py` | 生成结果 + 期望 | `pipeline_answer_judgments.jsonl` | 答案是否 PASS、知识状态是否正确 |
| ⑤ Final | `build_golden_v1_2_single_v2_final_results.py` | Generation + Judge | `pipeline_final_results.jsonl` + `_manifest.json` | 汇总 PASS/FAIL、按领域统计 |

### 共享库

- `golden_v1_1_round2_paths.py`：统一计算 runs 目录、结果/报告路径。
- `course_question_quality.py`：课程题的质量校验与元数据。
- `golden_current_runtime_expectations.py`：当前运行时 truth 覆盖（如学校固定不追问）。
- `materialize_golden_v1_2_reviewed.py`：一次性物化工具（v1.1 → v1.2.1，源数据已清理，仅供历史参考）。

### 关键约定

- **索引冻结**：每个 Manifest 绑定一个 `indexBuildIdAtFreeze`。评测必须用与题目冻结时一致的索引，否则对比失真。
- **单 Case Judge**：多 Case 批量裁判已实测发生跨题答案串读，故当前每次请求只评 1 个 Case，保证评分绑定可靠。
- **工作目录**：执行器以 `ai_agent_service/` 为工作目录运行（`.env` 中 `RAG_INDEX_DIR` 是相对路径）。

## 数据与索引的关系

GUIDE 文档（`knowledge/`）与 Post 快照（经 Java 获取）先构建 FAISS 索引，Router/Retrieval 再在其上工作：

```
knowledge/ GUIDE 文档 ─┐
                       ├─→ rebuild_index → FAISS 索引（含 indexBuildId）
market_backend Post 快照 ┘                          │
                                                   ▼
                  Dataset Manifest 记录冻结的 indexBuildId
                            │  评测时校验
                            ▼
        run_golden_pipeline.py → Retrieval 用该索引检索
```

改动 GUIDE 知识、Embedding 模型或向量维度后**必须重建索引**（`python -m app.rag.rebuild_index`），并用新索引跑评测。

## 运行评测

### 环境

评测需要调用真实 LLM 与 Embedding 服务，因此在配置好的 `ai_agent_service/` 环境（含 `.env`）中运行。本机评测环境为 conda 的 `fastapi` 环境。

### 完整 200 题

```powershell
# 从仓库根目录，用私有 Dataset + Manifest
<python> ai_agent_service\evaluation\tools\run_golden_pipeline.py `
  --dataset ai_agent_service\evaluation\dataset\golden_v1_2_1_reviewed_200.jsonl `
  --manifest ai_agent_service\evaluation\dataset\golden_v1_2_1_reviewed_200_manifest.json `
  --run-name <run-name> `
  --through final
```

### 选子集（调试 / 回归，不跑全量）

```powershell
<python> ai_agent_service\evaluation\tools\run_golden_pipeline.py `
  --dataset <dataset.jsonl> --manifest <manifest.json> `
  --run-name smoke_5_20260902 `
  --case-id boundary-01-official-opened-cosmetics `
  --case-id campus-campus-dorm-appliance-rules-01 `
  --case-id course-material_mention-001 `
  --case-id platform-account-and-identity-01 `
  --case-id post-legacy-001 `
  --through final
```

- `--case-id` 可多次传，或 `--limit N` 取前 N 题。
- `--through prepare` 只做 Case 选择与 Manifest 绑定，**不调用模型**，用于快速检查输入合法性。
- 5 问对照常按五个领域各取 1 题，其中 4 题选自扣留的 test 60（holdout），避免"背题"干扰。

### 索引不可用时的覆盖

源 Manifest 绑定的索引不在本地时，必须显式传可用索引：

```powershell
--index-build-id <可用-build-id>
```

原绑定与本次覆盖都会写入运行 Manifest（`pipelineSelection.sourceIndexBuildIdAtFreeze` / `indexBuildOverride`）。

## 运行产物（`runs/<run-name>/`）

```
runs/<run-name>/
├── PIPELINE_MANIFEST.json     运行总清单（Case、索引、命令、各阶段脚本哈希）
├── input/
│   ├── selected_cases.jsonl   实际评测的子集
│   └── selected_manifest.json 绑定的 Manifest
├── results/
│   ├── pipeline_router.jsonl
│   ├── pipeline_retrieval_<build>.jsonl
│   ├── pipeline_answer_generation.jsonl
│   ├── pipeline_answer_judgments.jsonl
│   └── pipeline_final_results.jsonl + _manifest.json
└── reports/                   各阶段 summary / badcases
```

`PIPELINE_MANIFEST.json` 记录 `implementationSha256`（各脚本哈希），两次运行据此可确认是否使用了**同一版代码**——这是"改动未触碰主体逻辑"的可验证证据。

## 对照两次运行

用 `compare_golden_runs.py` 对比两次运行（基线 vs 候选），逐阶段比较"契约字段"：

```powershell
<python> ai_agent_service\evaluation\tools\compare_golden_runs.py `
  --baseline-manifest <baseline-run>\PIPELINE_MANIFEST.json `
  --candidate-manifest <candidate-run>\PIPELINE_MANIFEST.json `
  --output <comparison.json>
```

对照维度：

| 阶段 | 契约字段（判定等价用） |
| --- | --- |
| Router | 路由、决策来源 |
| Retrieval | 路由、知识状态 |
| Generation | 成功状态、Intent |
| Judge | PASS、关键错误、知识状态 |

报告同时给出：
- `sameImplementation`：两次运行的脚本哈希是否一致。
- `sameSelectedCaseIds`、`allStagesHaveSameCaseSet`：是否同一批题。
- 各阶段 `contractEquivalentCaseCount`（契约等价）与 `equivalentCaseCount`（逐字一致）。
- 明确标注 `modelOutputsMayBeNondeterministic`：文本波动是模型特性，不等同于逻辑回归。

## 公开评测包

`public/` 是可公开、可复现的 Dev 包，用于数据格式、Schema、公开基线校验，不依赖模型。

### 完整性验证

```powershell
<python> ai_agent_service\evaluation\tools\validate_public_evaluation.py
```

验证器检查：140 题、字段 Schema 合规、review/provenance 脱敏、Manifest 统计与 Hash 一致。不调用模型。

### 公开边界

- 公开：脱敏 Dev 数据、Schema、Manifest、标注规范、聚合基线摘要。
- 不公开：test 60（扣留考核集）、模型原始输出、请求 Trace、标识符、用量记录、人工评审记录、私有 Dataset。

### 公开基线

`public/benchmark_summary.md` 给出完整冻结集的聚合指标（如 Router 94%、Recall@5 92.44%、Final 95%）。注意：Public Dev 一旦用于实现调整或参数选择，就不能再表述为"未见过的隐藏测试集"。

## 测试

评测工具自身的回归测试在 `ai_agent_service/tests/`：

```powershell
cd ai_agent_service
<python> -m pytest -q tests/test_golden_pipeline_tools.py `
  tests/test_public_evaluation.py `
  tests/test_query_router.py
```

- `test_golden_pipeline_tools.py`：编排入口与对照器的单元测试（不调用模型）。
- `test_public_evaluation.py`：公开包校验。
- `test_query_router.py`：Router 行为与公开数据断言。

## 常见问题

- **为什么不把 200 题放进 Git？** 完整集含人工评审记录与扣留的 test，属私有考核资产；公开仓库只放脱敏 dev 子集。
- **改动后跑什么？** 先用 `--through prepare` 确认输入合法；再跑代表性子集（如 5 问/跨领域）看契约字段是否一致；重大改动建议全量 200 题。
- **结果波动怎么判断好坏？** 看契约字段（路由/状态/PASS），不看逐字输出；两次运行若 `implementationSha256` 一致说明代码没变。
