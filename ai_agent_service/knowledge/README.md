# RAG GUIDE 知识数据

本目录保存 [AI Agent Service](../README.md) 使用的 **GUIDE 文档与知识管线**。GUIDE 覆盖平台规则、课程资料和校园生活指南，供 AI 回答知识类问题；**不提供实时商品价格、库存或交易状态**（那些由商品搜索工具提供）。

## 知识是什么

平台有一类问题不依赖商品数据，而是依赖"平台/学校的事实规则"——例如：宿舍能不能用 1500W 电热锅、教材是否必须买新版、能不能借同学账号卖东西。这类事实沉淀为 GUIDE 文档，进入向量索引后被 RAG 检索引用。

```
知识问题 ──→ HybridQueryRouter ──→ retrieve ──→ FAISS 索引 ──→ GUIDE 文档片段
                                                    ↑
                              documents/*.md（来源）  ↓
                              normalized/*.jsonl（元数据/分块规则）
```

## 目录结构

```
knowledge/
├── documents/
│   ├── effective/       当前生效、可进入普通用户 RAG 的文档
│   │   ├── courses/     课程事实与统一购买提示（76 篇）
│   │   ├── platform/    平台现行规则（13 篇）
│   │   ├── dorm/        宿舍指南（5 篇）
│   │   └── lifecycle/   校园生活指南（5 篇）
│   ├── reference/       来源研究与参考证据（审计用，不直接生效）
│   │   ├── comparisons/ 竞品/平台对比
│   │   ├── legal/       法条依据
│   │   ├── platforms/   平台政策参考
│   │   └── school/      校规/学校资料
│   └── draft/           待实现/待确认/待审核草案（5 篇，不作为确定依据）
├── normalized/          每个文档的规范化 Manifest（JSONL，含分块规则/来源/失效条件）
└── sources/             来源清单（course/platform/campus 源）
```

## 文档分类

### Effective（生效）

`documents/effective/` 保存**当前可以进入普通用户 RAG** 的内容。每个文档头部有 YAML front matter 声明 `document_id`、`category`、`status`、`title`，例如：

```yaml
document_id: "GUIDE:campus-dorm-appliance-rules"
category: "campus_dorm"
status: "effective"
title: "宿舍电器使用限制"
```

### Reference（参考）

`documents/reference/` 保存来源研究和参考证据，**用于审计与维护**，不等同于面向用户直接生效的平台规则。不能把 Reference 内容包装成已生效政策。

### Draft（草案）

`documents/draft/` 保存依赖待实现功能、待确认校规或尚未完成审核的草案。**草案不应作为普通用户回答的确定依据**。

## Normalized Manifest 的作用

`normalized/*.jsonl` 是每个有效文档的**结构化元数据**（AI Agent 侧读取）：

- `document_id`：进入索引后的 GUIDE 文档 ID（形如 `GUIDE:campus-dorm-appliance-rules`）。
- `relative_path`：指向 `documents/effective/` 下的源 Markdown。
- `source_ids` / `source_urls`：事实来源（校规文件、平台链接等）。
- `applicable_campus`：适用校区。
- `invalidation_condition`：何时该失效重核（如"学校通知/校历/宿舍规定变化"）。
- `chunking`：建索引时的分块策略。

## 知识质量约束

- **能证明"来源提及"，不等于"本学期必须"**：课程资料提到教材，只能说明某来源曾提及，不代表当前学期强制购买。
- **实时信息靠工具**：价格、库存、成色、上架状态必须由商品查询提供，不能从静态 GUIDE 推断。
- **生效边界**：只有 `effective` 能进入回答；Reference/Draft 不能被包装成已生效政策。
- **失效跟踪**：每条文档声明失效条件；条件变化后应人工复核并更新。

## 重建索引

GUIDE 文档改动、Embedding 模型或向量维度变化后**必须重建索引**，否则检索结果与文档不一致：

```powershell
# 从 ai_agent_service 目录执行
python -m app.rag.rebuild_index
```

### GUIDE 与 Post 的分工

- **GUIDE**：固化在本目录 Markdown 中，代表平台/学校稳定事实。
- **Post**：社区帖子，**不固化在本目录**。重建索引时 Python 通过 Java 获取当前有效 Post 快照，与 GUIDE 一起构建不可变 FAISS 索引。

Post 在进入回答前仍需校验当前版本，避免引用已删除或修改的内容。

## 常见操作

| 想做什么 | 操作 |
| --- | --- |
| 新增一条平台规则 | 在 `documents/effective/platform/` 写 Markdown（含 YAML front matter），在 `normalized/` 补 Manifest 行，重建索引 |
| 修改宿舍规则 | 改 `documents/effective/dorm/` 对应文件，更新 `normalized` 的 `last_verified_at`/来源，重建索引 |
| 校对一条来源 | 在 `documents/reference/` 记录证据，更新源文档的 `source_urls` |
| 确认知识当前状态 | 跑评测：改动后重建索引并用 `evaluation/` 的题目回归 |
