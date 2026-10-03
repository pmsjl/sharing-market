const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const { createRouter, createMemoryHistory } = require("vue-router");
function load(file, imports = {}) {
  const source = fs.readFileSync(path.join(__dirname, "../src", file), "utf8");
  const sandbox = {
    exports: {},
    document: { title: "" },
    require: (name) => {
      if (imports[name]) return imports[name];
      throw new Error("Unexpected import: " + name);
    }
  };
  vm.runInNewContext(
    ts.transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2020
      }
    }).outputText,
    sandbox
  );
  return sandbox.exports;
}
const navigation = load("utils/marketNavigation.ts");
const roles = load("utils/roleHome.ts", { "./marketNavigation": navigation });
const routes = load("router/routes.ts", {
  "@/utils/marketNavigation": navigation
});
const safeRoutes = (items) =>
  items.map((item) => ({
    ...item,
    ...(item.component ? { component: { template: "<div />" } } : {}),
    ...(item.children ? { children: safeRoutes(item.children) } : {})
  }));
const makeRouter = (role) =>
  createRouter({
    history: createMemoryHistory(),
    routes: safeRoutes([
      ...routes.constantRoute,
      ...(role === "admin" ? routes.asnycAdminRoute : routes.asnycUserRoute),
      routes.anyRoute
    ])
  });

test("administrator account allowlist is exact; ordinary users cannot enter administration", () => {
  for (const p of navigation.ACCOUNT_PATHS)
    assert.equal(roles.isPathAllowedForRole(p, "admin"), true);
  for (const p of [
    "/user/account/fake",
    "/user/accounting",
    "/user/publish",
    "/user/post/7/edit"
  ])
    assert.equal(roles.isPathAllowedForRole(p, "admin"), false);
  assert.equal(
    roles.isPathAllowedForRole("/admin/userManagement", "user"),
    false
  );
});
test("legacy order link keeps page and resolves to orders group", async () => {
  const router = makeRouter("user");
  await router.push("/user/orders?page=3");
  assert.equal(router.currentRoute.value.path, "/user/account/trade");
  assert.equal(router.currentRoute.value.query.view, "orders");
  assert.equal(router.currentRoute.value.query.page, "3");
});
test("new and edit routes do not resolve as post details", () => {
  const router = makeRouter("user");
  assert.notEqual(
    router.resolve("/user/post/new").name,
    router.resolve("/user/post/7").name
  );
  assert.notEqual(
    router.resolve("/user/post/7/edit").name,
    router.resolve("/user/post/7").name
  );
});
test("admin account child stays mounted in admin layout", () => {
  const route = makeRouter("admin").resolve("/user/account/wallet");
  assert.equal(route.matched[0].path, "/admin");
  assert.equal(route.matched.length, 2);
});
test("return target preserves filters, sorting, tags and pagination without allowing foreign routes", () => {
  for (const target of [
    "/user/commodity?q=book&category=2&sort=priceAsc&page=3",
    "/user/post?q=book&tags=a&tags=b&page=4",
    "/user/account/content?view=posts&page=2"
  ])
    assert.equal(navigation.editorReturnPath(target, "/user/post"), target);
  for (const target of [
    "https://evil.example",
    "//evil.example",
    "/admin",
    "/user/commodity#bad",
    "/user/post/7",
    "/user/post\\x"
  ])
    assert.equal(
      navigation.editorReturnPath(target, "/user/post"),
      "/user/post"
    );
});
test("browser back/forward restores selected group and pagination", async () => {
  const router = makeRouter("user");
  await router.push("/user/account/trade?view=favorites&page=3");
  await router.push("/user/account/content?view=comments");
  const back = new Promise((resolve) => {
    const remove = router.afterEach(() => {
      remove();
      resolve();
    });
  });
  router.back();
  await back;
  assert.equal(router.currentRoute.value.query.view, "favorites");
  assert.equal(router.currentRoute.value.query.page, "3");
});
test("page and currency formatting reject invalid values and keep wallet precision", () => {
  for (const value of ["bad", "0", "-1", "1.5", Infinity])
    assert.equal(navigation.queryPage(value), 1);
  assert.equal(navigation.queryPage("3"), 3);
  assert.equal(navigation.formatCampusCoin(1234.567), "1,234.57");
  assert.equal(navigation.formatCampusCoin(12), "12");
  assert.equal(navigation.formatCampusCoin(12, true), "12.00");
});
test("cold dynamic-route rematch preserves query and hash", async () => {
  let guard;
  const nextCalls = [];
  load("permission.ts", {
    "@/router": {
      default: {
        addRoute() {},
        beforeEach(fn) {
          guard = fn;
        },
        afterEach() {}
      }
    },
    nprogress: { default: { configure() {}, start() {}, done() {} } },
    "nprogress/nprogress.css": {},
    "./store/modules/user": { default: () => ({ token: "token" }) },
    "./setting": { default: { title: "校园集市" } },
    "./store": { default: {} },
    "@/utils/token": { GET_ROLE: () => "user" },
    "@/router/routes": routes,
    "@/utils/roleHome": roles
  });
  // The guard only reads document.title during navigation.
  global.document = { title: "" };
  try {
    await guard(
      {
        path: "/user/account",
        query: { tab: "chat", contactUserId: "2" },
        hash: "#section",
        matched: []
      },
      {},
      (v) => nextCalls.push(v)
    );
  } finally {
    delete global.document;
  }
  assert.equal(nextCalls[0].query.contactUserId, "2");
  assert.equal(nextCalls[0].hash, "#section");
});

test("mobile account navigation resolves only the five registered section destinations", () => {
  assert.equal(navigation.ACCOUNT_SECTIONS.length, 5);
  for (const item of navigation.ACCOUNT_SECTIONS)
    assert.equal(navigation.accountSectionPath(item.path), item.path);
  for (const value of [
    "/admin/userManagement",
    "/user/account/fake",
    undefined,
    ["/user/account/trade"]
  ])
    assert.equal(navigation.accountSectionPath(value), "/user/account");
});
