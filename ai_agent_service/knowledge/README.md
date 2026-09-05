# RAG GUIDE 知识库资料

本目录保存 [AI Agent Service](../README.md) 使用的 **GUIDE 文档和程序运行所需的知识数据**。GUIDE 覆盖平台规则、课程资料和校园生活指南，供 AI 回答知识类问题；**不提供实时商品价格、库存或交易状态**（这些信息由商品搜索工具提供）。

## 知识库收录哪些内容

平台有一类问题不依赖商品数据，而是依赖平台规则和学校规定——例如：宿舍能不能用 1500W 电热锅、教材是否必须买新版、能不能借同学账号卖东西。这些内容会被整理成 GUIDE 文档，加入向量索引后供 RAG 检索和引用。

```
documents/effective/*.md（GUIDE 正文） ─┐
                                       ├─→ rebuild_index → FAISS 索引
runtime/*.jsonl（文档信息和课程关系） ──┘
                                                   │
知识问题 ──→ HybridQueryRouter ──→ retrieve ───────┘
```

## 目录结构

```
knowledge/
├── documents/
│   └── effective/       当前生效、可用于普通用户知识检索的文档
│       ├── courses/     课程事实与统一购买提示（76 篇）
│       ├── platform/    平台现行规则（13 篇）
│       ├── dorm/        宿舍指南（5 篇）
│       └── lifecycle/   校园生活指南（5 篇）
└── runtime/             程序运行和重建索引所需的 JSONL
    ├── rag_document_manifest.jsonl
    ├── course_material_relations.jsonl
    ├── platform_rag_document_manifest.jsonl
    └── campus_guidance_manifest.jsonl
```

公开仓库只保留上面两类内容。知识采集过程、待审核草稿、来源核对材料、中间文件和检查报告不参与程序运行，已由 `.gitignore` 排除。

## 文档分类

### Effective（生效）

`documents/effective/` 保存**当前可用于普通用户知识检索**的内容。每个文档头部都会通过 YAML front matter 声明 `document_id`、`category`、`status` 和 `title`，例如：

```yaml
document_id: "GUIDE:campus-dorm-appliance-rules"
category: "campus_dorm"
status: "effective"
title: "宿舍电器使用限制"
```

## `runtime/` 中的文件

`runtime/*.jsonl` 是已经整理并审核、可由程序直接读取的数据，不是采集记录或检查报告：

| 文件 | 内容 |
| --- | --- |
| `rag_document_manifest.jsonl` | 课程 GUIDE 文档的信息，共 76 条 |
| `course_material_relations.jsonl` | 课程、专业、年级和学期之间的对应关系，共 2324 条 |
| `platform_rag_document_manifest.jsonl` | 平台规则文档的信息，共 13 条 |
| `campus_guidance_manifest.jsonl` | 宿舍与校园生活文档的信息，共 10 条 |

文档信息中主要包含：

- `document_id`：进入索引后的 GUIDE 文档 ID（形如 `GUIDE:campus-dorm-appliance-rules`）。
- `relative_path`：指向 `documents/effective/` 下的源 Markdown。
- `source_ids` / `source_urls`：事实来源（校规文件、平台链接等）。
- `applicable_campus`：适用校区。
- `invalidation_condition`：哪些变化会触发重新审核（如"学校通知/校历/宿舍规定变化"）。
- `chunking`：建立索引时使用的分块方式。

加载数据时，程序会确认 `relative_path` 指向 `documents/effective/`，并检查 JSONL 中的 `document_id`、`category`、`status` 和 `title` 是否与 Markdown 文件头部一致。

## 知识质量约束

- **能证明"来源提及"，不等于"本学期必须"**：课程资料提到教材，只能说明某来源曾提及，不代表当前学期强制购买。
- **实时信息靠工具**：价格、库存、成色、上架状态必须由商品查询提供，不能从静态 GUIDE 推断。
- **使用范围**：只有 `documents/effective/` 中状态为 `effective` 的文档可以用于生成回答。
- **更新与复核**：每份文档都会声明需要重新审核的条件；条件变化后应由人工复核并更新。
- **仓库内容**：草稿、采集记录和来源核对材料不能直接用于生成回答，也不随仓库发布。

## 重建索引

GUIDE 文档、`runtime/` 中的运行数据、Embedding 模型或向量维度变化后**必须重建索引**，否则检索结果可能与当前资料不一致：

```powershell
# 从 ai_agent_service 目录执行
python -m app.rag.rebuild_index
```

### GUIDE 与 Post 的分工

- **GUIDE**：保存在本目录的 Markdown 文件中，记录平台和学校相对稳定的资料。
- **Post**：社区帖子，**不保存在本目录中**。重建索引时，Python 通过 Java 获取当前有效帖子的快照数据，与 GUIDE 一起构建新的 FAISS 索引版本。

Post 在用于生成回答前仍需校验当前版本，避免引用已删除或修改的内容。

## 常见操作

| 想做什么 | 操作 |
| --- | --- |
| 新增一条平台规则 | 在 `documents/effective/platform/` 增加 Markdown，并在 `runtime/platform_rag_document_manifest.jsonl` 增加对应记录，然后重建索引 |
| 修改宿舍规则 | 修改 `documents/effective/dorm/` 中的文件，并同步更新 `runtime/campus_guidance_manifest.jsonl` 中的核对日期和来源，然后重建索引 |
| 修改课程资料 | 修改 `documents/effective/courses/` 中的文件，并同步更新 `runtime/rag_document_manifest.jsonl`；如果课程对应关系也有变化，还需更新 `runtime/course_material_relations.jsonl` |
| 核对资料来源 | 在仓库外保存采集和核对过程，只将审核后的正文及必要来源信息更新到 `documents/effective/` 和 `runtime/` |
| 确认知识当前状态 | 运行评测：改动后重建索引，并使用 `evaluation/` 中的题目进行回归评测 |
