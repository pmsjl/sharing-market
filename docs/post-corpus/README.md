# Campus post corpus

这个目录只保留校园二手帖子语料的安全说明，不保存网页采集结果、网页摘录、来源映射或生成正文副本。语料内容围绕当前校园二手平台的商品分类、课程/设备购买判断、宿舍使用、验机、面交、物流、付款和争议证据编写；不写实时在售状态或未经核验的学校制度。

## 权威源与重建

运行时 Post 的唯一权威来源是数据库。可审计的本地编辑源和构建入口为：

- `tools/post_corpus/posts.jsonl`：260 篇通过静态门禁的标题、标签、正文、主题和固定作者分配；
- `tools/post_corpus/expand_post_corpus_v2.py`：按当前八类商品生成第 180～259 篇扩充语料，先产出预览，通过校验后才允许发布；
- `tools/build_post_corpus.py`：执行字数、主题、重复段落、重复句、标题模板和相似度门禁，并生成 SQL；
- `tools/post_corpus/quality_report.json`：最近一次静态质量报告；
- `market_backend/sql/20260815_seed_campus_trade_posts.sql`：幂等更新数据库中固定 seed Post 的 SQL；
- `market_backend/sql/20260819_rollback_campus_trade_posts_v2.sql`：按固定 Snowflake ID 区间安全回滚当前 260 篇语料；
- `tools/post_corpus/retrieval_regression_report.json`：最近一次 20 条代表性 Post 检索回归报告。

重建顺序是：

1. 用 `python tools/build_post_corpus.py --check-only` 验证语料；
2. 用 `python tools/build_post_corpus.py` 生成 SQL；
3. 在目标 `trade` 数据库事务中执行 seed SQL；
4. 启动 Java 后端，使 Python 通过 `/api/internal/ai/rag/posts` 读取有效 Post 快照；
5. 在 `ai_agent_service` 中运行 `python -m app.rag.rebuild_index`，成功后原子切换 FAISS 的 `CURRENT`。

## 当前验收结果

截至 **2026-08-19**：

- 本地审核源和生成 SQL 已扩充为 260 篇 seed Post、20 个演示作者；原 180 篇索引不变，第 180～259 篇为追加内容；
- 中文汉字总数 302,357；
- 标题唯一，长段落和长句跨帖重复检查通过；
- 20 字符 shingle 最大相似度 0，4 字符 shingle 最大相似度 0.045116；
- 新增 80 篇只映射到当前八类商品，没有新增笔记本主题；
- 260 篇 SQL 已通过确定性生成与静态守卫检查；执行时保留旧帖作者、创建时间、点赞和收藏，且不修改非 seed 用户帖子；
- 当前已发布的数据库/RAG 索引仍是 180 篇 Post、99 篇 GUIDE、共 1209 个 chunks；需执行新版 SQL 并重建索引后才切换到 260 篇运行时语料；
- 20 条代表性校园二手问题全部命中 Post，Top 1 均覆盖预设主题关键词；
- 2026-08-19 全量 Python Agent 测试为 103 passed、23 subtests passed。

仓库不保存演示账号明文密码。需要登录演示账号时，应在执行种子 SQL 前，于同一个 MySQL 会话中设置 `@seed_post_author_password_hash`；未设置或格式不正确时，这些账号会写入不可登录的禁用值。演示账号不得直接用于公网环境。
