/* eslint-disable @typescript-eslint/no-var-requires */
// Run with: node tests/agent-concurrency.js
// Exercise the page's actual script with Vue reactivity and deferred API responses.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const vue = require("vue");

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

function setup() {
  const requests = [];
  const loads = [];
  const api = (id, body) => {
    const pending = deferred();
    requests.push({ id, body, ...pending });
    return pending.promise;
  };
  const source = fs
    .readFileSync(
      path.join(__dirname, "../src/views/user/agentGuide/index.vue"),
      "utf8"
    )
    .split('<script setup lang="ts">')[1]
    .split("</script>")[0];
  const ast = ts.createSourceFile(
    "page.ts",
    source,
    ts.ScriptTarget.Latest,
    true
  );
  const withoutImports = ast.statements
    .filter((node) => !ts.isImportDeclaration(node))
    .map((node) => node.getText(ast))
    .join("\n");
  const code = ts.transpileModule(
    withoutImports +
      `
    globalThis.page = { activeConversationId, activeChat, messages, sending, composer,
      chatStates, pendingDrafts, draftKey, shoppingContext, selectConversation,
      selectDraft, startNewChat, sendMessage, retryMessage, loadMessages };
  `,
    {
      compilerOptions: {
        target: ts.ScriptTarget.ES2020,
        module: ts.ModuleKind.CommonJS
      }
    }
  ).outputText;
  const context = {
    ...vue,
    watch: () => undefined,
    onMounted: () => undefined,
    onBeforeUnmount: () => undefined,
    useRouter: () => ({ replace: () => undefined, push: () => undefined }),
    useRoute: () => ({ query: {} }),
    useLayOutSettingStore: () => ({}),
    window: {
      innerWidth: 1200,
      matchMedia: () => ({ matches: true }),
      requestAnimationFrame: () => 1
    },
    localStorage: { getItem: () => null },
    ElMessage: {
      warning: () => undefined,
      error: () => undefined,
      success: () => undefined
    },
    getMyAiQuota: async () => ({ code: 200 }),
    sendAiConversationMessage: api,
    createAiConversation: (body) => api(null, body),
    listAiConversationMessages: (id) => {
      const pending = deferred();
      loads.push({ id, ...pending });
      return pending.promise;
    }
  };
  vm.runInNewContext(code, context);
  return { page: context.page, requests, loads };
}

const flush = async () => {
  for (let i = 0; i < 8; i++) await Promise.resolve();
};
const response = (id, text) => ({
  code: 200,
  data: {
    conversation: { id, title: id },
    userMessage: {
      id: `${id}-user`,
      role: "USER",
      content: text,
      status: "SUCCESS"
    },
    assistantMessage: {
      id: `${id}-answer`,
      role: "ASSISTANT",
      content: `reply ${text}`,
      status: "SUCCESS"
    }
  }
});
const empty = { code: 200, data: { records: [], total: 0 } };
async function select(h, id) {
  const pending = h.page.selectConversation({ id });
  h.loads.at(-1).resolve(empty);
  await pending;
}

async function run() {
  {
    const h = setup(),
      p = h.page;
    await select(h, "A");
    p.composer.value = "a";
    const a = p.sendMessage();
    await select(h, "B");
    p.composer.value = "b";
    const b = p.sendMessage();
    await flush();
    assert.equal(h.requests.length, 2);
    p.composer.value = "duplicate";
    await p.sendMessage();
    assert.equal(h.requests.length, 2, "same conversation cannot send twice");
    h.requests[0].resolve(response("A", "a"));
    await a;
    assert.equal(p.activeConversationId.value, "B");
    assert.equal(
      p.sending.value,
      true,
      "A finishing must not clear B's busy state"
    );
    assert.equal(p.messages.value[0].content, "b");
    h.requests[1].resolve(response("B", "b"));
    await b;
    assert.equal(p.chatStates.A.messages[1].content, "reply a");
    assert.equal(p.messages.value[1].content, "reply b");
  }
  {
    const h = setup(),
      p = h.page;
    p.shoppingContext.usageScene = "study";
    p.composer.value = "first draft";
    const a = p.sendMessage();
    const firstKey = p.draftKey.value;
    p.startNewChat();
    p.shoppingContext.usageScene = "travel";
    p.composer.value = "second draft";
    const b = p.sendMessage();
    await flush();
    assert.equal(h.requests[0].body.shoppingContext.usageScene, "study");
    assert.equal(h.requests[1].body.shoppingContext.usageScene, "travel");
    assert.equal(p.pendingDrafts.value.length, 2);
    p.selectDraft(firstKey);
    h.requests[1].resolve(response("B", "second draft"));
    await b;
    assert.equal(p.activeConversationId.value, null);
    assert.equal(p.draftKey.value, firstKey);
    h.requests[0].resolve(response("A", "first draft"));
    await a;
    assert.equal(p.activeConversationId.value, "A");
    assert.equal(p.pendingDrafts.value.length, 0);
    assert.equal(p.shoppingContext.usageScene, "study");
  }
  {
    const h = setup(),
      p = h.page;
    await select(h, "A");
    p.composer.value = "retry me";
    const a = p.sendMessage();
    await select(h, "B");
    h.requests[0].reject(new Error("network failed"));
    await a;
    assert.equal(p.messages.value.length, 0);
    await p.selectConversation({ id: "A" });
    const failed = p.messages.value.at(-1);
    assert.equal(failed.status, "FAILED");
    const retry = p.retryMessage(failed.id);
    await flush();
    h.requests[1].resolve(response("A", "retry me"));
    await retry;
    assert.equal(p.messages.value.filter((m) => m.role === "USER").length, 1);
  }
  {
    const h = setup(),
      p = h.page;
    const a = p.selectConversation({ id: "A" });
    const b = p.selectConversation({ id: "B" });
    h.loads[1].resolve(empty);
    await b;
    h.loads[0].resolve({
      code: 200,
      data: { records: [{ id: "old-a", content: "A history" }], total: 1 }
    });
    await a;
    assert.equal(p.activeConversationId.value, "B");
    assert.equal(
      p.messages.value.length,
      0,
      "late history must not replace the active chat"
    );
    const old = p.loadMessages("B"),
      recent = p.loadMessages("B");
    h.loads[3].resolve(empty);
    await recent;
    h.loads[2].resolve({
      code: 200,
      data: { records: [{ id: "stale" }], total: 1 }
    });
    await old;
    assert.equal(
      p.messages.value.length,
      0,
      "older fetch must not overwrite newer fetch"
    );
  }
  {
    const h = setup(),
      p = h.page;
    await select(h, "A");
    p.composer.value = "quota rejected";
    const a = p.sendMessage();
    await select(h, "B");
    p.composer.value = "B draft";
    h.requests[0].resolve({ code: 42901, message: "quota" });
    await a;
    assert.equal(p.composer.value, "B draft");
    assert.equal(p.chatStates.A.composer, "quota rejected");
    assert.equal(p.chatStates.A.messages.length, 0);
  }
  console.log("5 agent concurrency scenarios passed");
}
run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
