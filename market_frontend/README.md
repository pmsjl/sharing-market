# 智能 AI 校园二手交易平台 v1.0

> 优化复现版，状态复核：2026-08-19。原项目作者：[程序员小白条](https://luoye6.github.io/)；本仓库 v1.0 由 @pmsjl 按新架构持续复现与优化。

这是一个 Vue 3 + Spring Boot + Python Agent 的校园二手交易平台。v1.0 保留市场、订单、公告和攻略社区主线，并将原来的单轮 AI/独立推荐页改造成受 Java 主后端控制的多轮 AI 导购与 GUIDE + Post RAG。

## 当前能力

### 用户端

- 注册、登录、退出与个人资料。
- 商品列表/详情、收藏、评分、购买、后续支付与个人订单。
- 公告浏览。
- 攻略 Post 列表、详情、搜索、发布、编辑、点赞、收藏与嵌套评论。
- 个人中心：我的帖子、评论、收藏、订单、购物日历与当前私信表面。
- AI 导购：多轮会话、会话归档/恢复、可选购买条件、推荐商品卡片、失败状态、RAG 来源详情与相关帖子。

### 管理端

- 用户、公告、商品、商品类别、订单与攻略管理。
- `/commodityOrder/add` 是管理员手工建单；普通用户购买不走该端点。

### 当前明确边界

- 私信仅实现 `add` 与 `my/list/page/vo`，没有未读数、撤回、删除或独立会话资源。
- AI 使用同步 JSON，不支持 SSE。
- GUIDE + Post RAG 已实现；管理员 knowledge job、文档 upsert/delete 与独立 retrieve HTTP API 仍是保留契约。
- 独立商品推荐页和 legacy 单轮 AI CRUD 不属于 v1.0 当前表面。
- Barrage/买家留言已从复现范围移除。

## 架构

```mermaid
flowchart LR
    B[Browser] -->|Authorization Bearer Token| V[Vue 3 :8080]
    V -->|/api| J[Spring Boot :8102]
    J --> M[(MySQL trade)]
    J --> R[(Redis sessions/cache)]
    J -->|/agent/v1/runs| P[FastAPI :8103]
    P -->|Responses API| O[OpenAI-compatible provider]
    P --> F[(FAISS GUIDE + Post)]
    P -->|internal read-only tools| J
```

- Vue 只访问 Java，不直连 Python。
- Java 负责鉴权、会话归属、业务数据库、真实商品/Post 字段与最终响应组装。
- Python 负责 Prompt、模型工具编排、Structured Outputs 与可降级 RAG，不直连业务 MySQL。
- 商品价格、库存、上架状态和 Post 展示字段在返回前由 Java 实时复核。

## 技术栈

### 前端

- Vue 3.2、TypeScript 4.5、Vue Router 4、Pinia
- Element Plus、Axios、ECharts、MD Editor V3
- Vue CLI 5、Sass、ESLint、Prettier

### Java 后端

- Spring Boot 3.4.3、Java 17、Maven
- MyBatis-Plus 3.5.9、MySQL 8、Redis、Redisson
- UUID Token 会话、`UserHolder`、`@AuthCheck`

### Python Agent

- FastAPI、Pydantic、OpenAI Responses 兼容客户端
- Structured Outputs、商品搜索、当前用户脱敏偏好工具
- Query Planner、Embedding、FAISS、不可变索引与 `CURRENT` 热加载

## 目录

```text
sharing-market-v1.0/
  market_frontend/    Vue 前端
  market_backend/     Java 主后端与 SQL
  ai_agent_service/   Python Agent 与 RAG
  docs/               UI 和语料进度文档
```

## 本地运行

### 1. 基础依赖

- Java 17、Maven
- Node.js 与 pnpm
- MySQL 8，schema `trade`
- Redis
- Python Conda 环境 `fastapi`

数据库、Redis、OSS、内部 Token、模型和 Embedding 地址均按本地环境配置。不要把真实密钥提交到仓库。

### 2. Java 主后端

```powershell
cd market_backend
mvn test
mvn spring-boot:run
```

默认地址：`http://localhost:8102/api`。

### 3. Python Agent

参考 `ai_agent_service/.env.example` 准备本地 `.env`，然后运行：

```powershell
cd ai_agent_service
conda run -n fastapi python -m pytest -q
conda run -n fastapi uvicorn app.main:app --host 0.0.0.0 --port 8103
```

RAG 索引需要重建时：

```powershell
conda run -n fastapi python -m app.rag.rebuild_index
```

### 4. Vue 前端

```powershell
cd market_frontend
pnpm install
pnpm dev
```

默认地址：`http://localhost:8080`。

## 验证

```powershell
# Python
cd ai_agent_service
conda run -n fastapi python -m pytest -q

# Java
cd market_backend
mvn test

# Vue
cd market_frontend
pnpm lint
pnpm build
```

2026-08-19 实测基线：

- Python：`103 passed`、`23 subtests passed`；受限工作区可能仅警告 `.pytest_cache` 不可写。
- Java：`38 tests`，全部通过。
- Vue：lint 无错误，生产构建成功。
- 生产构建仍有 42 条 Sass legacy API / `@import` 弃用与 bundle 体积警告，属于后续性能/依赖治理项。

## 关键文档

- [项目协作与当前 memory](../AGENTS.md)
- [AI Agent API 契约](../AI_AGENT_API.md)
- [AI Agent 当前复现与维护路线](../AI_AGENT_REPRODUCTION_GUIDE.md)
- [RAG 基础实现指南](../RAG_基础实现指南.md)
- [Python Agent README](../ai_agent_service/README.md)
- [UI 设计方案](../docs/UI_REDESIGN_PLAN.md)
- [UI 实施状态](../docs/UI_REDESIGN_PROGRESS.md)
- [Post 语料说明](../docs/post-corpus/README.md)

## 开发约定

- 真实前端调用优先于生成 API wrapper；未被调用的模板 CRUD 不自动进入复现范围。
- 普通用户购买走 `/commodity/buy`，需要后续支付时走 `/commodity/pay`。
- Long ID 在前端按字符串处理，避免 JavaScript 精度损失。
- 不信任模型生成的商品/Post 展示字段；必须使用 Java 查询结果。
- 不提交 `.env`、数据库密码、OSS 密钥、模型 Key 或内部服务 Token。
- 修改 v1.0 时不要同步改动只读参考项目 `../sharing-market/`。
