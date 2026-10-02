const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");

// 用现有 TypeScript 依赖载入源码，不增加测试框架或构建产物。
const source = fs.readFileSync(path.join(__dirname, "../src/utils/aiMessagePolling.ts"), "utf8");
const code = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText;
const flush = () => new Promise((resolve) => setImmediate(resolve));
const row = (id, status = "PENDING") => ({ id, role: "ASSISTANT", status, content: "", createTime: "" });
const page = (records, current = 1, total = records.length, pageSize = 20) => ({ records, current, total, pageSize });

function fixture(fetchPage, onError = () => true) {
  let sequence = 0;
  const timers = new Map();
  const events = [];
  const sandbox = {
    exports: {},
    setTimeout: (callback, delay) => {
      timers.set(++sequence, { callback, delay });
      return sequence;
    },
    clearTimeout: (id) => timers.delete(id)
  };
  vm.runInNewContext(code, sandbox);
  const start = (id = "target") => sandbox.exports.startAiMessagePolling({
    messageId: id,
    fetchPage,
    onSnapshot: (records, total) => events.push({ type: "snapshot", records, total }),
    onResult: (message) => events.push({ type: "result", message }),
    onMissing: () => events.push({ type: "missing" }),
    onError: (error) => {
      events.push({ type: "error", error });
      return onError(error);
    }
  });
  const tick = async () => {
    const [id, timer] = timers.entries().next().value;
    timers.delete(id);
    timer.callback();
    await flush();
    return timer.delay;
  };
  return { start, timers, events, tick };
}

for (const terminal of ["SUCCESS", "FAILED"]) {
  test(`PENDING continues and ${terminal} stops polling`, async () => {
    let status = "PENDING";
    const f = fixture(async () => page([row("target", status)]));
    f.start();
    assert.equal(await f.tick(), 2000);
    assert.equal(f.timers.size, 1);
    status = terminal;
    await f.tick();
    assert.equal(f.timers.size, 0);
    assert.equal(f.events.at(-1).message.status, terminal);
  });
}

test("finds the target on an older page instead of treating the newest reply as its result", async () => {
  const calls = [];
  const f = fixture(async (current) => {
    calls.push(current);
    return current === 1 ? page([row("newer", "SUCCESS")], 1, 2, 1)
      : page([row("target", "FAILED")], 2, 2, 1);
  });
  f.start();
  await f.tick();
  assert.deepEqual(calls, [1, 2]);
  assert.equal(f.events.at(-1).message.id, "target");
  assert.equal(f.events[0].records.length, 2);
});

test("never overlaps requests while an earlier query is unresolved", async () => {
  let resolve;
  let calls = 0;
  const f = fixture(() => {
    calls += 1;
    return new Promise((done) => { resolve = done; });
  });
  f.start();
  await f.tick();
  assert.equal(calls, 1);
  assert.equal(f.timers.size, 0);
  resolve(page([row("target")]));
  await flush();
  assert.equal(f.timers.size, 1);
});

test("network failure keeps waiting and recovers after ten seconds", async () => {
  let calls = 0;
  const f = fixture(async () => {
    if (++calls === 1) throw new Error("offline");
    return page([row("target", "SUCCESS")]);
  });
  f.start();
  await f.tick();
  assert.equal(f.events[0].type, "error");
  assert.equal(await f.tick(), 10000);
  assert.equal(f.events.at(-1).type, "result");
});

test("stopping on unmount ignores a late response", async () => {
  let resolve;
  const f = fixture(() => new Promise((done) => { resolve = done; }));
  const poller = f.start();
  await f.tick();
  poller.stop();
  resolve(page([row("target", "SUCCESS")]));
  await flush();
  assert.equal(f.events.length, 0);
  assert.equal(f.timers.size, 0);
});

test("a replacement poller ignores the previous generation's late response", async () => {
  let resolve;
  let calls = 0;
  const f = fixture(() => ++calls === 1 ? new Promise((done) => { resolve = done; })
    : Promise.resolve(page([row("replacement", "SUCCESS")])));
  const previous = f.start();
  await f.tick();
  previous.stop();
  f.start("replacement");
  resolve(page([row("target", "SUCCESS")]));
  await flush();
  await f.tick();
  assert.equal(f.events.filter((event) => event.type === "result").length, 1);
  assert.equal(f.events.at(-1).message.id, "replacement");
});

test("missing or unauthorized messages stop rather than resubmitting", async () => {
  const missing = fixture(async () => page([]));
  missing.start();
  await missing.tick();
  assert.equal(missing.events[0].type, "missing");
  assert.equal(missing.timers.size, 0);
  const unauthorized = fixture(async () => { throw new Error("401"); }, () => false);
  unauthorized.start();
  await unauthorized.tick();
  assert.equal(unauthorized.timers.size, 0);
});

test("independent conversations do not cancel each other", async () => {
  const first = fixture(async () => page([row("target")]));
  const second = fixture(async () => page([row("target", "SUCCESS")]));
  first.start();
  second.start();
  await first.tick();
  await second.tick();
  assert.equal(first.timers.size, 1);
  assert.equal(second.timers.size, 0);
});
