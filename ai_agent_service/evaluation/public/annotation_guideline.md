# Golden v1.3 公开 Dev 标注说明

公开包来自 `golden-v1.3-reviewed-20260911` 的 Dev 140。完整集共 200 题，课程题压缩至 20，并移除重复度较高的问题、加入新问题及更新部分 expected；公开集课程题为 13。此次发布保留定版题面、期望、qrels 和领域契约，不重新标注。

- `expectedRoute`：`retrieve`、`clarify`、`out_of_scope`、`skip_rag`。`expectedAction` 若存在，保留源数据的动作期望。
- `expectedKnowledgeState`：`answerable`、`unknown_after_search`、`not_applicable`。`skip_rag` 表示不使用知识检索，并不排除商品或偏好工具；空库存场景可标为 `unknown_after_search`。
- `expectedRequiredKnowledgeDomains` / `expectedForbiddenKnowledgeDomains`：回答所需或禁止的证据领域，独立于题目分类 `domain`；允许多领域。
- `preferredSourceType` / `allowedSourceTypes`：GUIDE、POST 来源契约。
- `qrels`：文档级相关性，3 为核心，2 为重要支持，1 为背景，0 为无关或禁止；`required` 指必需证据，`supportingChunkIds` 指冻结片段。
- `mustNotUse` / `forbiddenDocumentPrefixes`：证据排除条件；`expectedFacts`：期望回答要点。

范围以用户实际需求和可靠上下文为准。本平台规则、实际在售商品查询、物品取得或处置决策、选择适配验货以及可交易物品型号规格属于范围内；单纯外部安排、准备自带、已有物品日常管理不等同于取得。混合请求分别判断；具体对象缺失与可以给出通用方法要区分。

正式规则优先使用 GUIDE；经验 POST 不能覆盖正式规则。实时库存、价格、品相和个人账户事实不能由静态知识推断。证据领域按结论依赖选择，不仅凭物品名称或课程背景。

公开 `version` 规范化为 `golden-v1.3`；`review` 只保留 frozen，`provenance` 只保留 source，其中旧知识目录前缀沿用公开包规则映射到 `knowledge/runtime/`。评审意见、内部构造信息、原始输出和 Test 60 不公开。Test 已参与人工查看和分析，不能称为从未见过的独立测试集。

统计与 SHA-256 见 [Manifest](manifest.json)，当前三阶段结果见 [基线摘要](benchmark_summary.md)。
