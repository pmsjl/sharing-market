# 后端部署指南

生产环境由两个独立服务组成：

1. `market_backend`：Java 17 / Spring Boot，向浏览器提供 `/api`。
2. `ai_agent_service`：Python 3.11 / FastAPI，仅供 Java 调用。

MySQL、Redis 和 Aliyun OSS 是外部托管依赖，不包含在应用容器内。两个应用服务必须配置同一个高强度 `AI_AGENT_INTERNAL_TOKEN`。

## 部署前置条件

- 已创建 MySQL `trade` 业务库，并完成原项目基础 schema 初始化。
- 按时间顺序执行 `market_backend/sql/` 中适用于当前环境的 AI 增量脚本。
- 已准备 Redis 和 Aliyun OSS。
- 已准备兼容 OpenAI Responses 的模型服务；启用 RAG 时还需独立的 Embedding 服务。

仓库当前没有完整业务库的基线建表脚本，因此不会自动对空数据库执行初始化。`market_backend/sql/` 中还包含种子和回滚脚本，不能整目录无差别执行。

## Java 服务

以 `market_backend/` 为 Docker 构建上下文：

```bash
docker build -t sharing-market-backend .
docker run --env-file .env -p 8102:8102 sharing-market-backend
```

复制 `.env.example` 后填写真实值，或在部署平台逐项设置同名环境变量。生产运行必须启用：

```text
SPRING_PROFILES_ACTIVE=prod
```

核心必填变量：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`
- `OSS_ACCESS_KEY`、`OSS_SECRET_KEY`、`OSS_BUCKET`、`OSS_HOST`
- `AI_AGENT_BASE_URL`、`AI_AGENT_INTERNAL_TOKEN`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

平台注入的 `PORT` 优先于 `SERVER_PORT`。CORS 使用逗号分隔的精确来源或受限域名模式，例如：

```text
https://market.example.com,https://*.sharing-market.pages.dev
```

禁止配置全局 `*`。服务健康检查：

- 存活：`GET /api/actuator/health/liveness`
- 就绪：`GET /api/actuator/health/readiness`

## Python Agent

以 `ai_agent_service/` 为 Docker 构建上下文：

```bash
docker build -t sharing-market-agent .
docker run --env-file .env -p 8103:8103 sharing-market-agent
```

核心必填变量：

- `AI_AGENT_INTERNAL_TOKEN`
- `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`
- `JAVA_BACKEND_BASE_URL`

`JAVA_BACKEND_BASE_URL` 只填写 Java 服务根地址，不要追加 `/api`。平台的 `PORT` 会覆盖 `AI_AGENT_PORT`。

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
