# Market Backend

`market_backend` 是[智能 AI 校园二手交易平台](../README.md)的 **Java 主后端**（Spring Boot）。浏览器只通过该服务访问业务能力，Python Agent 也通过受内部 Token 保护的只读接口查询商品和 Post 数据。

## 系统定位

```
Browser (Vue) ──→  market_backend (Java)  ──→ MySQL / Redis / Aliyun OSS
                       │   ↑
                       │   POST /agent/v1/runs  (X-Internal-Token)
                       ▼   │
                 Python Agent (FastAPI + FAISS)
```

- Java 是**唯一业务入口**：负责鉴权、业务数据、AI 会话编排，以及最终返回结果的校验和整理。
- Python Agent 不直接连业务 MySQL，只通过 Java 的只读内部接口取商品/Post 快照。
- Java 与 Python 必须配置相同的 `ai.agent.internal-token`。

## 模块职责

### 业务域

- 用户：注册（含邮箱验证）、登录/登出、个人资料、角色与封禁。
- 商品交易：商品、分类、收藏、评分、购买（校园币扣款）、订单与支付。
- 校园内容：公告、攻略 Post、点赞、收藏、嵌套评论、私信。
- 管理端：用户、商品、分类、订单、公告、攻略管理，校园币发放。
- 校园币：注册赠送（初始余额）、购买扣款、管理员发放、流水台账。

### AI 会话

- 会话：创建、续聊、归档、恢复、删除；历史上下文最多近 5 轮。
- 消息：先记录为 PENDING，再调用 Agent；调用结束后使用 CAS 将状态更新为 SUCCESS 或 FAILED，并通过行锁避免重复处理。
- 额度：按 `AiUsageDaily`/`AiUsageGlobalDaily` 每日限量（用户默认 10、平台默认 100，`ai.access` 配置，时区 Asia/Shanghai）。
- 定时任务：清理超时 PENDING 消息（每 30 秒）、释放过期未支付订单（每分钟）、同步商品浏览量（每 5 分钟）。

## 鉴权机制

登录凭证是**随机 UUID Token**（不使用 JWT 生成登录令牌），通过 `Authorization: Bearer <token>` 携带。三层校验：

| 层 | 类型 | 作用 |
| --- | --- | --- |
| `RefreshTokenInterceptor` | HandlerInterceptor | 取 Token → 查 Redis `login:token:` → 写入 `UserHolder`(ThreadLocal) → 刷新 TTL |
| `AuthInterceptorHandler` | HandlerInterceptor | 未登录返回 401；放行登录/注册/内部 AI 接口/健康检查 |
| `AuthInterceptor` | AOP `@AuthCheck` | 角色校验（BAN 拒绝、ADMIN 要求管理员） |

## 内部 AI 接口（仅 Python 调用）

不走用户登录态，靠 `X-Internal-Token` + `X-Request-Id`（反查一条 USER 消息）鉴权：

| 接口 | 路径 | 用途 |
| --- | --- | --- |
| 商品搜索 | `internal/ai/tools/commodities/search` | 关键词/分类/价格/成色过滤，仅可售（上架且有库存） |
| 用户偏好 | `internal/ai/tools/users/{userId}/preference-signals` | 根据购买和收藏记录汇总的用户偏好（已脱敏） |
| Post 校验 | `internal/ai/tools/posts/validate` | Post 版本校验 |
| Post 快照 | `internal/ai/rag/posts?afterId=&limit=` | 重建 RAG 索引时，使用游标分页获取当前帖子快照 |

Java 会通过 `AiStructuredContentAssembler` 校验推荐结果：根据模型返回的商品 ID 查询数据库，仅保留仍在售且有库存的商品；Post 引用也会再次校验版本，避免推荐已经下架或失效的内容。

## 技术栈

- Java 17、Spring Boot 3.4.3、Maven、MyBatis-Plus 3.5.9、MySQL 8
- Redis + Redisson、Aliyun OSS、Knife4j、OkHttp、Spring Boot Actuator

## 源码结构（`src/main/java/com/pmsjl`）

```
com.pmsjl/
├── controller/     REST 接口层（按业务域分组，见下）
├── service/        业务逻辑（24 个 Service）
├── mapper/         MyBatis-Plus 持久层（18 个 Mapper）
├── model/
│   ├── entity/     18 张表实体
│   ├── dto/ai/     AI 会话请求与购买需求信息
│   ├── dto/ai/internal/  Java 与 Python 之间的请求响应模型
│   ├── vo/         视图对象（含 AI 结构化内容 VO）
│   └── enums/      枚举（AI 意图、角色、状态、校园币流水类型等）
├── manager/        Java 调用 Python 的客户端（AiAgentClient）、推荐结果校验（AiStructuredContentAssembler）
├── interceptor/    三层鉴权拦截器
├── cycle/          定时任务（PENDING 清理、订单释放、浏览量同步）
├── config/         各配置组绑定与注册
├── constant/       常量与 Redis key
└── common/         统一返回 Result/错误码
```

## API 分组

所有接口带 `/api` 前缀（`server.servlet.context-path: /api`）。

| 分组 | 前缀 | 覆盖 |
| --- | --- | --- |
| 用户 | `/user` | 注册、登录、个人信息、管理 |
| 校园币 | `/campusCoin` | 钱包、管理员发放、流水 |
| 商品 | `/commodity` | 详情、增改删、购买、支付、分页 |
| 商品订单 | `/commodityOrder` | 订单、购买日历热力图 |
| 商品分类/评分 | `/commodityType`、`/commodityScore` | 分类、平均分 |
| 内容 | `/post`、`/comment`、`/notice`、`/privateMessage` | 攻略、评论、公告、私信 |
| 收藏/点赞 | `/post_favour`、`/post_thumb`、`/userCommodityFavorites` | 帖子与商品收藏点赞 |
| AI | `/ai/conversations`、`/ai/quota` | 会话、消息、额度 |
| 内部 | `internal/ai/tools`、`internal/ai/rag` | 仅供 Python Agent |
| 文件 | `/file` | OSS 上传 |

## 本地配置

从资源目录复制无密钥示例到后端根目录：

```powershell
Copy-Item src/main/resources/application.example.yml application-local.yml
```

`application-local.yml` 已被 Git 忽略，并位于 Maven 资源目录之外。至少需要检查：

| 配置组 | 主要内容 |
| --- | --- |
| `spring.datasource` | MySQL URL、用户名、密码 |
| `spring.data.redis` | Redis 地址、端口、密码 |
| `oss.client` | OSS Endpoint、Bucket、访问凭证 |
| `app.cors` | 本地前端允许来源 |
| `ai.agent` | Python Agent 地址、内部 Token、超时 |
| `market.campus-coin` | 初始校园币、管理员发放上限 |
| `ai.access` | 用户/平台每日 AI 额度 |

生产部署不复制本文件，直接用 `application-prod.yml` + 环境变量（见根目录 `.env.example`）。真实密码、OSS 凭证和内部 Token 不得提交到 Git。

## 本地启动

```powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "optional:file:./application-local.yml"
mvn spring-boot:run
```

默认地址为 `http://localhost:8102/api`。健康检查：

- 存活：`GET /api/actuator/health/liveness`
- 就绪：`GET /api/actuator/health/readiness`

## 数据库脚本

`sql/script.sql` 是适用于 MySQL 8 的完整数据库初始化脚本，包含平台业务表及索引，不包含 `CREATE DATABASE` 或演示数据。用于本地开发和课程展示的种子数据脚本位于 `sql/seed/`。

| 脚本 | 用途 |
| --- | --- |
| `script.sql` | 从空库创建完整业务表和索引 |
| `seed/01_demo_users.sql` | 写入演示账号 |
| `seed/02_commodity_types.sql` | 写入商品分类 |
| `seed/03_commodities.sql` | 写入 60 条演示商品 |
| `seed/04_posts.sql` | 写入 260 篇演示攻略帖子 |
| `seed/rollback/03_commodities.sql` | 撤销对应的商品种子数据 |

从仓库根目录初始化：

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p trade -e "source market_backend/sql/script.sql"
```

脚本未使用 `IF NOT EXISTS`，仅应在全新或已清空的 `trade` Schema 中执行。已有数据库请先备份并确认不会与现有表冲突。

如需导入本地开发数据，请在建表脚本执行成功后，按 `01_demo_users.sql`、`02_commodity_types.sql`、`03_commodities.sql`、`04_posts.sql` 的顺序执行 `sql/seed/` 下的脚本。种子数据仅用于本地开发和课程展示，不要在生产数据库执行。

## 测试

```powershell
mvn test
```

测试覆盖：配置校验（CORS/Redis/Agent 属性）、AI 服务层（会话/消息/内部工具/RAG 快照/偏好/额度）、推荐结果校验与整理、校园币与用户 Service。多数为 Mockito 单元测试；**无 User/Commodity/Post 主流程的端到端 MockMvc 测试**。

## License

本模块中由 pmsjl 持有版权的内容采用根目录的 [MIT License](../LICENSE)。
