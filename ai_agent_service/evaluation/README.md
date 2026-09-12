# Golden Test 评测体系（Public RAG Evaluation）

本目录保存 [AI Agent Service](../README.md) 的 **Golden Test 评测体系**：它对"意图路由 → GUIDE/Post 检索 → 答案生成 → 自动评分"进行端到端回归评测，并提供可公开、可复现的 Dev 评测集。

## 为什么需要它

平台 AI 导购依赖 LLM 意图路由、向量检索和答案生成。Prompt、路由阈值、索引和 Embedding 模型中任一项发生变化，都可能影响回答质量。Golden Test 使用**经人工审核的固定题目集**进行完整流程的回归评测：每次改动后运行同一批题，逐阶段对比结果，判断这次改动是让系统变好了还是变差了。

评测目标：
- **可复现**：使用同一题目集、同一索引版本和同一版评测脚本，两次结果才具有可比性。
- **分阶段定位**：Router、Retrieval、Generation、Judge 各阶段独立产出，出问题能定位到环节。
- **考虑模型输出的不确定性**：生成结果和自动评分可能存在波动，因此对比时主要检查路由、状态和 PASS/FAIL 等关键结果，同时单独记录回答文本是否完全一致。

## 目录结构

```
evaluation/
├── public/             公开：脱敏后的 Dev 140 题包 + Manifest + 标注规范 + 基准摘要
├── schemas/            Case 的 JSON Schema
├── tools/              运行公开评测所需的脚本
└── README.md
```

本地还可以存在 `dataset/`、`runs/` 和 `human_review/`，分别用于保存完整题目集、评测输出和人工评审记录。这些目录已被 Git 忽略，不会随仓库发布。

| 子目录 | 是否进 Git | 说明 |
| --- | --- | --- |
| `public/`、`schemas/`、必要的 `tools/` 脚本、`README.md` | 是 | 公开评测数据和运行评测所需的代码 |
| `dataset/`、`runs/`、`human_review/` | 否（忽略） | 不随仓库发布的完整题目集、评测输出和评审材料 |

## 数据集

评测数据依次经过"人工编写 → 人工审核 → 生成并定版"三个步骤。最终生成 `dataset/` 下的完整评测集，以及 `public/` 下的脱敏子集。

### 完整评测集（不随仓库发布，`dataset/golden_v1_3_reviewed_200.jsonl`）

当前定版 **golden-v1.3-reviewed-20260911**，共 **200 题**：

| 领域 | 题数 | 覆盖内容 |
| --- | ---: | --- |
| course（课程资料） | 20 | 教材版本、课件获取、课程决策 |
| post（二手商品） | 64 | 各类商品购买/转卖决策 |
| platform（平台规则） | 45 | 账号、交易规则、边界 |
| boundary（合规边界） | 41 | 能否上架、正品、禁售 |
| campus（校园生活） | 30 | 宿舍电器、生活决策 |

- **split**：dev 140（开发调试用）+ test 60（不公开，已参与人工查看和分析）。
- 每条含 `expectedRoute`（retrieve/clarify/out_of_scope/skip_rag）、`expectedKnowledgeState`、`expectedFacts`、`qrels`（文档级相关性标注）、`provenance`、`review`（评审记录）等字段。
- **数据集本身不进 Git**：完整评测集包含人工评审记录，不随仓库发布。仓库只提交执行代码，运行完整评测时需由调用方自备 Dataset 和 Manifest。

### 公开评测集（`public/dev_v1_3.jsonl`）

从完整评测集的 dev 140 题脱敏后发布：剥离评审记录、内部构造字段和不公开的 test 60 题。

- 140 题、109 条 qrels。
- 准确版本、数量、Hash 以 `public/manifest.json` 为准。
- 用途：开发调试、数据格式验证、公开可复现基线。

### 完整评测集 ↔ 公开评测集

| | 完整评测集（不随仓库发布） | 公开评测集（public/） |
| --- | --- | --- |
| 题数 | 200（dev 140 + test 60） | 140（仅 dev） |
| review 评审记录 | 有 | 剥离，仅留 `{"status":"frozen"}` |
| provenance | 含内部构造细节 | 仅留 `source` |
| 索引版本 | Manifest 记录 `indexBuildIdAtFreeze` | Manifest 记录 `indexSnapshot` |

## 评测流程（五阶段）

```
        ┌──────────────────────────────────────────────────────────┐
        │            run_golden_pipeline.py（统一运行入口）         │
        │  选 Case → 指定索引版本 → 依次运行 4 个阶段脚本 → 汇总结果 │
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

| 阶段 | 阶段脚本（`tools/` 下） | 输入 | 输出 | 检查什么 |
| --- | --- | --- | --- | --- |
| ① Router | `run_golden_v1_3_router_eval.py` | Case 的 query/history | `pipeline_router.jsonl` | 路由是否命中 `expectedRoute` |
| ② Retrieval | `run_golden_v1_3_retrieval_eval.py` | query + 指定版本的索引 | `pipeline_retrieval_<build>.jsonl` | Recall@k、MRR、qrel 命中 |
| ③ Generation | `run_golden_v1_3_answer_generation.py` | Router 结果 + 检索结果 | `pipeline_answer_generation.jsonl` | 答案生成成功、引用完整 |
| ④ Judge | `run_golden_v1_3_answer_judge.py` | 生成结果 + 期望 | `pipeline_answer_judgments.jsonl` | 答案是否 PASS、知识状态是否正确 |
| ⑤ Final | `build_golden_v1_3_single_v2_final_results.py` | Generation + Judge | `pipeline_final_results.jsonl` + `_manifest.json` | 汇总 PASS/FAIL、按领域统计 |

公开评测脚本名称统一使用 `v1_3`，调用、导入及默认数据和输出名称同步更新。独立阶段脚本使用 `GOLDEN_V1_3_RUN_DIRECTORY` 指定运行目录；统一入口会自动设置它。历史运行记录中的旧命令仍保留原样，不迁移历史产物。Final 文件名中的 `single_v2` 表示单 Case Judge 的评分协议版本，不是数据集版本。

### 共享库

- `golden_v1_3_round2_paths.py`：统一计算 runs 目录、结果/报告路径。
- `course_question_quality.py`：课程题的质量校验与元数据。
- `golden_current_runtime_expectations.py`：按当前系统行为修正预期结果（如学校固定不追问）。
- `materialize_golden_v1_3_reviewed.py`：随 v1.3 工具包统一命名的历史物化工具，仍只处理 v1.1 → v1.2.1；不是 v1.3 数据生成器。旧输入、输出版本和统计约束保留，避免把旧题集误标为 v1.3。

### 关键约定

- **固定索引版本**：每个 Manifest 记录一个 `indexBuildIdAtFreeze`。评测必须使用题目集定版时指定的索引版本，否则对比结果不可靠。
- **每次只评一道题**：批量提交多道题时，自动评分曾出现题目与答案错配。因此目前每次请求只评测一个 Case，以确保评分对应正确。
- **工作目录**：阶段脚本以 `ai_agent_service/` 为工作目录运行（`.env` 中 `RAG_INDEX_DIR` 是相对路径）。

## 数据与索引的关系

GUIDE 文档（`knowledge/`）与 Post 快照（经 Java 获取）先构建 FAISS 索引，Router/Retrieval 再在其上工作：

```
knowledge/ GUIDE 文档 ─┐
                       ├─→ rebuild_index → FAISS 索引（含 indexBuildId）
market_backend Post 快照 ┘                          │
                                                   ▼
                  Dataset Manifest 记录题目集对应的 indexBuildId
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
# 从仓库根目录，使用不随仓库发布的评测集和 Manifest
<python> ai_agent_service\evaluation\tools\run_golden_pipeline.py `
  --dataset ai_agent_service\evaluation\dataset\golden_v1_3_reviewed_200.jsonl `
  --manifest ai_agent_service\evaluation\dataset\golden_v1_3_reviewed_200_manifest.json `
  --run-name <run-name> `
  --through final
```

### 选子集（调试 / 回归，不跑全量）

```powershell
<python> ai_agent_service\evaluation\tools\run_golden_pipeline.py `
  --dataset <dataset.jsonl> --manifest <manifest.json> `
  --run-name smoke_5_v1_3 `
  --case-id boundary-02-official-dorm-kettle `
  --case-id campus-campus-dorm-bed-desk-dimensions-01 `
  --case-id course-material_mention-002 `
  --case-id platform-cancellation-refund-and-disputes-01 `
  --case-id post-legacy-001 `
  --through final
```

- `--case-id` 可多次传，或 `--limit N` 取前 N 题。
- `--through prepare` 只选择 Case 并关联 Manifest，**不调用模型**，用于快速检查输入是否合法。
- 子集调试优先从公开 Dev 各领域选题；Test 已参与人工分析，不作为从未见过的隐藏测试集。

### 索引不可用时的覆盖

Manifest 中记录的索引不在本地时，必须显式传入可用索引：

```powershell
--index-build-id <可用-build-id>
```

覆盖索引后的结果不直接等同于冻结基线。原索引版本和本次临时指定的版本都会写入运行 Manifest（`pipelineSelection.sourceIndexBuildIdAtFreeze` / `indexBuildOverride`）。

## 评测输出（`runs/<run-name>/`）

```
runs/<run-name>/
├── PIPELINE_MANIFEST.json     本次评测清单（Case、索引、命令、各阶段脚本哈希）
├── input/
│   ├── selected_cases.jsonl   实际评测的子集
│   └── selected_manifest.json  对应的 Manifest
├── results/
│   ├── pipeline_router.jsonl
│   ├── pipeline_retrieval_<build>.jsonl
│   ├── pipeline_answer_generation.jsonl
│   ├── pipeline_answer_judgments.jsonl
│   └── pipeline_final_results.jsonl + _manifest.json
└── reports/                   各阶段 summary / badcases
```

`PIPELINE_MANIFEST.json` 记录 `implementationSha256`（各脚本哈希），只能据此确认这些阶段脚本是否相同，不能证明整个运行时、提示词与依赖均未变化。
Router、Answer Generation 和 Judge 的报告还会分别记录实际模型、`reasoningEffort` 与 `textVerbosity`；正式对比不得只记录模型名称而省略推理强度。

## 对照两次运行

用 `compare_golden_runs.py` 对比修改前后的两次评测结果，逐阶段比较关键判定字段：

```powershell
<python> ai_agent_service\evaluation\tools\compare_golden_runs.py `
  --baseline-manifest <baseline-run>\PIPELINE_MANIFEST.json `
  --candidate-manifest <candidate-run>\PIPELINE_MANIFEST.json `
  --output <comparison.json>
```

对照维度：

| 阶段 | 关键判定字段 |
| --- | --- |
| Router | 路由、决策来源 |
| Retrieval | 路由、知识状态 |
| Generation | 成功状态、Intent |
| Judge | PASS、关键错误、知识状态 |

报告同时给出：
- `sameImplementation`：两次运行的脚本哈希是否一致。
- `sameSelectedCaseIds`、`allStagesHaveSameCaseSet`：是否同一批题。
- 各阶段 `contractEquivalentCaseCount`（关键判定结果一致）与 `equivalentCaseCount`（回答文本完全一致）。
- 明确标注 `modelOutputsMayBeNondeterministic`：回答文本存在差异属于模型输出波动，不等同于功能退化。

## 公开评测包

`public/` 是可公开、可复现的 Dev 包，用于数据格式、Schema、公开基线校验，不依赖模型。

### 完整性验证

```powershell
<python> ai_agent_service\evaluation\tools\validate_public_evaluation.py
```

验证器检查：140 题、字段是否符合 Schema、review/provenance 是否完成脱敏，以及 Manifest 统计与 Hash 是否一致。不调用模型。

### 仓库中包含的评测内容

- 公开：脱敏 Dev 数据、Schema、Manifest、标注规范、聚合基线摘要。
- 不公开：test 60（固定回归集）、模型原始输出、请求 Trace、标识符、用量记录、人工评审记录和完整 Dataset。
- 公开题目中的 `provenance.source` 已改为指向 `knowledge/runtime/` 下对应的运行数据，不再引用已移出仓库的 `knowledge/normalized/`。

### 公开基线

`public/benchmark_summary.md` 给出 2026-09-12 更新的 v1.3 三阶段聚合指标（阶段 3 Router 95.00%、Recall@5 90.55%、Final 97.00%）。注意：Public Dev 一旦用于实现调整或参数选择，就不能再表述为"未见过的隐藏测试集"。

三阶段使用同一份 Golden v1.3 数据集、同一套评测脚本和同一冻结索引，对比各阶段代码与提示词的最终结果。每阶段包含 143 条未改动题的历史结果和 57 条新增或更新题的结果；阶段 1 使用 6e7874e 的检索代码与回答提示词，阶段 2、3 使用各自对应版本。最新最终通过数依次为 173/200（86.50%）、186/200（93.00%）、194/200（97.00%）；阶段 2、3 检索指标分别来自各自的运行产物。

三个代码阶段的公开汇总对比见 [`../../docs/evaluation/three-stage-benchmark.md`](../../docs/evaluation/three-stage-benchmark.md)。该文档只披露汇总数据；逐题结果与 Judge 理由仍保存在本地，不随仓库发布。

## 测试

评测工具自身的回归测试在 `ai_agent_service/tests/`：

```powershell
cd ai_agent_service
<python> -m pytest -q tests/test_golden_pipeline_tools.py `
  tests/test_public_evaluation.py `
  tests/test_query_router.py
```

- `test_golden_pipeline_tools.py`：统一运行入口与结果对比脚本的单元测试（不调用模型）。
- `test_public_evaluation.py`：公开包校验。
- `test_query_router.py`：Router 行为与公开数据断言。

## 常见问题

- **为什么不把 200 题放进 Git？** 完整评测集包含人工评审记录和未公开的 Test 集，不随仓库发布；公开仓库只放脱敏后的 dev 子集。
- **改动后跑什么？** 先用 `--through prepare` 确认输入合法，再跑代表性子集（如跨五个领域各取 1 题），检查关键判定字段是否一致；重大改动建议运行完整的 200 题。
- **结果波动怎么判断好坏？** 主要看路由、状态和 PASS/FAIL 等关键结果，不只看回答文本；两次运行若 `implementationSha256` 一致，说明评测脚本代码没有变化。
