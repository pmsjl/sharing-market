// Mock-interface browser regression. See docs/ui-editorial-acceptance-2026-10-03.md.
const path = require("node:path");
const OUTPUT_DIR =
  process.env.MARKET_QA_DIR ||
  path.join(require("node:os").tmpdir(), "market-editorial-qa");
require("node:fs").mkdirSync(OUTPUT_DIR, { recursive: true });
const BASE_URL = process.env.MARKET_PREVIEW_URL || "http://127.0.0.1:8765";
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const mode = process.argv[2] || "user";
const book = {
  id: "book",
  commodityName: "活着",
  commodityAvatar: BASE_URL + "/fixture-book.svg",
  commodityTypeName: "文学书籍",
  commodityInventory: 5,
  degree: "九成新",
  price: 12,
  isListed: 1,
  adminId: "2",
  adminName: "sjlnb",
  viewNum: 26,
  favourNum: 3,
  commodityDescription:
    "读过一次，保存完好，没有划线和折角。希望这本书能继续陪伴另一位爱读书的同学。可在图书馆门口当面交易。"
};
const rec = {
  commodity: book,
  reason: "在预算内，也适合睡前慢慢读。卖家标注九成新，可以先问问是否有笔记。",
  riskTip: "见面时检查书脊、缺页和污渍，再确认是否购买。"
};
const msg = (id, role, content, sequenceNo, extra = {}) => ({
  id,
  role,
  content,
  sequenceNo,
  status: "SUCCESS",
  createTime: "2026-10-02T15:30:00",
  ...extra
});
const messages = [
  msg("u1", "USER", "想找一本适合睡前读的小说，预算 30 校园币。", 1),
  msg(
    "a1",
    "ASSISTANT",
    "## 先从这本开始吧\n\n**《活着》**在你的预算内，适合放慢节奏阅读。不过故事并不轻松，如果你希望更轻快一点，我也可以继续帮你找。\n\n- 先向卖家确认有没有笔记和破损\n- 约在校园公共区域，当面看看再决定",
    2,
    {
      structuredContent: {
        recommendations: [rec],
        sources: [
          {
            sourceType: "GUIDE",
            sourceId: "g1",
            title: "校园二手交易小指南",
            targetPath: null,
            citations: [
              {
                chunkId: "x",
                section: "图书验货",
                excerpt: "检查书页完整性与笔记情况。",
                content:
                  "当面交易时，先检查书页是否完整、有无水渍或大量笔记，再确认购买。"
              }
            ]
          }
        ],
        relatedPosts: [
          {
            postId: 7,
            title: "图书馆旁的旧书交换角",
            excerpt: "分享一次校园旧书交换体验。",
            tags: ["校园生活"]
          }
        ]
      }
    }
  ),
  msg("u2", "USER", "还有适合书桌的台灯吗？", 3),
  msg(
    "a2",
    "ASSISTANT",
    "可以看看这盏台灯。见面时试一下开关、亮度和供电接口。",
    4,
    {
      structuredContent: {
        recommendations: [
          {
            ...rec,
            commodity: {
              ...book,
              id: "lamp",
              commodityName: "宿舍阅读台灯",
              price: 25
            },
            reason: "占用桌面空间小，适合晚间阅读。"
          }
        ]
      }
    }
  )
];
const conv = (id) => ({
  id,
  title: id === "c1" ? "睡前读物与书桌好物" : "另一条咨询",
  scene: "SHOPPING",
  status: "ACTIVE",
  lastMessageTime: "2026-10-02T15:30:00",
  createTime: "2026-10-02T15:30:00"
});
const pageData = (records) => ({
  records,
  total: records.length,
  current: 1,
  pageSize: 20
});
(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.MARKET_CHROME_PATH,
    headless: true
  });
  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
      reducedMotion: "reduce"
    });
    await context.addInitScript(
      (role) => {
        if (role === "auth") {
          localStorage.clear();
          return;
        }
        localStorage.setItem("TOKEN", "preview-token");
        localStorage.setItem("role", role);
        localStorage.setItem("id", "1");
        localStorage.setItem("userName", "小林");
      },
      mode === "extra" ? "user" : mode
    );
    const errors = [],
      requests = [];
    let archived = [{ ...conv("c3"), status: "ARCHIVED" }];
    let failWallet = false,
      failPublish = true,
      failUpload = true;
    let created = 0,
      uploaded = 0,
      sent = 0;
    let scenario = "normal";
    await context.route("**/*", async (route) => {
      const u = new URL(route.request().url());
      if (u.pathname === "/fixture-book.svg")
        return route.fulfill({
          contentType: "image/svg+xml",
          body: '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="800"><rect width="800" height="800" fill="#e8e0cf"/><rect x="230" y="105" width="350" height="570" rx="2" fill="#a92d23"/><path d="M247 105v570" stroke="#761f17" stroke-width="8"/><text x="405" y="290" text-anchor="middle" font-size="85" font-family="serif" fill="#ffefd3">活着</text><text x="405" y="365" text-anchor="middle" font-size="26" fill="#ffefd3">余 华</text><path d="M330 520q75-80 150 0M330 530h150" stroke="#f5d5a5" fill="none" stroke-width="3"/></svg>'
        });
      if (
        u.hostname !== new URL(BASE_URL).hostname &&
        !u.pathname.includes("/api/")
      ) {
        if (u.pathname.includes("mermaid"))
          return route.fulfill({
            contentType: "application/javascript",
            headers: { "access-control-allow-origin": "*" },
            body: "export default {initialize(){}}"
          });
        return route.fulfill({
          contentType: u.pathname.endsWith(".css")
            ? "text/css"
            : "application/javascript",
          body: ""
        });
      }
      if (!u.pathname.includes("/api/")) return route.continue();
      requests.push(u.pathname);
      if (route.request().method() === "OPTIONS")
        return route.fulfill({
          status: 204,
          headers: {
            "access-control-allow-origin": BASE_URL,
            "access-control-allow-credentials": "true",
            "access-control-allow-headers": "*",
            "access-control-allow-methods": "*"
          }
        });
      let data = pageData([]);
      const samplePosts = [
        {
          id: "7",
          title: "图书馆旁的旧书交换角",
          content: "## 交换之前\n\n检查书页完整性，约好校园见面时间。",
          tagList: ["校园生活", "旧书交换"],
          tags: '["校园生活","旧书交换"]',
          userId: "1",
          user: { id: "1", userName: "小林" },
          thumbNum: 12,
          favourNum: 3,
          createTime: "2026-10-03T09:00:00"
        }
      ];

      const reply = (code, data, message) =>
        route.fulfill({
          contentType: "application/json",
          headers: {
            "access-control-allow-origin": BASE_URL,
            "access-control-allow-credentials": "true"
          },
          body: JSON.stringify({ code, data, message })
        });
      if (
        u.pathname.includes("/post/my/list") ||
        u.pathname.includes("/post_favour/my/list") ||
        u.pathname === "/api/post/list/page"
      )
        return reply(200, pageData(samplePosts));
      if (u.pathname.includes("/user/get"))
        return reply(200, {
          id: u.searchParams.get("id") || "1",
          userName:
            scenario === "long"
              ? "在图书馆读书也在校园分享好物的一个很长的昵称"
              : "小林",
          userRole: mode === "admin" ? "admin" : "user",
          userProfile:
            scenario === "long"
              ? "把闲置留给需要的人。".repeat(15)
              : "大二在读，喜欢旧书、摄影，和刚刚好的校园日常。",
          userAvatar:
            scenario === "broken" ? BASE_URL + "/missing-avatar.png" : "",
          userAccount: "xiaolin"
        });
      if (
        u.pathname.includes("/commodityOrder/") &&
        u.pathname.includes("/page")
      )
        return reply(
          200,
          pageData(
            scenario === "empty"
              ? []
              : [
                  {
                    id: "1001",
                    commodityName: "陪我读完大一的高等数学教材",
                    buyNumber: 1,
                    paymentAmount: 12,
                    payStatus: 1,
                    userName: "小林",
                    userPhone: "13800000000",
                    createTime: "2026-10-02T15:30:00"
                  },
                  {
                    id: "1002",
                    commodityName: "宿舍阅读台灯",
                    buyNumber: 1,
                    paymentAmount: 25,
                    payStatus: 0,
                    createTime: new Date().toISOString()
                  },
                  {
                    id: "1003",
                    commodityName: "随身的帆布包",
                    buyNumber: 2,
                    paymentAmount: 18.5,
                    payStatus: 2,
                    createTime: "2026-09-28T15:30:00"
                  }
                ]
          )
        );
      if (u.pathname.includes("/comment/myComments") && scenario === "long")
        return reply(200, [
          {
            id: "co1",
            postId: 7,
            postTitle: "图书馆旁的旧书交换角",
            content: "交换之前先检查书页完整性，约好见面的地点和时间。".repeat(
              30
            ),
            updateTime: "2026-10-02T15:30:00"
          }
        ]);
      if (u.pathname === "/api/campusCoin/me" && failWallet)
        return reply(500, null, "钱包暂时无法加载");
      if (u.pathname === "/api/commodity/add") {
        created++;
        await new Promise((r) => setTimeout(r, 350));
        return reply(
          failPublish ? 500 : 200,
          failPublish ? null : 10,
          failPublish ? "发布失败，请重试" : undefined
        );
      }
      if (u.pathname === "/api/file/upload") {
        uploaded++;
        await new Promise((r) => setTimeout(r, 250));
        return reply(
          failUpload ? 500 : 200,
          failUpload ? null : BASE_URL + "/fixture-book.svg",
          failUpload ? "上传失败" : undefined
        );
      }
      if (u.pathname.includes("/ai/conversations/c3/restore")) {
        archived = [];
        return reply(200, true);
      }
      if (
        u.pathname === "/api/ai/conversations/c3" &&
        route.request().method() === "DELETE"
      ) {
        archived = [];
        return reply(200, true);
      }
      if (u.pathname.includes("/campusCoin/my/transactions"))
        return reply(
          200,
          pageData([
            {
              id: "t1",
              amount: -12,
              balanceAfter: 88,
              transactionType: "PURCHASE",
              remark: "购买高等数学教材",
              createTime: "2026-10-03T09:12:00"
            },
            {
              id: "t2",
              amount: 100,
              balanceAfter: 100,
              transactionType: "ADMIN_GRANT",
              remark: "校园币发放",
              createTime: "2026-10-02T08:12:00"
            }
          ])
        );
      if (u.pathname.includes("/user/list"))
        return reply(
          200,
          pageData([
            {
              id: "1",
              userName: "小林",
              userRole: "user",
              userAccount: "xiaolin",
              userProfile: "把闲置留给需要的人。",
              balance: 88,
              createTime: "2026-10-02T08:12:00"
            }
          ])
        );
      if (u.pathname.includes("/privateMessage/my/conversation/"))
        return reply(
          200,
          pageData([
            {
              contactUserId: "2",
              userName: "小周",
              lastMessageId: "2",
              lastReceivedMessageId: "2",
              lastMessageContent: "在图书馆门口见。",
              lastMessageTime: "2026-10-03T09:00:00"
            }
          ])
        );
      if (u.pathname.includes("/privateMessage/my/list"))
        return reply(
          200,
          pageData([
            {
              id: "2",
              senderId: "2",
              receiverId: "1",
              content: "在图书馆门口见。",
              createTime: "2026-10-03T09:00:00"
            }
          ])
        );
      if (u.pathname.includes("/privateMessage/add")) {
        sent++;
        return reply(200, "3");
      }
      if (u.pathname.includes("/userCommodityFavorites/my/list"))
        return reply(
          200,
          pageData([{ ...book, commodityId: book.id, status: 1 }])
        );

      if (u.pathname.includes("/commodity/get/vo")) data = book;
      else if (u.pathname.includes("/user/get"))
        data = {
          id: "1",
          userName: "小林",
          userRole: "user",
          userAccount: "xiaolin",
          userProfile: "把闲置留给需要的人。",
          userAvatar: "",
          balance: 100
        };
      else if (u.pathname === "/api/ai/conversations")
        data = pageData(
          u.searchParams.get("status") === "ARCHIVED"
            ? archived
            : [conv("c1"), conv("c2")]
        );
      else if (u.pathname.includes("/ai/conversations/c1/messages"))
        data = pageData(messages);
      else if (u.pathname.includes("/ai/conversations/c2/messages"))
        data = pageData([
          msg("other", "ASSISTANT", "这是一条独立的咨询记录。", 1)
        ]);
      else if (u.pathname.includes("/quota/me"))
        data = {
          dailyLimit: 10,
          usedCount: 1,
          remaining: 9,
          globalDailyLimit: 100,
          globalUsed: 5,
          globalRemaining: 95,
          resetAt: "2026-10-03T00:00:00"
        };
      else if (u.pathname.includes("averageScore")) data = 0;
      else if (/heatmap/i.test(u.pathname)) data = [];
      else if (
        u.pathname.includes("/wallet") ||
        u.pathname === "/api/campusCoin/me"
      )
        data = { balance: 100 };
      else if (
        u.pathname.includes("/commodityOrder/") &&
        u.pathname.includes("/page")
      )
        data = pageData([
          {
            id: "1001",
            commodityName: "活着",
            buyNumber: 1,
            paymentAmount: 12,
            payStatus: 1,
            userName: "小林",
            userPhone: "13800000000",
            createTime: "2026-10-02T15:30:00"
          }
        ]);
      else if (u.pathname.includes("/notice/list"))
        data = pageData([
          {
            id: "n1",
            noticeTitle: "十月旧书交换活动开始啦",
            noticeContent:
              "这个周末，带上你读过的好书，在图书馆旁与同学交换。记得先检查书籍成色，约定好见面时间。",
            createTime: "2026-10-02",
            user: { userName: "校园市集" }
          }
        ]);
      else if (u.pathname.includes("/comment/get/")) data = [];
      else if (u.pathname.includes("/comment/myComments"))
        data = [
          {
            id: "co1",
            postId: 7,
            postTitle: "图书馆旁的旧书交换角",
            updateTime: "2026-10-02T15:30:00",
            content: "交换过几次旧书，同学们都很友好。"
          }
        ];
      else if (u.pathname.includes("/commodity/list"))
        data = pageData([
          book,
          { ...book, id: "book2", commodityName: "阅读笔记", price: 18 }
        ]);
      else if (u.pathname.includes("/commodityType/list"))
        data = pageData([
          { id: "1", typeName: "文学书籍" },
          { id: "2", typeName: "数码设备" }
        ]);
      else if (u.pathname.includes("/post/get/vo"))
        data = {
          id: "7",
          title: "图书馆旁的旧书交换角",
          content:
            "## 交换之前\n\n每本旧书都有自己的故事。周末在图书馆旁交换书籍，让阅读继续。\n\n## 看看成色\n\n检查书页、笔记与装订。\n\n## 约好见面\n\n建议选择校园公共区域。",
          tagList: ["校园生活", "旧书交换"],
          user: { id: "1", userName: "小林" },
          userId: "1",
          createTime: "2026-10-02",
          thumbNum: 12,
          favourNum: 3
        };
      else if (u.pathname.includes("/post/list"))
        data = pageData([
          {
            id: 7,
            title: "图书馆旁的旧书交换角",
            content:
              "每本旧书都有自己的故事。周末在图书馆旁交换书籍，让阅读继续。",
            tagList: ["校园生活", "旧书交换"],
            user: { userName: "小林" },
            createTime: "2026-10-02",
            thumbNum: 12,
            favourNum: 3
          }
        ]);
      await route.fulfill({
        contentType: "application/json",
        headers: {
          "access-control-allow-origin": BASE_URL,
          "access-control-allow-credentials": "true"
        },
        body: JSON.stringify({ code: 200, data })
      });
    });
    const page = await context.newPage();
    page.on("pageerror", (e) => errors.push(e.message));

    const assert = require("node:assert/strict"),
      results = [],
      checks = [];
    const go = async (path) => {
      await page.goto(BASE_URL + "/#" + path);
      await page.waitForTimeout(400);
    };
    const shot = async (name) => {
      await page.waitForTimeout(150);
      const overflow = await page.evaluate(() => ({
        document: document.documentElement.scrollWidth > innerWidth,
        main: (() => {
          const el = document.querySelector(".layout_main");
          return !!el && el.scrollWidth > el.clientWidth + 1;
        })()
      }));
      results.push({ name, url: page.url(), overflow });
      await page.screenshot({
        path: path.join(OUTPUT_DIR, "editorial-") + name + ".png"
      });
      require("node:fs").writeFileSync(
        path.join(OUTPUT_DIR, "editorial-") + mode + ".partial.json",
        JSON.stringify({ errors, results, checks }, null, 2)
      );
    };
    const check = async (name, fn) => {
      try {
        await fn();
        checks.push({ name, pass: true });
      } catch (e) {
        checks.push({ name, pass: false, error: e.message });
      }
    };
    const userPages = [
      ["/user/account", "overview"],
      ["/user/publish", "publish"],
      ["/user/account/trade?view=orders", "orders"],
      ["/user/account/trade?view=favorites", "favorites"],
      ["/user/account/trade?view=calendar", "calendar"],
      ["/user/account/content?view=posts", "content"],
      ["/user/account/content?view=comments", "comments"],
      ["/user/account/wallet", "wallet"],
      ["/user/account/settings", "settings"],
      ["/user/post/new", "editor"]
    ];
    if (mode === "user") {
      for (const [path, name] of userPages) {
        await go(path);
        for (const width of [1440, 768, 390, 320]) {
          await page.setViewportSize({
            width,
            height: width === 1440 ? 1000 : 844
          });
          await shot(name + "-" + width);
        }
      }
      await go("/user/account");
      await page.setViewportSize({ width: 390, height: 844 });
      await check(
        "overview body has no duplicate destination links or fake order links",
        async () => {
          const links = await page
            .locator(".account-overview a")
            .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("href")));
          assert.equal(new Set(links).size, links.length);
          assert.equal(
            await page.locator(".recent-order a,a.recent-order").count(),
            0
          );
          assert.equal(
            await page.locator(".account-overview a[href*=settings]").count(),
            0
          );
        }
      );
      await check(
        "mobile group menu works by keyboard and restores focus on Escape",
        async () => {
          const trigger = page.getByRole("button", {
            name: "切换个人中心分组"
          });
          await trigger.focus();
          await page.keyboard.press("Enter");
          await page
            .getByRole("menuitem", { name: "我的交易", exact: true })
            .waitFor();
          await page.keyboard.press("Escape");
          await page.waitForTimeout(200);
          assert.equal(
            await trigger.evaluate((el) => el === document.activeElement),
            true
          );
        }
      );
      await check(
        "group menu changes route, closes and restores trigger focus",
        async () => {
          const trigger = page.getByRole("button", {
            name: "切换个人中心分组"
          });
          await trigger.click();
          await page
            .getByRole("menuitem", { name: "我的交易", exact: true })
            .click();
          await page.waitForURL(/account\/trade/);
          await page.waitForTimeout(300);
          assert.equal(
            await trigger.evaluate((el) => el === document.activeElement),
            true
          );
          assert.equal(await trigger.getAttribute("aria-expanded"), "false");
        }
      );
      await page.getByRole("button", { name: "切换个人中心分组" }).click();
      await shot("group-menu-mobile");
      await page.keyboard.press("Escape");
      await check("browser back restores overview", async () => {
        await page.goBack();
        await page.waitForURL(/#\/user\/account$/);
        await page.locator(".account-overview").waitFor();
      });
      await go("/user/publish");
      await check("cover upload can be activated with Enter", async () => {
        const upload = page.getByRole("button", { name: "上传商品封面" });
        await upload.focus();
        const chooser = page.waitForEvent("filechooser");
        await page.keyboard.press("Enter");
        await chooser;
      });
      for (const value of ["empty", "long", "broken"]) {
        scenario = value;
        await go("/user/account");
        await page.reload();
        await page.waitForTimeout(500);
        for (const width of [1440, 320]) {
          await page.setViewportSize({ width, height: 844 });
          await shot("overview-" + value + "-" + width);
        }
        if (value === "long") {
          await go("/user/account/content?view=comments");
          await shot("long-comments-320");
        }
      }
      scenario = "normal";
      for (const theme of ["light", "night"])
        for (const accent of ["campus-blue", "indigo", "lake-blue"]) {
          await page.evaluate(
            ({ theme, accent }) => {
              localStorage.setItem("market-theme-mode", theme);
              localStorage.setItem("market-theme-accent", accent);
            },
            { theme, accent }
          );
          await page.reload();
          await page.waitForTimeout(250);
          await page.setViewportSize({ width: 390, height: 844 });
          for (const [path, name] of [
            ["/user/account", "overview"],
            ["/user/publish", "publish"],
            ["/user/post/new", "editor"]
          ]) {
            await go(path);
            await shot(name + "-" + theme + "-" + accent);
          }
        }
    } else {
      for (const [path, name] of [
        ["/user/account", "overview"],
        ["/user/account/content?view=posts", "content"],
        ["/user/account/content?view=favorites", "saved-posts"],
        ["/user/account/content?view=comments", "comments"],
        ["/user/account/trade?view=favorites", "saved-products"]
      ]) {
        await go(path);
        await page.setViewportSize({ width: 390, height: 844 });
        await shot("admin-" + name);
        await check(
          "admin " + name + " has no disallowed user-content links",
          async () => {
            assert.equal(
              await page
                .locator(
                  '.account-body a[href*="/user/post"],.account-body a[href*="/user/commodity"],.account-body a[href*="/user/agentGuide"]'
                )
                .count(),
              0
            );
            assert.equal(await page.locator(".market-consumer").count(), 0);
          }
        );
      }
      await go("/admin/commodityManagement");
      await page.setViewportSize({ width: 320, height: 844 });
      await page.getByRole("button", { name: "打开管理导航" }).click();
      await shot("admin-navigation-320");
      await check("admin mobile menu opens and can navigate", async () => {
        await page
          .getByRole("menuitem", { name: "公告管理", exact: true })
          .click();
        await page.waitForURL(/admin\/noticeManagement/);
      });
    }
    require("node:fs").writeFileSync(
      path.join(OUTPUT_DIR, "editorial-") + mode + ".json",
      JSON.stringify({ errors, results, checks }, null, 2)
    );
    console.log(
      JSON.stringify(
        {
          errors,
          overflows: results.filter(
            (x) => x.overflow.document || x.overflow.main
          ),
          checks,
          count: results.length
        },
        null,
        2
      )
    );
    if (
      errors.length ||
      results.some((x) => x.overflow.document || x.overflow.main) ||
      checks.some((x) => !x.pass)
    )
      process.exitCode = 1;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
