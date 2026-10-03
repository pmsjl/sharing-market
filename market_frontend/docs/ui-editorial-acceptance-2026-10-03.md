# 校园集市 · 年轻杂志感修订交付

日期：2026-10-03（Asia/Shanghai）。仅修改 v1.0 前端；保留此前未提交的退出登录修复。未修改后端接口、数据库或请求/响应字段。

## 状态

- **实现：已完成本轮计划的前端改造与整站收尾。**
- **模拟接口功能验收：通过。**
- **视觉检查：已检查代表性桌面、手机、深色与异常数据页面；全量截图另做自动溢出和页面脚本异常检查。**
- **真实接口验收：未执行。** 本轮使用隔离的模拟登录态和接口；未执行真实发布、购买、支付、资料修改或消息发送。不能将下面的结果当成真实业务联调通过。
- **外部资源限制：** 离线浏览器夹具替换了外部 CDN 资源，Markdown 工具栏的远程图标等不属于本次线上资源加载验收；实际 Markdown 输入与预览已通过浏览器交互验证。

## 已落地

### 个人中心

- 桌面仅保留五个分组入口；手机通过标题旁菜单切换。菜单支持键盘打开、Escape、路由切换后恢复焦点和浏览器返回。
- 移除底部重复功能清单、侧栏私信、重复资料入口与概览归档入口。
- 不对称个人信息与余额摘要；昵称突出，收藏和私信为紧凑操作。手机余额进一步压缩，避免抢占近期交易空间。
- 最近三笔订单突出商品、状态、金额；订单记录不再伪装成详情链接，只保留一个全部订单入口。
- 订单页将联系人、电话、订单号放入可展开的订单信息；购买数量与金额保留在主要区域，支付入口不变。
- 内容页新增普通用户编写入口；钱包保留桌面账单表格和手机日期分组；资料设置使用窄栏编辑。
- 管理员仍使用管理端外壳。其账户收藏和评论保留阅读信息，但不渲染无权访问的用户端详情链接；攻略编辑导向已有管理能力，不放宽路由权限。

### 商品发布与攻略编辑

- 封面区域本身为原生按钮，点击或 Enter 均可选择单张图片；比例约 4:3，支持预览、更换、上传失败和同文件重试。
- 保留 URL 次级入口，名称、介绍、商品信息、价格与数量分组；数量仍默认 1，校园币单位直接显示。
- 新建与编辑攻略继续共用原页面与 Markdown 能力，突出标题、话题、正文层级。
- 两类编辑页只保留顶部返回和底部单一主提交操作；上传/提交锁、失败保留输入、未保存离开及来源列表恢复均保留。

### 整站与样式

- 用户端采用独立的 editorial 样式作用域，管理端不继承用户端的大标题与宽松表单间距。
- 公共弹层、私信、归档、登录注册、公告、欢迎页、404、外观设置以及六个管理页面进行了本轮构建回归。
- 保留管理表格固定操作列、表内横向滚动、手机管理导航及长弹窗内部滚动。
- 移除失效的旧账户/订单样式及重复按钮阴影、位移覆盖；本轮没有增加多层 !important。
- 首页、商品浏览/详情、Agent 和攻略阅读维持原有结构。

## 验证结果

最终源码构建标识：**837b3c0c2a718549**。构建完成于上海时间 2026-10-03 02:02；以下浏览器回归均针对该构建，之后没有修改运行时代码。

| 检查 | 结果 |
| --- | --- |
| lint（不自动修复） | 通过 |
| node --test tests/*.test.cjs | 64 passed / 0 failed |
| 原有 Agent 并发脚本 | exit 0 |
| 生产构建 | 通过；保留既有 Sass、依赖弃用、Browserslist 和包体积警告 |
| git diff --check | 通过；仅提示现有测试文件 CRLF/LF 规范化 |
| 核心用户端视觉矩阵 | 66 个截图检查点，0 页面脚本异常，0 页面级横向溢出 |
| 管理员账户与导航矩阵 | 6 个截图检查点，6 项行为断言通过 |
| 整站用户端回归 | 97 个截图检查点，8 项行为断言通过 |
| 六个管理页面及辅助页 | 27 个截图检查点，账户外壳断言通过 |
| 登录注册 | 8 个截图检查点，无脚本异常或页面级横向溢出 |
| 编辑与消息浏览器流程 | 10 项交互检查通过 |

合计 **204 个模拟数据截图检查点**，包含重复页面在不同尺寸、主题和状态下的快照，不是 204 个独立页面。核心尺寸为 320、390、768、1440px；分享浮层另覆盖 667×375、320×480。颜色检查涵盖浅色/深色和三种主题色。

新增/延续的行为覆盖：
- 五个账户目的地白名单、旧订单/发布/私信链接、动态路由首次进入查询参数、列表返回状态。
- 分组菜单的键盘、焦点恢复、浏览器返回，概览入口去重与取消伪订单链接。
- 上传失败不清空其他字段、重试同一文件、提交失败/重试、重复点击只发送一次请求。
- Markdown 输入和预览、继续编辑/放弃更改、浏览器 beforeunload 监听。
- 管理员五个账户视图没有越权用户内容链接，手机管理菜单可以导航。
- 私信发送、归档恢复/确认删除、Agent 查询参数切换、推荐清单与来源弹层、分享链接剥离咨询上下文、分享关闭后焦点恢复。
- 空订单、三笔订单、长昵称/简介、头像失败、长评论、钱包局部失败及独立重试。

## 视觉证据

- [发布页前后对比](F:/market/editorial-compare-publish.png)
- [概览前后对比](F:/market/editorial-compare-overview.png)（旧图为单笔订单，本轮为三笔订单及完整简介；不是相同数据的像素对比）
- [手机概览与发布页](F:/market/editorial-mobile-preview.png)
- [桌面概览](F:/market/editorial-overview-1440.png)
- [桌面发布](F:/market/editorial-publish-1440.png)
- [手机内容页](F:/market/editorial-content-390.png)
- [手机分组菜单](F:/market/editorial-group-menu-mobile.png)
- [深色攻略编辑](F:/market/editorial-editor-night-indigo.png)
- [上传/发布失败状态](F:/market/editorial-publish-error-mobile.png)
- [Agent 来源浮层](F:/market/editorial-site-agent-source.png)
- [手机管理导航](F:/market/editorial-admin-navigation-320.png)

## 复现与日志

从 F:/market/sharing-market-v1.0/market_frontend 执行：

~~~powershell
npm run lint -- --no-fix
node --test tests/*.test.cjs
node tests/agent-concurrency.js
npm run build
~~~

使用独立终端预览构建：

~~~powershell
& 'E:/anaconda/envs/fastapi/python.exe' -m http.server 8765 --bind 127.0.0.1 --directory F:/market/sharing-market-v1.0/market_frontend/dist
~~~

核心浏览器回归脚本已保存到项目 tests/editorialUi.e2e.cjs，不要求向项目安装新依赖；本机可使用已有运行时：

~~~powershell
$env:PLAYWRIGHT_MODULE='C:/Users/jerry/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'
$env:MARKET_CHROME_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
$env:MARKET_PREVIEW_URL='http://127.0.0.1:8765'
$env:MARKET_QA_DIR='F:/market'
node tests/editorialUi.e2e.cjs user
node tests/editorialUi.e2e.cjs admin
~~~

现场整站与编辑流程脚本保存在 F:/market/editorial-site-qa.cjs 和 F:/market/editorial-workflow.cjs，运行模式分别为 user/admin/auth 和 user；它们基于现有工作区模拟夹具。

主要结果文件：F:/market/editorial-user.json、editorial-admin.json、editorial-site-user.json、editorial-site-admin.json、editorial-site-auth.json、editorial-workflow.json。lint、单测及构建日志分别为 F:/market/editorial-lint.log、editorial-tests.log、editorial-build.log。

## 尚未验证的边界

- 真实登录态下的后端权限与资料、上传、发布、交易、消息写入流程。
- 外部 CDN 资源在真实网络中的可用性。
- 本次为 Windows Chrome 浏览器验证，未宣称 Safari/iOS 实机或全浏览器兼容性测试完成。

这些边界不以模拟成功代替；需要可用的测试环境与账号后再执行真实验收。
