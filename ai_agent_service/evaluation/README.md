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
├── public/             公开：脱敏后的 Dev 140 题目集 + Manifest + 标注规范 + 基准摘要
├── schemas/            Case 的 JSON Schema
├── tools/              运行公开评测所需的脚本
└── README.md
```

`dataset/` 与 `runs/` 分别用于存放完整题目集和评测输出：完整题目集需自行准备，评测输出在运行评测后生成。

| 子目录 | 说明 |
| --- | --- |
| `public/`、`schemas/`、`tools/`、`README.md` | 公开评测数据和运行评测所需的代码 |
| `dataset/`、`runs/` | 完整题目集与评测输出；前者需自行准备，后者运行后生成 |

## 数据集

完整评测集位于 `dataset/`，`public/` 是其脱敏后的 Dev 子集。

### 完整评测集（`dataset/golden_v1_3_reviewed_200.jsonl`）

当前最终版本 **golden-v1.3-reviewed-20260911**，共 **200 题**：

| 领域 | 题数 | 覆盖内容 |
| --- | ---: | --- |
| course（课程资料） | 20 | 教材版本、课件获取、课程决策 |
| post（二手商品） | 64 | 各类商品购买/转卖决策 |
| platform（平台规则） | 45 | 账号、交易规则、边界 |
| boundary（合规边界） | 41 | 能否上架、正品、禁售 |
| campus（校园生活） | 30 | 宿舍电器、生活决策 |

- **split**：dev 140（开发调试用）+ test 60。
- 每条含 `expectedRoute`（retrieve/clarify/out_of_scope/skip_rag）、`expectedKnowledgeState`、`expectedFacts`、`qrels`（文档级相关性标注）、`provenance`、`review`（评审记录）等字段。
- **数据集**：运行完整评测时需自备 Dataset 和 Manifest。

### 公开评测集（`public/dev_v1_3.jsonl`）

由完整评测集的 dev 140 题脱敏而成。

- 140 题、109 条 qrels。
- 准确版本、数量、Hash 以 `public/manifest.json` 为准。
- 用途：开发调试、数据格式验证、公开可复现基线。

### 完整评测集 ↔ 公开评测集

| | 完整评测集 | 公开评测集（public/） |
| --- | --- | --- |
| 题数 | 200（dev 140 + test 60） | 140（仅 dev） |
| review 评审记录 | 有 | `{"status":"frozen"}` |
| provenance | 含构造信息 | 仅留 `source` |
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
| ② Retrieval | `run_golden_v1_3_retrieval_eval.py` | query + 指定版本的索引 | `pipeline_retrieval_<build>.jsonl` | Hit@k、MRR、qrel 命中 |
| ③ Generation | `run_golden_v1_3_answer_generation.py` | Router 结果 + 检索结果 | `pipeline_answer_generation.jsonl` | 答案生成成功、引用完整 |
| ④ Judge | `run_golden_v1_3_answer_judge.py` | 生成结果 + 期望 | `pipeline_answer_judgments.jsonl` | 答案是否 PASS、知识状态是否正确 |
| ⑤ Final | `build_golden_v1_3_single_v2_final_results.py` | Generation + Judge | `pipeline_final_results.jsonl` + `_manifest.json` | 汇总 PASS/FAIL、按领域统计 |

公开评测脚本统一使用 `v1_3` 命名。独立阶段脚本使用 `GOLDEN_V1_3_RUN_DIRECTORY` 指定运行目录；统一入口会自动设置它。Final 文件名中的 `single_v2` 是单 Case Judge 的评分协议版本。

### 共享库

- `golden_v1_3_round2_paths.py`：统一计算 runs 目录、结果/报告路径。
- `course_question_quality.py`：课程题的质量校验与元数据。
- `golden_current_runtime_expectations.py`：按当前系统行为修正预期结果（如学校固定不追问）。
- `materialize_golden_v1_3_reviewed.py`：数据集生成脚本，处理 v1.1 → v1.2.1 数据。

### 关键约定

- **固定索引版本**：每个 Manifest 记录一个 `indexBuildIdAtFreeze`。评测必须使用题目集确定版本时指定的索引版本，否则对比结果不可靠。
- **工作目录**：阶段脚本以 `ai_agent_service/` 为工作目录运行（`.env` 中 `RAG_INDEX_DIR` 是相对路径）。
- **Hit@k 指标定义**：检索排序指标 `hitAt1/3/5` 判断"首个 relevance ≥ 2 的文档是否落在前 k 位"。

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

改动 GUIDE 知识、Embedding 模型或向量维度后**必须重建索引**（`python -m app.rag.rebuild_index`），并用新索引运行评测。

## 运行评测

### 环境

评测需要调用真实 LLM 与 Embedding 服务，因此在配置好的 `ai_agent_service/` 环境（含 `.env`）中运行。本机评测环境为 conda 的 `fastapi` 环境。

### 完整 200 题

```powershell
# 从仓库根目录，使用自行准备的评测集和 Manifest
<python> ai_agent_service\evaluation\tools\run_golden_pipeline.py `
  --dataset ai_agent_service\evaluation\dataset\golden_v1_3_reviewed_200.jsonl `
  --manifest ai_agent_service\evaluation\dataset\golden_v1_3_reviewed_200_manifest.json `
  --run-name <run-name> `
  --through final
```

### 选子集（调试 / 回归，不运行全部题目）

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
- 子集调试优先从公开 Dev 各领域选题。

### 索引不可用时的覆盖

Manifest 中记录的索引不在本地时，必须显式传入可用索引：

```powershell
--index-build-id <可用-build-id>
```

覆盖索引后的结果不直接等同于固定基线。原索引版本和本次临时指定的版本都会写入运行 Manifest（`pipelineSelection.sourceIndexBuildIdAtFreeze` / `indexBuildOverride`）。

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

- 脱敏 Dev 数据、Schema、Manifest、标注规范、聚合基线摘要。
- 公开题目中的 `provenance.source` 指向 `knowledge/runtime/` 下对应的运行数据。

### 公开基线

`public/benchmark_summary.md` 给出 2026-09-21 更新的 v1.3 五阶段消融指标。五个阶段使用同一份 Golden v1.3 数据集、同一套评测脚本和同一固定索引，各阶段的新增能力与 Route / Hit 指标如下：

| 阶段 | 新增能力 | Router 正确 | Hit@1 | 条件 Hit@1 | Required qrel hit |
|---|---|---:|---:|---:|---:|
| 1 | 无 Router 基线 | 不适用 | 61.42% | 61.42% | 93.70% |
| 2 | + LLM Router | 176/200（88.00%） | 60.63% | 61.60% | 92.91% |
| 3 | + ABC 三通道检索 | 176/200（88.00%） | 80.31% | 81.60% | 89.76% |
| 4 | + 短 chunk ID 映射 | 176/200（88.00%） | 80.31% | 81.60% | 89.76% |
| 5 | + 最终提示词 | 190/200（95.00%） | 81.10% | 82.40% | 87.40% |

Hit@1 在阶段 3 出现最大跃升（60.63% → 80.31%），Router 正确数在阶段 5 提升到 190/200（95.00%）；阶段 4 的检索指标与阶段 3 逐位相同，其改的是引用协议。

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
