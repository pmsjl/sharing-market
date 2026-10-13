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
export const __test = { submitContent, sendMessage, retryMessage, composer, loadMessages, selectConversation, startNewChat,
  activeConversationId, chatStates, activeChat, typingMessageId,
  selectedRecommendationId, selectionOpen, selectionMessage, selectionPrompt, openSelection, restoreRequestedConversation, isDisclosureOpen, toggleDisclosure };
`);
const flush = () => new Promise((resolve) => setImmediate(resolve));
const message = (id, status = "PENDING", sequenceNo = 2, role = "ASSISTANT") => ({
  id, status, role, sequenceNo, content: status === "SUCCESS" ? "回答" : "", createTime: "2026-10-02"
});
const conversation = (id = "c") => ({ id, status: "ACTIVE", title: "咨询", lastMessageTime: "2026-10-02" });
const response = (status = "PENDING", id = "c", userSequenceNo = 1, content = "回答") => ({ code: 200, data: {
  requestId: "request", conversation: conversation(id),
  userMessage: { ...message(`${id}-user`, "SUCCESS", userSequenceNo, "USER"), content },
  assistantMessage: message(`${id}-assistant`, status, userSequenceNo + 1)
} });
const snapshot = (records, total = records.length) => ({ code: 200,
  data: { records, current: 1, pageSize: 20, total } });

function fixture(overrides = {}) {
  const route = vue.reactive({ query: {} });
  const timers = new Map();
  let timerId = 0;
  const lifecycle = [];
  const notifications = [];
  const requests = [];
  let posts = 0;
  const api = {
    AI_RAG_MAX_SOURCE_COUNT: 8,
    AI_RAG_MAX_CITATION_COUNT: 2,
    createAiConversation: async (body) => { posts += 1; return response("PENDING", "c", 1, body.content); },
    sendAiConversationMessage: async (id, body) => {
      posts += 1; return response("PENDING", id, body.expectedUserSequenceNo, body.content);
    },
    listAiConversationMessages: async (id) => snapshot([message(`${id}-assistant`)]),
    listAiConversations: async () => ({ code: 200, data: { records: [conversation()], total: 1 } }),
    getMyAiQuota: async () => ({ code: 200, data: { remaining: 9, globalRemaining: 99 } }),
    ...overrides
  };
  const create = api.createAiConversation;
  const send = api.sendAiConversationMessage;
  api.createAiConversation = (body) => {
    requests.push({ conversationId: null, body });
    return create(body);
  };
  api.sendAiConversationMessage = (id, body) => {
    requests.push({ conversationId: id, body });
    return send(id, body);
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
      useRoute: () => route, useRouter: () => ({ replace: async () => {} })
    };
    if (name === "element-plus") return { ElMessage: {
      error: (text) => notifications.push(text), warning: (text) => notifications.push(text), success: () => {}
    } };
    if (name === "@/store/modules/setting") return { default: () => ({ focusMode: false }) };
    if (name === "@/api/aiController") return api;
    if (name === "@/utils/agentMarkdown") return { splitMarkdownTypingUnits: (text, split) => split(text) };
    if (name === "@/utils/aiMessagePolling") return pollModule;
    if (name === "@/components/AgentSelection/index.vue") return { default: {} };
    if (name === "md-editor-v3" || name.endsWith(".css")) return {};
    throw new Error(`Unexpected import ${name}`);
  };
  scope.run(() => vm.runInContext(componentCode, sandbox));
  const entry = sandbox.exports.__test;
  return {
    entry, api, timers, notifications, requests, route, posts: () => posts,
    tick: async () => {
      const [id, timer] = timers.entries().next().value;
      timers.delete(id);
      timer.callback();
      await flush();
    },
    dispose: () => { lifecycle.forEach((fn) => fn()); scope.stop(); }
  };
}

function readyConversation(f, id = "c") {
  f.entry.activeConversationId.value = id;
  const state = f.entry.activeChat.value;
  state.messages.push(message(`${id}-previous`, "SUCCESS"));
  state.nextUserSequenceNo = 3;
  return state;
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
  const state = readyConversation(f);
  state.messages.push(message("older", "SUCCESS", -1));
  await f.entry.submitContent("继续");
  await f.tick();
  assert.ok(state.messages.some((row) => row.id === "older"));
  assert.equal(state.messages.filter((row) => row.id === "c-assistant").length, 1);
});

test("loading older pages after polling does not duplicate overlapping messages", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  const state = readyConversation(f);
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
  readyConversation(f);
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
  readyConversation(f);
  await f.entry.submitContent("另一个页面发来了请求");
  assert.equal(f.entry.activeChat.value.sending, true);
  assert.equal(f.timers.size, 1);
});

test("first submission needs no sequence and continuation reserves the next sequence before awaiting", async (t) => {
  let finish;
  const f = fixture({
    createAiConversation: async () => response("SUCCESS"),
    sendAiConversationMessage: (id, body) => new Promise((resolve) => {
      finish = () => resolve(response("SUCCESS", id, body.expectedUserSequenceNo));
    })
  });
  t.after(f.dispose);
  await f.entry.submitContent("首轮");
  assert.equal(Object.hasOwn(f.requests[0].body, "expectedUserSequenceNo"), false);
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 3);

  const first = f.entry.submitContent("A");
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 5);
  assert.equal(f.entry.activeChat.value.sending, true);
  await f.entry.submitContent("B");
  await flush();
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].body.expectedUserSequenceNo, 3);
  assert.ok(f.notifications.includes("上一条消息仍在处理中"));
  finish();
  await first;
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 5);
});

test("history restores the latest server sequence and older pages or local placeholders cannot change it", async (t) => {
  const f = fixture({ listAiConversationMessages: async () => snapshot([
    message("reply", "SUCCESS", 8), message("question", "SUCCESS", 7, "USER"),
    message("local-placeholder", "SUCCESS", 999)
  ]) });
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  await f.entry.loadMessages("c");
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 9);

  f.api.listAiConversationMessages = async () => snapshot([message("older", "SUCCESS", 2)]);
  f.entry.activeChat.value.page = 2;
  await f.entry.loadMessages("c", true);
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 9);
  await f.entry.submitContent("继续");
  assert.equal(f.requests[0].body.expectedUserSequenceNo, 9);
});

test("sequence conflict reloads page one, preserves input, and waits for a manual resend", async (t) => {
  const pages = [];
  let attempts = 0;
  const f = fixture({
    sendAiConversationMessage: async (id, body) => ++attempts === 1
      ? { code: 40900, message: "消息顺序已变化，请刷新后重试" }
      : response("SUCCESS", id, body.expectedUserSequenceNo),
    listAiConversationMessages: async (id, page) => {
      pages.push(page);
      return snapshot([message("new-reply", "SUCCESS", 10)]);
    }
  });
  t.after(f.dispose);
  const state = readyConversation(f);
  state.page = 3;
  await f.entry.submitContent("保留这个问题");
  assert.deepEqual(pages, [1]);
  assert.equal(f.requests.length, 1);
  assert.equal(state.nextUserSequenceNo, 11);
  assert.equal(state.composer, "保留这个问题");
  assert.equal(state.messages.some(row => row.id.startsWith("local-")), false);
  assert.equal(state.sending, false);

  await f.entry.sendMessage();
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].body.expectedUserSequenceNo, 11);
  assert.equal(state.messages.filter(row => row.role === "USER").length, 1);
  assert.equal(state.nextUserSequenceNo, 13);
});

test("retry uses the saved failed assistant sequence before removing it", async (t) => {
  const f = fixture({
    createAiConversation: async (body) => response("FAILED", "c", 1, body.content),
    sendAiConversationMessage: async (id, body) => response("SUCCESS", id, body.expectedUserSequenceNo)
  });
  t.after(f.dispose);
  await f.entry.submitContent("重试问题");
  await f.entry.retryMessage("c-assistant");
  assert.equal(f.requests[1].body.expectedUserSequenceNo, 3);
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 5);
  assert.equal(f.entry.activeChat.value.messages.filter(row => row.role === "USER").length, 1);
  assert.equal(f.entry.activeChat.value.messages.filter(row => row.role === "ASSISTANT").length, 1);
});

test("quota rejection restores a failed retry and releases its reserved sequence", async (t) => {
  const f = fixture({
    createAiConversation: async (body) => response("FAILED", "c", 1, body.content),
    sendAiConversationMessage: async () => ({ code: 42901, message: "quota" })
  });
  t.after(f.dispose);
  await f.entry.submitContent("重试问题");
  await f.entry.retryMessage("c-assistant");
  const state = f.entry.activeChat.value;
  assert.equal(f.requests[1].body.expectedUserSequenceNo, 3);
  assert.equal(state.nextUserSequenceNo, 3);
  assert.equal(state.messages.filter(row => row.id === "c-assistant").length, 1);
  assert.equal(state.messages.find(row => row.id === "c-assistant").status, "FAILED");
  assert.equal(state.messages.some(row => row.id.startsWith("local-")), false);
  assert.equal(state.composer, "重试问题");
});

test("unresolved continuation blocks another POST until history can be confirmed", async (t) => {
  const pages = [];
  const f = fixture({
    sendAiConversationMessage: async () => { throw new Error("offline"); },
    listAiConversationMessages: async (id, page) => {
      pages.push(page);
      throw new Error("offline");
    }
  });
  t.after(f.dispose);
  const state = readyConversation(f);
  state.page = 2;
  await f.entry.submitContent("请求结果未知");
  await f.entry.submitContent("不能再次发出");
  assert.equal(f.requests.length, 1);
  assert.deepEqual(pages, [1]);
  assert.equal(state.submissionUnknown, true);
  assert.equal(state.composer, "请求结果未知");
});

test("pending timeout observed by polling resynchronizes the next sequence", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  await f.entry.submitContent("首轮");
  f.api.listAiConversationMessages = async () => snapshot([
    { ...message("c-assistant", "FAILED"), agentErrorKey: "AI_AGENT_PENDING_TIMEOUT" }
  ]);
  await f.tick();
  assert.equal(f.entry.activeChat.value.sending, false);
  assert.equal(f.entry.activeChat.value.nextUserSequenceNo, 3);
  await f.entry.submitContent("超时后继续");
  assert.equal(f.requests[1].body.expectedUserSequenceNo, 3);
});

test("a conversation without a confirmed server sequence cannot submit a continuation", async (t) => {
  const f = fixture();
  t.after(f.dispose);
  f.entry.activeConversationId.value = "c";
  await f.entry.submitContent("先确认历史");
  assert.equal(f.requests.length, 0);
  assert.equal(f.entry.activeChat.value.loadFailed, true);
  assert.equal(f.entry.activeChat.value.composer, "先确认历史");
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

test('same-page conversation query restores the requested chat without replacing another chat state', async () => {
  const h = fixture({listAiConversationMessages: async id => snapshot([message(id+'-reply','SUCCESS')])});
  try {
    h.route.query.conversationId='c1'; await h.entry.restoreRequestedConversation();
    assert.equal(h.entry.activeConversationId.value,'c1');
    h.route.query.conversationId='c2'; await h.entry.restoreRequestedConversation();
    assert.equal(h.entry.activeConversationId.value,'c2');
    assert.equal(h.entry.activeChat.value.messages[0].id,'c2-reply');
    h.route.query.conversationId='c1'; await h.entry.restoreRequestedConversation();
    assert.equal(h.entry.activeChat.value.messages[0].id,'c1-reply');
  } finally { h.dispose(); }
});


test("reference disclosures are isolated by answer, type and conversation, and reset on remount", (t) => {
  const f = fixture(); t.after(f.dispose);
  f.entry.activeConversationId.value = "c1";
  assert.equal(f.entry.isDisclosureOpen("answer", "sources"), false);
  f.entry.toggleDisclosure("answer", "sources");
  assert.equal(f.entry.isDisclosureOpen("answer", "sources"), true);
  assert.equal(f.entry.isDisclosureOpen("answer", "posts"), false);
  assert.equal(f.entry.isDisclosureOpen("answer2", "sources"), false);
  f.entry.activeConversationId.value = "c2";
  assert.equal(f.entry.isDisclosureOpen("answer", "sources"), false);
  f.entry.activeConversationId.value = "c1";
  assert.equal(f.entry.isDisclosureOpen("answer", "sources"), true);
  f.entry.toggleDisclosure("answer", "sources");
  assert.equal(f.entry.isDisclosureOpen("answer", "sources"), false);
  const fresh = fixture(); t.after(fresh.dispose);
  fresh.entry.activeConversationId.value = "c1";
  assert.equal(fresh.entry.isDisclosureOpen("answer", "sources"), false);
});
