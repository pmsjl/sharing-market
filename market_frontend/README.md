# Market Frontend

`market_frontend` 是[智能 AI 校园二手交易平台](../README.md)的 **Vue 3 前端**，包含普通用户页面和管理端页面。浏览器只访问 Java API，不直接调用 Python Agent 或数据库。

## 前端如何与后端通信

```
Vue (8080) ──axios──→ Java API (8102/api) ──→ MySQL/Redis
                        ↑
                Authorization: Bearer <UUID Token>
```

- `VUE_APP_API_BASE_URL` 指向 Java 服务根地址（默认 `http://localhost:8102`）。**接口路径已含 `/api`，不要重复追加**。
- 登录成功后 Java 返回 **UUID Token**，前端存 localStorage 并在请求头 `Authorization: Bearer <token>` 携带；登录态由 Java + Redis 校验。
- 路由按 `localStorage.role` 区分用户端 `/user/*` 与管理端 `/admin/*`，越权访问被守卫拦截。

## 页面能力

### 用户端

- 登录、注册、退出；认证页保留商品橱窗与表单布局，统一品牌为“校园集市 / SHARING MARKET”。
- 个人中心分为个人概览、我的交易、我的内容、校园币、资料设置；桌面使用分组侧栏，手机使用标题旁分组菜单。每个分组按需加载，失败可局部重试。
- 概览包含个人信息、余额摘要、收藏与私信入口及最近三笔订单；交易分组包含订单、收藏商品和购物日历，内容分组包含我的攻略、收藏攻略和评论活动列表。
- 校园币采用桌面流水表格与手机日期分组账单，金额使用“数字＋校园币”，钱包与流水固定两位小数。
- 商品：列表浏览、详情、收藏、评分、购买、支付和独立发布页；联系卖家融入卖家信息行，手机购买操作区位于主导航上方。
- 内容：公告、交易攻略（搜索/发布/编辑/详情/嵌套评论）；新建和编辑复用 Markdown 编辑页，支持编写/预览切换。
- 编辑：单封面上传、图片链接次级入口、字段校验、上传/提交状态及重复提交保护；失败保留输入，未保存修改触发站内离开确认或浏览器原生刷新提醒，不新增持久化草稿。
- 私信：全局消息抽屉、联系人列表、双方气泡、历史加载与发送状态；未读提示基于当前账号在本浏览器保存的阅读位置，不代表服务端已读回执。
- 智能导购：多轮 AI 会话（详见下节）。

### 管理端

- 用户管理、商品管理、商品类别管理、商品订单管理。
- 公告管理、攻略管理、校园币发放。

## 智能导购（AI Agent Guide）

实现位于 `src/views/user/agentGuide/index.vue`，消息轮询逻辑位于 `src/utils/aiMessagePolling.ts`。

### 交互模式：后台生成 + 消息轮询 + 打字机动画

首轮创建和后续发送返回已持久化的 `AiChatVO`，助手通常为 `PENDING`；后台完成后，前端通过现有消息分页接口获取 `SUCCESS` 或 `FAILED`。成功回答仍使用 `requestAnimationFrame` 按设定速度逐字显示，支持打字速度档位（含"立即"），不提供流式输出。

- 发送接口超时为 30 秒，不等待生成。每个待完成会话维护一个串行轮询，每次查询结束后间隔 2 秒；查询断网时保留 PENDING，10 秒后重试，不自动重复提交。
- 刷新或重新进入会话时恢复 PENDING 的等待状态；切换会话继续更新缓存，页面卸载停止轮询但不取消后台生成。
- 只有数据库确认 FAILED 才提供生成重试；POST 网络失败属于提交结果未知，提示加载会话确认，避免自动重复生成。线程池满载直接返回可重试 FAILED。
- 使用真实助手消息 ID 定位结果，必要时继续查询历史页；轮询按 ID 合并，不丢失已加载历史。等待期间保持同一会话的发送、重试、归档和删除限制。

轮询及页面状态测试：`node --test tests/aiMessagePolling.test.cjs tests/agentGuideAsync.test.cjs`（使用已有 TypeScript 和 Vue 依赖，无新增测试框架）。

### 引用来源与结构化内容

前端根据 `structuredContent.sources` 展示引用来源：

- **回答参考来源**：`sourceType ∈ GUIDE/COMMODITY/POST/NOTICE/COMMENT`，最多展示 8 条、每条最多 2 条引用；GUIDE 来源点击打开引用片段弹窗，其他跳转详情。
- **推荐商品**：通过“查看本轮好物”打开与该助手消息绑定的清单，展示商品、推荐理由、验货提醒和校园币价格；新回答不会替换正在查看的旧清单。
- **来源与相关攻略**：默认展示数量入口，点击或键盘激活后独立展开；状态按会话、消息和内容类型隔离，刷新后默认折叠。相关攻略中的引用帖子保留引用标识并排在最前。
- Markdown 正文用 `md-editor-v3` 渲染。

### 购买需求与额度

- 每次发送消息都会携带 `shoppingContext`（预算区间/使用场景/偏好标签/避雷项），并在收到响应后回填表单。
- 展示用户今日剩余额度和平台当日剩余额度（`GET /api/ai/quota/me`）；额度用尽时（错误码 40901/42901/42902）直接阻止继续发送。
- 会话支持归档/恢复/删除；归档记录位于 Agent 历史记录的“已归档”入口，支持 `/user/agentGuide?history=archived` 直达，删除需确认。

## 路由与角色

`src/router/routes.ts` 定义常量路由（登录/注册/欢迎/404）+ 异步路由（用户端与管理端）。`src/permission.ts` 全局守卫：

- 未登录 → 跳登录页。
- 按角色（`GET_ROLE()`）动态 `addRoute` 注入对应菜单路由。
- `utils/roleHome.ts` 做越权拦截：admin 可访问 `/admin/*` 和明确注册的五个账户页面，账户仍使用管理端外壳；普通用户不能进入管理路由。欢迎页等公共路由按现有守卫放行，后端继续执行业务鉴权。

| 页面 | 路由与默认视图 |
| --- | --- |
| 公共页面 | `/login`、`/register`、`/welcome`、`/404` |
| 首页、商品与公告 | `/user/home`、`/user/commodity`、`/user/commodity/detail/:id`、`/user/notice` |
| 个人概览 | `/user/account` |
| 我的交易 | `/user/account/trade?view=orders`；另支持 `favorites`、`calendar` |
| 我的内容 | `/user/account/content?view=posts`；另支持 `favorites`、`comments` |
| 校园币、资料设置 | `/user/account/wallet`、`/user/account/settings` |
| 商品发布 | `/user/publish` |
| 攻略 | `/user/post`、`/user/post/:id`、`/user/post/new`、`/user/post/:id/edit` |
| Agent | `/user/agentGuide`；`conversationId` 定位会话，`history=archived` 打开归档记录 |
| 管理页面 | `/admin/userManagement`、`/admin/commodityManagement`、`/admin/commodityTypeManagement`、`/admin/commodityOrderManagement`、`/admin/noticeManagement`、`/admin/postManagement` |

兼容与返回状态：

- `/user/orders` 重定向至 `/user/account/trade?view=orders`。
- `/user/commodity?publish=1` 转入发布页；编辑页通过受白名单约束的 `returnTo` 返回来源列表。
- `/user/account?tab=chat&contactUserId=…` 继续打开指定私信。
- 商品和攻略列表将搜索、筛选、排序（页面支持时）及页码同步到路由查询参数，编辑返回和浏览器后退可恢复。
- 商品与攻略的返回咨询链接保留会话上下文；公开分享链接清除 `from`、`conversationId` 等咨询参数。

## 状态管理（Pinia）

| Store | 管理内容 |
| --- | --- |
| `useUserStore` | token、用户名、头像、角色、动态菜单路由、按钮权限 |
| `useLayOutSettingStore` | 菜单折叠、页面刷新、AI 导购专注模式 |
| `usePrivateMessageStore` | 全局私信抽屉、联系人、消息提示、当前账号的本地阅读位置及会话内发送状态 |

token/角色等同时持久化到 localStorage（`utils/token.ts`）。

## 源码结构

```
src/
├── main.ts            应用入口：ElementPlus(zh-cn)/Pinia/router/主题
├── permission.ts      全局路由守卫（角色动态路由）
├── api/               每个后端 Controller 对应一个 API 模块，再由 index 汇总
├── components/        公共组件（CommodityCard、Post、PrivateMessage、
│                      CalendarChart、AuthMarketLayout 等，按需引入；
│                      SvgIcon 与 Element Plus 图标全局注册）
├── layout/            用户端/管理端外壳（logo/menu/tabbar/main）
├── router/            路由定义
├── composables/       按需加载、未保存修改提醒等共享行为
├── store/             Pinia（user、setting、privateMessage）
├── styles/            全局 scss（含变量注入）
├── utils/             request/token/roleHome/theme/motion/eventBus
└── views/             页面（用户端/管理端/登录注册欢迎）
```

关键公共组件：

- `CommodityCard`：商品详情（购买/收藏/评分/分享二维码/卖家联系与手机操作区）。
- `Post`/`Comment` 系列：攻略与嵌套评论。
- `PrivateMessage`：私信气泡（含 emoji 选择器）。
- `CalendarChart`：购物日历（ECharts）。
- `AgentSelection` / `ArchivedAiConversations`：本轮推荐清单与历史归档记录。
- `AuthMarketLayout`：登录/注册外壳（静态商品橱窗＋表单，图片失败时提供占位）。

## 技术栈

- Vue 3.3、TypeScript 4.5（`strict: true`）、Vue Router 4（hash 模式）、Pinia
- Element Plus、Axios、ECharts、MD Editor V3、GSAP、mitt
- Vue CLI 5、Sass、ESLint、Prettier
- Node.js 22.16.0、npm 10.9.2（见 `.node-version` / `package.json`）

## 主题

`utils/theme.ts` 支持明暗主题（light/night）+ 三档强调色（campus-blue/indigo/lake-blue），通过 CSS 变量 `--market-*` 注入并同步 Element Plus 主色。

## 视觉与响应式规则

- 首页、商品、攻略和个人中心以开放内容行、间距和文字层级组织信息；校园贴纸仅用于少量装饰，主操作采用小圆角，卖家联系、返回等次级操作采用无框文字。
- 用户端样式在 `styles/consumer.scss`、`product.scss`、`editorial.scss` 中组织，管理端保留紧凑表格与独立外壳。不要全局移除输入框、聊天气泡和可删除标签的必要边界。
- 公共弹窗、抽屉限制视口宽高并支持内部滚动；分享浮层与卖家联系避免裁切、文字拥挤，键盘焦点保持可见。
- 手机商品操作区在 760px 及以下显示，与主导航共享高度/安全区变量；加载或失败时不显示，售罄和下架时禁止购买，本人商品隐藏联系入口。
- Agent 正文为 16px，主要辅助信息约 13px；字体与主题变量同时覆盖明暗模式及三种强调色。

## 本地开发

```powershell
Copy-Item .env.development.local.example .env.development.local
# 按本机 Java 地址修改 VUE_APP_API_BASE_URL。
npm ci
npm run dev
```

页面默认运行在 `http://localhost:8080`。

## 环境变量

环境变量模板为 `.env.development.local.example`，本地配置写入 `.env.development.local`。

| 变量 | 用途 |
| --- | --- |
| `VUE_APP_API_BASE_URL` | Java 服务根地址（不含 `/api`） |
| `VUE_APP_TITLE` | 模板保留变量，当前未读取；运行时页面标题由 `src/setting.ts` 配置 |
| `OPENAPI_SCHEMA_URL` | 仅供 OpenAPI 代码生成读取 Schema |

Vue CLI 在构建阶段注入 `VUE_APP_*` 变量。不要在可提交配置或源码中写入真实生产地址、密钥和内网信息。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动本地开发服务器 |
| `npm run build` | 生成生产静态资源到 `dist/` |
| `npm run lint` | 执行 ESLint |
| `npm run openapi` | 按 `OPENAPI_SCHEMA_URL` 生成 API 代码到 `src/api/generated` |

验证前端：

```powershell
npm ci
npm run lint -- --no-fix
node --test tests/*.test.cjs
node tests/agent-concurrency.js
npm run build
```

测试覆盖异步等待与断网恢复、推荐消息绑定、来源折叠隔离、路由权限与兼容、编辑返回状态、防重复提交、主题和退出登录等行为。

## License

本目录中由 pmsjl 持有版权的内容采用 [MIT License](LICENSE)（与根目录一致）。第三方 npm 依赖仍受其各自许可证约束（见 `package-lock.json`）。
