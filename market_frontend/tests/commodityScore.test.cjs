const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const vue = require("vue");

function ratingHarness(api = {}, props = {}) {
  const messages = [];
  const events = [];
  const mounted = [];
  const source = fs
    .readFileSync(
      path.join(__dirname, "../src/components/CommodityScore/index.vue"),
      "utf8"
    )
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const imports = {
    vue: { ...vue, onMounted: (callback) => mounted.push(callback) },
    "@element-plus/icons-vue": {},
    "element-plus": {
      ElMessage: Object.fromEntries(
        ["error", "info", "warning", "success"].map((kind) => [
          kind,
          (text) => messages.push({ kind, text })
        ])
      )
    },
    "@/api/commodityScoreController": {
      getAverageScoreUsingGet: async () => ({ code: 200, data: 4 }),
      listMyCommodityScoreVoByPageUsingPost: async () => ({
        code: 200,
        data: { records: [] }
      }),
      ...api
    },
    "vue-router": { useRoute: () => ({ params: { id: "7" } }) },
    "@/utils/eventBus": { default: { emit: (event) => events.push(event) } }
  };
  const box = {
    exports: {},
    defineProps: () => props,
    require: (name) => {
      if (name in imports) return imports[name];
      throw new Error("Unexpected import " + name);
    }
  };
  vm.runInNewContext(
    ts.transpileModule(
      source +
        `
    export const state = { handleRating, checkUserRating, hasRated, currentRating,
      submitting, checkingRating, ratingDisabled, ratingHint };
  `,
      {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2020
        }
      }
    ).outputText,
    box
  );
  return { ...box.exports.state, messages, events, mounted };
}

test("rating displays the business rejection reason and permits retry", async () => {
  const h = ratingHarness({
    addCommodityScoreUsingPost: async () => ({
      code: 40000,
      message: "不能评价自己的商品"
    })
  });
  await h.checkUserRating();
  await h.handleRating(5);
  assert.equal(h.messages.at(-1).text, "不能评价自己的商品");
  assert.equal(h.hasRated.value, false);
  assert.equal(h.currentRating.value, 0);
  assert.equal(h.submitting.value, false);
});

test("rating ignores clicks during initial status lookup and submits once while pending", async () => {
  let finish,
    calls = 0;
  const h = ratingHarness({
    addCommodityScoreUsingPost: () => {
      calls++;
      return new Promise((resolve) => {
        finish = resolve;
      });
    }
  });
  await h.handleRating(5);
  assert.equal(calls, 0);
  await h.checkUserRating();
  const pending = h.handleRating(4);
  await h.handleRating(5);
  assert.equal(calls, 1);
  assert.equal(h.ratingDisabled.value, true);
  finish({ code: 200, data: "99" });
  await pending;
  assert.equal(h.currentRating.value, 4);
  assert.equal(h.hasRated.value, true);
  assert.deepEqual(h.events, ["refresh-commodity-score-list"]);
  await h.handleRating(5);
  assert.equal(calls, 1);
});

test("rating restores an existing score after another page has already submitted", async () => {
  let lookups = 0;
  const h = ratingHarness({
    listMyCommodityScoreVoByPageUsingPost: async () => ({
      code: 200,
      data: { records: lookups++ ? [{ score: 3 }] : [] }
    }),
    addCommodityScoreUsingPost: async () => ({
      code: 40900,
      message: "您已评分，不可重复评分"
    })
  });
  await h.checkUserRating();
  await h.handleRating(5);
  assert.equal(h.hasRated.value, true);
  assert.equal(h.currentRating.value, 3);
  assert.equal(h.messages[0].text, "您已评分，不可重复评分");
});

test("rating does not overwrite a transport error already shown by the interceptor", async () => {
  const h = ratingHarness({
    addCommodityScoreUsingPost: async () => {
      throw {
        message: "登录状态已失效，请重新登录",
        requestMessageShown: true
      };
    }
  });
  await h.checkUserRating();
  await h.handleRating(5);
  assert.equal(h.messages.length, 0);
  assert.equal(h.submitting.value, false);
  assert.equal(h.hasRated.value, false);
});

test("own and unlisted products explain why rating is unavailable before sending", async () => {
  for (const reason of ["不能评价自己的商品", "商品未上架，不能评价"]) {
    let calls = 0;
    const h = ratingHarness(
      {
        addCommodityScoreUsingPost: async () => {
          calls++;
        }
      },
      { disabledReason: reason }
    );
    await h.checkUserRating();
    assert.equal(h.ratingHint.value, reason);
    assert.equal(h.ratingDisabled.value, true);
    await h.handleRating(5);
    assert.equal(calls, 0);
    assert.equal(h.messages.at(-1).text, reason);
  }
});
