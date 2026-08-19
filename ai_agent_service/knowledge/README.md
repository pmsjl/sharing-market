# RAG GUIDE Document 数据集

本目录是通过硬验收后发布的 GUIDE 可审计快照，覆盖平台正式知识以及 2019–2025 级全部本科培养方案。社区 Post 不固化在本目录的 Markdown 中；离线重建时通过 Java `GET /api/internal/ai/rag/posts` 分页读取，并与 GUIDE 一起发布为不可变 FAISS 构建。

## 验收结论

- 发布许可：`delivery_allowed = true`
- 培养方案：210份
- 专业—课程记录：12595条
- 唯一课程身份：1351个，均已完成公开来源检索
- 公开课程仓库：127个；含书目提及的课程仓库：46个；含软件/器材/环境提及的课程仓库：41个
- 公开课程仓库全树审计：127个已完成，共清点4627个文件；读取90个安全文本/配置，1984个文件仅记元数据
- 含任一购买相关资料提及的课程仓库：75个，均生成唯一的教材、软件与器材事实文档
- 课程RAG采用结构化关系 + Multi-representation父文档结构：75份课程事实文档、1份统一说明、2324条课程关系和194个稳定关系组
- 完整过滤、来源和审计元数据位于 `normalized/rag_document_manifest.jsonl`；关系联查数据位于 `normalized/course_material_relations.jsonl`
- 未发现公开购买相关资料提及的课程身份：1231个，均明确标记为 `unknown_after_search`
- 第一类规则来源：56个来源记录，包括六个平台官方资料、国家现行法规、哈工大/深圳校区规定和本项目代码事实
- 第一类规则事实：229条审核事实，覆盖13个主题；13份面向用户的现行规则位于 `documents/effective/platform/`
- 第一类RAG清单：`normalized/platform_rag_document_manifest.jsonl`；来源发现报告和需求追踪矩阵位于 `normalized/platform_source_discovery_report.json` 与 `normalized/platform_policy_requirement_matrix.json`
- 第三、四类校园指南：10份面向用户的文档位于 `documents/effective/dorm/` 与 `documents/effective/lifecycle/`；来源见 `sources/campus_sources.json`，清单见 `normalized/campus_guidance_manifest.jsonl`
- 六个平台研究、法规与校规证据位于 `documents/reference/`；依赖新功能或待确认校规的内容位于 `documents/draft/platform/`，不参与普通用户RAG
- 正式/参考/草案文档总数：118份
- 当前已发布索引基线：99 篇 GUIDE、180 篇 Post、1209 个 chunks；Post 在每次进入模型前仍需通过 Java 版本校验

课程中的“教材与参考资料”只证明来源曾提及该书，不证明当前学期必须购买。实时价格、库存、成色和上架状态继续由商品工具查询。

详细硬门槛、未知比例和问答审计见 `acceptance_report.json`、`unknown_information_report.json` 与 `qa_source_answer_review.jsonl`。
