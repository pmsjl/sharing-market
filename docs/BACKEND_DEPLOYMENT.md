# 后端部署指南

生产环境由两个独立服务组成：

1. `market_backend`：Java 17 / Spring Boot，向浏览器提供 `/api`。
2. `ai_agent_service`：Python 3.11 / FastAPI，仅供 Java 调用。

MySQL、Redis 和 Aliyun OSS 是外部托管依赖，不包含在应用容器内。两个应用服务必须配置同一个高强度 `AI_AGENT_INTERNAL_TOKEN`。

## 部署前置条件

- 已创建 MySQL `trade` 业务库，并完成原项目基础 schema 初始化。
- 按时间顺序执行 `market_backend/sql/` 中适用于当前环境的 schema 增量脚本。升级到 2026-08-30 版本时，必须在启动新版 Java 服务前执行 `market_backend/sql/20260830_add_campus_coin_and_ai_quota.sql`；该脚本会保留现有余额、补期初流水，并创建校园币流水及 AI 日配额表。
- 已准备 Redis 和 Aliyun OSS。
- 已准备兼容 OpenAI Responses 的模型服务；启用 RAG 时还需独立的 Embedding 服务。

仓库当前没有完整业务库的基线建表脚本，因此不会自动对空数据库执行初始化。`market_backend/sql/` 中还包含种子和回滚脚本，不能整目录无差别执行。

## Java 服务

以 `market_backend/` 为 Docker 构建上下文：

```bash
docker build -t sharing-market-backend .
docker run --env-file .env.production -p 8081:8081 sharing-market-backend
```

复制 `.env.example` 为未跟踪的 `.env.production` 后填写真实值，或在部署平台逐项设置同名环境变量。端口不需要写进这个文件：容器默认监听 `8081`，云平台提供 `PORT` 时会自动覆盖。生产运行必须启用：

```text
SPRING_PROFILES_ACTIVE=prod
```

核心必填变量：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`（或用 `SPRING_DATA_REDIS_URL` 提供连接地址与认证）
- `OSS_ACCESS_KEY`、`OSS_SECRET_KEY`、`OSS_BUCKET`、`OSS_HOST`
- `AI_AGENT_BASE_URL`、`AI_AGENT_INTERNAL_TOKEN`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

平台注入的 `PORT` 优先于生产配置中的默认端口 `8081`。CORS 使用逗号分隔的精确来源或受限域名模式，例如：

```text
https://market.example.com,https://*.sharing-market.pages.dev
```

禁止配置全局 `*`。服务健康检查：

Redis 的 Spring Data、Session 和 Redisson 三种用法现在共用同一份连接配置。托管 Redis 如果要求 TLS，设置 `REDIS_SSL_ENABLED=true`；如果服务商提供用户名，同时填写 `REDIS_USERNAME`。也可以使用 Spring Boot 原生的 `SPRING_DATA_REDIS_URL=rediss://主机:端口` 代替分项连接参数；需要认证时优先使用上面的用户名和密码环境变量，库号仍由 `REDIS_DATABASE` 指定。不要把 Redis 地址写进 Java 代码。

- 存活：`GET /api/actuator/health/liveness`
- 就绪：`GET /api/actuator/health/readiness`

## Python Agent

以 `ai_agent_service/` 为 Docker 构建上下文：

```bash
docker build -t sharing-market-agent .
docker run --env-file .env.production -p 8082:8082 sharing-market-agent
```

复制 `.env.example` 为未跟踪的 `.env.production` 后填写真实值。不要直接把本地 `.env` 交给生产容器：本地直接运行默认使用 `8103`，Docker 容器默认使用 `8082`，云平台仍可通过 `PORT` 覆盖。

核心必填变量：

- `AI_AGENT_INTERNAL_TOKEN`
- `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`
- `JAVA_BACKEND_BASE_URL`

`JAVA_BACKEND_BASE_URL` 只填写 Java 服务根地址，不要追加 `/api`。平台的 `PORT` 会覆盖容器默认端口 `8082`。

探针地址：

- 存活：`GET /live`
- 就绪：`GET /ready`
- 诊断摘要：`GET /health`

## 启用 RAG

启用前设置 Embedding 变量，并把 `RAG_INDEX_DIR` 挂载到持久化磁盘。容器默认目录是 `/data/rag_index`。

在同一运行环境中执行一次：

```bash
python -m app.rag.rebuild_index
```

索引完成后再设置 `RAG_ENABLED=true`。Embedding 模型、维度或语料发生变化后必须重新构建。未挂载持久化磁盘时，重新部署会丢失索引，`/ready` 将返回 503。

## 校园币与 AI 日配额配置

`20260830_add_campus_coin_and_ai_quota.sql` 执行完成后，可通过以下环境变量调整额度；未配置时使用右侧默认值：

- `CAMPUS_COIN_INITIAL_BALANCE=1000.00`：新用户注册赠送校园币。
- `CAMPUS_COIN_MAX_ADMIN_GRANT=100000.00`：管理员单次发放上限。
- `AI_USER_DAILY_LIMIT=10`：单个用户每日可派发的 Agent 请求数。
- `AI_GLOBAL_DAILY_LIMIT=100`：全平台每日可派发的 Agent 请求数。
- `AI_QUOTA_TIMEZONE=Asia/Shanghai`：日额度归零所使用的自然日时区。

这些限制由 MySQL 原子更新和用户行锁强制执行，不依赖单个 Java 进程的内存状态。若迁移未执行，注册、钱包或 AI 咨询会因缺少新表而失败。
## 推荐部署顺序

1. 创建 MySQL、Redis、OSS 并初始化数据库。
2. 在部署平台预先分配 Java 与 Python 的内部/公开地址。
3. 生成内部 Token，并同时写入两个服务。
4. 部署 Python Agent，确认 `/live` 正常。
5. 部署 Java，确认 Actuator readiness 为 `UP`。
6. 如启用 RAG，挂载卷、重建索引并检查 Python `/ready`。
7. 将 Java API 地址写入前端 `VUE_APP_API_BASE_URL`，重新构建前端。

不要提交真实 `.env`、本地 `application.yml`、数据库转储、模型密钥或 OSS 凭据。Java Docker 构建上下文会主动排除本地 `application*.yml`，只保留无密钥的生产 profile 和示例文件。

本地 Java 配置保存在未跟踪的 `market_backend/application-local.yml`，通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./application-local.yml` 显式加载。它位于 Maven 资源目录之外，因此普通 `mvn package` 不会把本地凭据写入 JAR。
