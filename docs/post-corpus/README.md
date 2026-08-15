# Campus post corpus

这个目录只保留校园二手帖子种子数据的安全说明，不保存采集结果、网页摘录、来源映射或生成正文副本。

采集产物曾包含公开网页中的邮箱和手机号。它们不参与项目运行，也不是 Post RAG 的数据源，因此不提交到 Git。以后重新采集时，应在本地临时目录处理，并在写入仓库前执行个人信息和凭据扫描。

运行时 Post 的唯一权威来源是数据库。用于本地重建的 SQL 文件是：

- `market_backend/sql/20260815_seed_campus_trade_posts.sql`
- `market_backend/sql/20260815_rollback_campus_trade_posts.sql`

种子 SQL 已于 **2026-08-15** 导入本地 `trade` 数据库，创建了 20 个演示作者和 180 篇帖子；重复执行没有新增重复记录。

仓库不保存演示账号的明文密码。需要登录演示账号时，应在执行种子 SQL 前，于同一个 MySQL 会话中设置 `@seed_post_author_password_hash`；未设置或格式不正确时，这些账号会写入不可登录的禁用值。演示账号不得直接用于公网环境。
