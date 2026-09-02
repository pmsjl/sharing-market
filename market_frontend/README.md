# Market Frontend

`market_frontend` 是[智能 AI 校园二手交易平台](../README.md)的 **Vue 3 前端**，包含普通用户页面和管理端页面。浏览器只访问 Java API，不直接调用 Python Agent 或数据库。

## 前端如何与后端通信

```
Vue (8080) ──axios──→ Java API (8102/api) ──→ MySQL/Redis
                        ↑
                Authorization: Bearer <UUID Token>
```

- `VUE_APP_API_BASE_URL` 指向 Java 服务根地址（默认 `http://localhost:8102`）。**接口路径已含 `/api`，不要重复追加**。
- 登录成功后 Java 返回 **UUID Token**，前端存 localStorage 并在请求头 `Authorization: Bearer <token>` 携带；登录态由 Java + Redis 校验，不是前端生成的 JWT。
- 路由按 `localStorage.role` 区分用户端 `/user/*` 与管理端 `/admin/*`，越权访问被守卫拦截。

## 页面能力

### 用户端

- 登录、注册、个人中心（个人信息/我的攻略/收藏/评论/订单/校园币流水/购物日历/收藏商品/已归档对话/私信）。
- 商品：列表浏览、详情、收藏、评分、购买、支付、我的订单。
- 内容：公告墙、交易攻略（搜索/发布/详情/嵌套评论）。
- 智能导购：多轮 AI 会话（详见下节）。

### 管理端

- 用户管理、商品管理、商品类别管理、商品订单管理。
- 公告管理、攻略管理、校园币发放。

## 智能导购（AI Agent Guide）

实现位于 `src/views/user/agentGuide/index.vue`（约 3700 行，本前端最复杂的页面）。

### 交互模式：同步请求 + 打字机动画

**不是 SSE/流式**：前端一次性 `POST /api/ai/conversations/{id}/messages`，Java 返回完整 `AiChatVO` 后，前端用 `requestAnimationFrame` 按设定速度逐字显示回答（打字机效果），支持打字速度档位（含"立即"）。

- 因 Java 最长等 Python Agent 整轮 120 秒，AI 接口的 axios 超时单独覆盖为 `160000` ms。
- 失败消息标记 `FAILED` 且可重试，成功后重新提交原用户消息。

### 引用来源与结构化内容

回答由 `structuredContent.sources` 驱动展示：

- **回答参考来源**：`sourceType ∈ GUIDE/COMMODITY/POST/NOTICE/COMMENT`，最多展示 8 条、每条最多 2 条引用；GUIDE 来源点击打开引用片段弹窗，其他跳转详情。
- **推荐商品**：渲染匹配商品卡（匹配度/理由/风险提示/价格）。
- **相关帖子**：被回答引用的帖子打"引用"徽标并排到最前。
- Markdown 正文用 `md-editor-v3` 渲染。

### 购物上下文与额度

- 每次发送消息携带 `shoppingContext`（预算区间/使用场景/偏好标签/避雷项），并在响应后回填表单。
- 展示今日/全局剩余 AI 额度（`GET /api/ai/quota/me`）；额度用尽（错误码 40901/42901/42902）做保护性拦截。
- 会话支持归档/恢复/删除；归档会话在个人中心"已归档对话"查看。

## 路由与角色

`src/router/routes.ts` 定义常量路由（登录/注册/欢迎/404）+ 异步路由（用户端与管理端）。`src/permission.ts` 全局守卫：

- 未登录 → 跳登录页。
- 按角色（`GET_ROLE()`）动态 `addRoute` 注入对应菜单路由。
- `utils/roleHome.ts` 做越权拦截：admin 只能访问 `/admin/*`（+个人主页），user 只能访问 `/user/*`。

```
常量路由      /login /register /index(欢迎) /404
用户端异步    /user/home /user/commodity(+详情) /user/agentGuide
             /user/post(+详情) /user/notice /user/orders /user/account
管理端异步    /admin/userManagement /admin/commodityManagement
             /admin/commodityTypeManagement /admin/commodityOrderManagement
             /admin/noticeManagement /admin/postManagement
```

## 状态管理（Pinia）

| Store | 管理内容 |
| --- | --- |
| `useUserStore` | token、用户名、头像、角色、动态菜单路由、按钮权限 |
| `useLayOutSettingStore` | 菜单折叠、页面刷新、AI 导购专注模式 |

token/角色等同时持久化到 localStorage（`utils/token.ts`）。

## 源码结构

```
src/
├── main.ts            应用入口：ElementPlus(zh-cn)/Pinia/router/主题
├── permission.ts      全局路由守卫（角色动态路由）
├── api/               每后端 Controller 一个 API 模块 + index 汇总
├── components/        公共组件（CommodityCard、Post、PrivateMessage、
│                      CalendarChart、AuthMarketLayout 等，全局注册）
├── layout/            用户端/管理端外壳（logo/menu/tabbar/main）
├── router/            路由定义
├── store/             Pinia（user、setting）
├── styles/            全局 scss（含变量注入）
├── utils/             request/token/roleHome/theme/motion/eventBus
└── views/             页面（用户端/管理端/登录注册欢迎）
```

关键公共组件：
- `CommodityCard`：商品卡（购买/收藏/评分/分享二维码/联系卖家）。
- `Post`/`Comment` 系列：攻略与嵌套评论。
- `PrivateMessage`：私信气泡（含 emoji 选择器）。
- `CalendarChart`：购物日历（ECharts）。
- `ArchivedAiConversations`：已归档 AI 会话。
- `AuthMarketLayout`：登录/注册外壳（插画 + 首页精选商品）。

## 技术栈

- Vue 3.3、TypeScript 4.5（`strict: true`）、Vue Router 4（hash 模式）、Pinia
- Element Plus、Axios、ECharts、MD Editor V3、GSAP、mitt
- Vue CLI 5、Sass、ESLint、Prettier
- Node.js 22.16.0、npm 10.9.2（见 `.node-version` / `package.json`）

## 主题

`utils/theme.ts` 支持明暗主题（light/night）+ 三档强调色（campus-blue/indigo/lake-blue），通过 CSS 变量 `--market-*` 注入并同步 Element Plus 主色。

## 本地开发

```powershell
Copy-Item .env.development.local.example .env.development.local
# 按本机 Java 地址修改 VUE_APP_API_BASE_URL。
npm ci
npm run dev
```

页面默认运行在 `http://localhost:8080`。

## 环境变量

可提交模板为 `.env.development.local.example`。本地 `.env.development.local` 已被 Git 忽略。

| 变量 | 用途 |
| --- | --- |
| `VUE_APP_API_BASE_URL` | Java 服务根地址（不含 `/api`） |
| `VUE_APP_TITLE` | 页面标题 |
| `OPENAPI_SCHEMA_URL` | 仅供 OpenAPI 代码生成读取 Schema |

Vue CLI 在构建阶段注入 `VUE_APP_*` 变量。不要在可提交配置或源码中写入真实生产地址、密钥和内网信息。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动本地开发服务器 |
| `npm run build` | 生成生产静态资源到 `dist/` |
| `npm run lint` | 执行 ESLint |
| `npm run openapi` | 按 `OPENAPI_SCHEMA_URL` 生成 API 代码到 `src/api/generated` |

前端无单元测试脚本（无 jest/vitest 配置）。验证前端：

```powershell
npm ci
npm run lint
npm run build
```

当前生产构建可能显示 Sass 弃用和 Bundle 体积警告；这些警告不等同于构建失败。

## License

本目录中由 pmsjl 持有版权的内容采用 [MIT License](LICENSE)（与根目录一致）。第三方 npm 依赖仍受其各自许可证约束（见 `package-lock.json`）。
