# 数据库迁移

迁移脚本按文件名中的日期顺序执行。`20260908_mysql_managed_timestamps.sql` 用于已有数据库，将业务表的 `createTime`、`updateTime` 统一交给 MySQL 默认值和 `ON UPDATE` 维护。

部署步骤：

1. 备份目标数据库，并确认应用连接使用的数据库名称。
2. 在目标库执行迁移 SQL。脚本会先补齐历史 `NULL` 时间，再统一字段定义；`ALTER TABLE` 会隐式提交事务。
3. 查询 `information_schema.columns`，确认 `createTime` 的默认值为 `CURRENT_TIMESTAMP`，`updateTime` 的默认值和 `EXTRA` 中包含 `on update CURRENT_TIMESTAMP`。
4. 再部署应用代码。

校验语句：

```sql
SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.columns
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('createTime', 'updateTime')
ORDER BY TABLE_NAME, COLUMN_NAME;
```

新建数据库时直接使用上级目录的 `script.sql`，不需要重复执行迁移脚本。
