# Golden v1.2.1 三阶段评测对比

本文只公开可用于说明系统演进的**聚合指标**。完整 200 题 Dataset、扣留的 Test 60、完整 qrels、逐题 Router/Retrieval/Generation/Judge 输出、失败理由和人工评审记录均保留在本地，不进入 Git。

## 评测口径

- Dataset：Golden v1.2.1，200 条人工复核 Case（140 Dev / 60 Test）。
- 冻结索引：`20260819T151857Z-b1c54bb0e56f49e89251135abebc4c71`。
- 索引规模：359 documents / 1,611 chunks。
- 生成与 Judge 模型：`gpt-5.6-terra`。
- Judge：`v2_current_runtime`，每次请求只评 1 个 Case，避免批内交叉污染。
- 人工修订影响的 4 条 Case 已分别在三个真实代码版本上重跑；各阶段总结果由 196 条未受影响记录与 4 条新结果重组，并校验为完整 200 条，不是直接修改 PASS/FAIL 标签。

> 阶段 1 没有同口径的独立 Router/Retrieval 报告，因此对应指标保持“—”，不从其他 Golden 版本拼接。

## 三个版本

| 阶段 | Git commit | 代码状态 |
|---|---|---|
| 阶段 1：原始基线 | `b29171c278ba1f01c170a06b2dea0a19d3a13ee6` | 新 Router、课程 A/B/C 检索规划及 K/C 短引用映射引入前 |
| 阶段 2：结构优化首版 | `b4f5601b6be3a0394ddf429a53c6a2ae056d8581` | 已加入结构化 Router、课程 A/B/C 检索和 K/C 短引用；通用边界提示词优化前 |
| 阶段 3：当前版 | `5d6dbfbb444353e4dbf4cd18661035c56624c392` | 在阶段 2 基础上完成通用 Router/Answer 边界提示词优化 |

## 聚合结果

| 指标 | 阶段 1：原始基线 | 阶段 2：结构优化首版 | 阶段 3：当前版 |
|---|---:|---:|---:|
| Router Accuracy | — | 94.00%（188/200） | 96.50%（193/200） |
| Recall@1 | — | 71.43% | 74.29% |
| Recall@3 | — | 87.43% | 92.00% |
| Recall@5 | — | 92.57% | 92.57% |
| MRR | — | 0.772952 | 0.805238 |
| nDCG@5 | — | 0.793497 | 0.814119 |
| Required qrel hit | — | 89.14% | 89.71% |
| Generation SUCCESS | 182/200 | 200/200 | 200/200 |
| Generation ERROR | 18 | 0 | 0 |
| 最终 PASS | **169/200（84.50%）** | **190/200（95.00%）** | **194/200（97.00%）** |

### 分领域最终通过率

| Domain | 阶段 1 | 阶段 2 | 阶段 3 |
|---|---:|---:|---:|
| boundary | 17/20（85.00%） | 19/20（95.00%） | 20/20（100.00%） |
| campus | 15/20（75.00%） | 15/20（75.00%） | 20/20（100.00%） |
| course | 58/70（82.86%） | 69/70（98.57%） | 67/70（95.71%） |
| platform | 40/40（100.00%） | 38/40（95.00%） | 39/40（97.50%） |
| post | 39/50（78.00%） | 49/50（98.00%） | 48/50（96.00%） |

## 如何理解提升

### 阶段 1 → 阶段 2

这一段是**组合优化**，包括结构化 LLM Router、课程关系索引与 A/B/C QueryPlan、K/C 短引用白名单校验及真实 ID 回填。它不是只改 Router 的单变量消融，因此不能把端到端提升全部归因于 Router。结果表现为 Generation Error 从 18 降至 0，最终通过率从 84.50% 提升至 95.00%。

### 阶段 2 → 阶段 3

这一段主要是通用边界提示词迭代：按照“平台规则、实时查询、商品决策、是否缺少用户条件、一般信息”的顺序判断，并区分商品决策与已有物品日常管理。Router Accuracy 从 94.00% 提升至 96.50%，Recall@3 从 87.43% 提升至 92.00%，端到端通过率从 95.00% 提升至 97.00%。

## 对外披露边界

仓库公开：

- [`ai_agent_service/evaluation/public/dev_v1_2_1.jsonl`](../../ai_agent_service/evaluation/public/dev_v1_2_1.jsonl)：脱敏 Dev 140。
- [`ai_agent_service/evaluation/public/manifest.json`](../../ai_agent_service/evaluation/public/manifest.json)：公开数据统计、Hash 与索引快照。
- [`ai_agent_service/evaluation/public/benchmark_summary.md`](../../ai_agent_service/evaluation/public/benchmark_summary.md)：当前版聚合结果。
- [`ai_agent_service/evaluation/tools/`](../../ai_agent_service/evaluation/tools/)：评测执行与校验脚本。

仓库不公开：完整 Test、私有 Dataset、原始模型输出、逐题判断、Case 迁移清单、失败理由、请求 Trace、用量和人工评审记录。上述内容仅存放在被 `.gitignore` 排除的 `evaluation/dataset/`、`evaluation/runs/` 与 `evaluation/human_review/` 中。

完整评测结构与运行方式见 [`ai_agent_service/evaluation/README.md`](../../ai_agent_service/evaluation/README.md)。
