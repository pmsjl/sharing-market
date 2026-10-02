const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const vue = require("vue");

const read = (file) => fs.readFileSync(path.join(__dirname, "../src", file), "utf8");
const compile = (source) => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText;
const component = read("views/user/agentGuide/index.vue").match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const polling = compile(read("utils/aiMessagePolling.ts"));
const componentCode = compile(component + `
export const __test = { submitContent, loadMessages, selectConversation, startNewChat,
  activeConversationId, chatStates, activeChat, typingMessageId,
  selectedRecommendationId, selectionOpen, selectionMessage, selectionPrompt, openSelection };
`);
const flush = () => new Promise((resolve) => setImmediate(resolve));
const message = (id, status = "PENDING", sequenceNo = 2, role = "ASSISTANT") => ({
  id, status, role, sequenceNo, content: status === "SUCCESS" ? "回答" : "", createTime: "2026-10-02"
});
const conversation = (id = "c") => ({ id, status: "ACTIVE", title: "咨询", lastMessageTime: "2026-10-02" });
const response = (status = "PENDING", id = "c") => ({ code: 200, data: {
  requestId: "request", conversation: conversation(id),
  userMessage: message(`${id}-user`, "SUCCESS", 1, "USER"),
  assistantMessage: message(`${id}-assistant`, status)
} });
const snapshot = (records, total = records.length) => ({ code: 200,
  data: { records, current: 1, pageSize: 20, total } });

function fixture(overrides = {}) {
  const timers = new Map();
  let timerId = 0;
  const lifecycle = [];
  const notifications = [];
  let posts = 0;
  const api = {
    AI_RAG_MAX_SOURCE_COUNT: 8,
    AI_RAG_MAX_CITATION_COUNT: 2,
    createAiConversation: async () => { posts += 1; return response(); },
    sendAiConversationMessage: async (id) => { posts += 1; return response("PENDING", id); },
    listAiConversationMessages: async (id) => snapshot([message(`${id}-assistant`)]),
    listAiConversations: async () => ({ code: 200, data: { records: [conversation()], total: 1 } }),
    getMyAiQuota: async () => ({ code: 200, data: { remaining: 9, globalRemaining: 99 } }),
    ...overrides
  };
  const scope = vue.effectScope();
  const sandbox = {
    exports: {},
    console,
    setTimeout: (callback, delay) => { timers.set(++timerId, { callback, delay }); return timerId; },
    clearTimeout: (id) => timers.delete(id),
    localStorage: { getItem: () => null, setItem: () => {} },
    window: { innerWidth: 1200, matchMedia: () => ({ matches: true }),
      requestAnimationFrame: () => 1, cancelAnimationFrame: () => {},
      addEventListener: () => {}, removeEventListener: () => {} }
  };
  vm.createContext(sandbox);
  vm.runInContext(polling, sandbox);
  const pollModule = sandbox.exports;
  sandbox.exports = {};
  sandbox.require = (name) => {
    if (name === "vue") return { ...vue, onMounted: () => {}, onBeforeUnmount: (fn) => lifecycle.push(fn) };
    if (name === "vue-router") return {
      useRoute: () => ({ query: {} }), useRouter: () => ({ replace: async () => {} })
    };
    if (name === "element-plus") return { ElMessage: {
      error: (text) => notifications.push(text), warning: (text) => notifications.push(text), success: () => {}
    } };
    if (name === "@/store/modules/setting") return { default: () => ({ focusMode: false }) };
    if (name === "@/api/aiController") return api;
    if (name === "@/utils/aiMessagePolling") return pollModule;
    if (name === "@/components/AgentSelection/index.vue") return { default: {} };
    if (name === "md-editor-v3" || name.endsWith(".css")) return {};
    throw new Error(`Unexpected import ${name}`);
  };
  scope.run(() => vm.runInContext(componentCode, sandbox));
  const entry = sandbox.exports.__test;
  return {
    entry, api, timers, notifications, posts: () => posts,
    tick: async () => {
      const [id, timer] = timers.entries().next().value;
      timers.delete(id);
      timer.callback();
      await flush();
    },
    dispose: () => { lifecycle.forEach((fn) => fn()); scope.stop(); }
  };
}

test("selection stays attached to its answer and question when another result arrives", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  const rows = f.entry.activeChat.value.messages;
  const answer = { ...message("answer", "SUCCESS"), structuredContent: {
    recommendations: [{ commodity: { id: "book", commodityName: "活着", price: 12 } }]
  } };
  rows.push({ ...message("question", "SUCCESS", 1, "USER"), content: "想找一本小说" }, answer);
  f.entry.openSelection("answer");
  assert.equal(f.entry.selectionOpen.value, true);
  assert.equal(f.entry.selectionPrompt.value, "想找一本小说");
  rows.push({ ...message("next-question", "SUCCESS", 3, "USER"), content: "再看看台灯" },
    { ...answer, id: "next-answer", sequenceNo: 4 });
  await flush();
  assert.equal(f.entry.selectionMessage.value.id, "answer");
  assert.equal(f.entry.selectionPrompt.value, "想找一本小说");
  f.entry.openSelection("next-answer");
  assert.equal(f.entry.selectionPrompt.value, "再看看台灯");
  f.entry.activeConversationId.value = "other";
  assert.equal(f.entry.selectionOpen.value, false);
  assert.equal(f.entry.selectedRecommendationId.value, null);
});

test("selection cannot display failed or removed recommendations", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  const rows = f.entry.activeChat.value.messages;
  rows.push({ ...message("answer", "SUCCESS"), structuredContent: {
    recommendations: [{ commodity: { id: "book", commodityName: "活着", price: 12 } }]
  } });
  f.entry.openSelection("answer");
  assert.equal(f.entry.selectionOpen.value, true);
  rows[0].status = "FAILED";
  await flush();
  assert.equal(f.entry.selectionOpen.value, false);
  assert.equal(f.entry.selectionMessage.value, null);
  f.entry.openSelection("missing");
  assert.equal(f.entry.selectionOpen.value, false);
});

test("submission stays locked after returning PENDING and unlocks only on the server result", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  await f.entry.submitContent("买电脑");
  assert.equal(f.entry.activeConversationId.value, "c");
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.entry.activeChat.value.pendingMessageId, "c-assistant");
  assert.equal(f.timers.size, 1);
  await f.entry.submitContent("不能再发");
  assert.equal(f.posts(), 1);
  f.api.listAiConversationMessages = async () => snapshot([message("c-assistant", "SUCCESS")]);
  await f.tick();
  assert.equal(f.entry.activeChat.value.sending, false);
  assert.equal(f.entry.activeChat.value.messages.at(-1).status, "SUCCESS");
});

test("reloading a saved PENDING restores its waiting state", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  assert.equal(await f.entry.loadMessages("c"), true);
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.timers.size, 1);
});

test("polling preserves already loaded history", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  const state = f.entry.activeChat.value;
  state.messages.push(message("older", "SUCCESS", -1));
  await f.entry.submitContent("继续");
  await f.tick();
  assert.ok(state.messages.some((row) => row.id === "older"));
  assert.equal(state.messages.filter((row) => row.id === "c-assistant").length, 1);
});

test("loading older pages after polling does not duplicate overlapping messages", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  const state = f.entry.activeChat.value;
  state.messages.push(message("overlap", "SUCCESS", 0));
  await f.entry.submitContent("继续");
  f.api.listAiConversationMessages = async () => snapshot([message("c-assistant", "SUCCESS")]);
  await f.tick();
  f.api.listAiConversationMessages = async () => snapshot([
    message("older", "SUCCESS", -1), message("overlap", "SUCCESS", 0)
  ]);
  state.page = 2;
  await f.entry.loadMessages("c", true);
  assert.equal(state.messages.filter((row) => row.id === "overlap").length, 1);
  assert.ok(state.messages.some((row) => row.id === "older"));
});

test("switching conversations does not redirect a background answer to the active conversation", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  await f.entry.submitContent("第一轮");
  await f.entry.selectConversation(conversation("other"));
  assert.equal(f.timers.size, 2);
  f.api.listAiConversationMessages = async (id) => snapshot([message(`${id}-assistant`, id === "c" ? "SUCCESS" : "PENDING")]);
  await f.tick();
  assert.equal(f.entry.chatStates.c.sending, false);
  assert.equal(f.entry.activeChat.value.pendingMessageId, "other-assistant");
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.entry.typingMessageId.value, null);
});

test("rejected task response is immediately FAILED and starts no polling", async (t) => {
  const f = fixture({ createAiConversation: async () => response("FAILED") });
  t.after(f.dispose);
  await f.entry.submitContent("咨询");
  assert.equal(f.entry.activeChat.value.sending, false);
  assert.equal(f.entry.activeChat.value.messages.at(-1).status, "FAILED");
  assert.equal(f.timers.size, 0);
});

test("lost POST response reconciles saved messages without another submission or a fake FAILED", async (t) => {
  let posts = 0;
  const f = fixture({ sendAiConversationMessage: async () => { posts += 1; throw new Error("offline"); } });
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  await f.entry.submitContent("已提交但断网");
  assert.equal(posts, 1);
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.entry.activeChat.value.submissionUnknown, false);
  assert.equal(f.entry.activeChat.value.messages.some((row) => row.status === "FAILED"), false);
});

test("an unknown first submission blocks draft resubmission until the user checks saved conversations", async (t) => {
  let posts = 0;
  const f = fixture({ createAiConversation: async () => { posts += 1; throw new Error("offline"); } });
  t.after(f.dispose);
  await f.entry.submitContent("首轮");
  assert.equal(f.entry.activeChat.value.submissionUnknown, true);
  await f.entry.submitContent("首轮");
  assert.equal(posts, 1);
});

test("server conflict reloads the existing PENDING instead of permitting another immediate submission", async (t) => {
  const f = fixture({ sendAiConversationMessage: async () => ({ code: 40900, message: "正在回复" }) });
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  await f.entry.submitContent("另一个页面发来了请求");
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.timers.size, 1);
});

test("expired login stops polling and requires a reload", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  await f.entry.submitContent("咨询");
  f.api.listAiConversationMessages = async () => { throw { response: { status: 401 } }; };
  await f.tick();
  assert.equal(f.entry.activeChat.value.loadFailed, true);
  assert.equal(f.timers.size, 0);
  assert.ok(f.notifications.length);
});
