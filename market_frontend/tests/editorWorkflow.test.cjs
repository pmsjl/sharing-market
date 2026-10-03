const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const vue = require("vue");
const read = (file) =>
  fs.readFileSync(path.join(__dirname, "../src", file), "utf8");
const compile = (source) =>
  ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText;
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
};
const flush = () => new Promise((resolve) => setImmediate(resolve));
function execute(source, imports = {}) {
  const box = {
    exports: {},
    Error,
    window: { addEventListener() {}, removeEventListener() {} },
    require: (name) => {
      if (imports[name]) return imports[name];
      throw new Error("Unexpected import " + name);
    }
  };
  vm.runInNewContext(compile(source), box);
  return box.exports;
}
const navigation = execute(read("utils/marketNavigation.ts"));
const remote = execute(read("composables/useRemote.ts"), { vue });
function editor(kind, api = {}) {
  const scope = vue.effectScope();
  const navigations = [];
  const dirtyGuards = [];
  const route = vue.reactive({
    params: {},
    query: {
      returnTo:
        kind === "post"
          ? "/user/account/content?view=posts&page=3"
          : "/user/commodity?q=book&sort=priceAsc&page=2"
    }
  });
  const imports = {
    vue: { ...vue, onMounted() {} },
    "vue-router": {
      useRoute: () => route,
      useRouter: () => ({
        replace: async (p) => navigations.push(p),
        push: async (p) => navigations.push(p)
      })
    },
    "element-plus": { ElMessage: { success() {} } },
    "@element-plus/icons-vue": {},
    "md-editor-v3": {},
    "md-editor-v3/lib/style.css": {},
    "@/components/AsyncState/index.vue": {},
    "@/utils/marketNavigation": navigation,
    "@/utils/token": { GET_ID: () => "1" },
    "@/composables/useRemote": remote,
    "@/composables/useUnsavedChanges": {
      useUnsavedChanges: (dirty, busy) => dirtyGuards.push({ dirty, busy })
    },
    "@/api/postController": api,
    "@/api/commodityController": api,
    "@/api/commodityTypeController": api,
    "@/api/fileController": api
  };
  const file =
    kind === "post"
      ? "views/user/post/Editor.vue"
      : "views/user/commodity/Publish.vue";
  const code = read(file).match(
    /<script setup lang="ts">([\s\S]*?)<\/script>/
  )[1];
  const exports = scope.run(() =>
    execute(
      code +
        "\nexport const state={form,original,formRef,submit,saving,submitError" +
        (kind === "post"
          ? ",load,loadError,loading,validateTags"
          : ",upload,uploading,uploadError,selectCover") +
        "};",
      imports
    )
  );
  exports.state.formRef.value = { validate: async () => true };
  return {
    ...exports.state,
    navigations,
    route,
    dirtyGuards,
    dispose: () => scope.stop()
  };
}
for (const kind of ["commodity", "post"]) {
  test(
    kind +
      ": repeated submit while validation/request is pending sends only once and restores source list",
    async () => {
      const validation = deferred(),
        request = deferred();
      let calls = 0;
      const h = editor(kind, {
        addCommodityUsingPost: () => {
          calls++;
          return request.promise;
        },
        addPostUsingPost: () => {
          calls++;
          return request.promise;
        }
      });
      try {
        h.formRef.value.validate = () => validation.promise;
        h.form.title = "分享";
        h.form.commodityName = "旧书";
        const first = h.submit();
        await h.submit();
        assert.equal(calls, 0);
        validation.resolve(true);
        await flush();
        await h.submit();
        assert.equal(calls, 1);
        request.resolve({ code: 200, data: 1 });
        await first;
        assert.equal(h.saving.value, false);
        assert.equal(h.navigations[0], h.route.query.returnTo);
        assert.equal(h.original.value, JSON.stringify(h.form));
      } finally {
        h.dispose();
      }
    }
  );
  test(
    kind + ": failure preserves input and releases submission lock for retry",
    async () => {
      let calls = 0;
      const send = async () => {
        calls++;
        return calls === 1
          ? { code: 500, message: "服务暂不可用" }
          : { code: 200, data: true };
      };
      const h = editor(kind, {
        addCommodityUsingPost: send,
        addPostUsingPost: send
      });
      try {
        h.form.title = "保留我的内容";
        h.form.commodityName = "高数教材";
        const snapshot = JSON.stringify(h.form);
        await h.submit();
        assert.equal(JSON.stringify(h.form), snapshot);
        assert.equal(h.saving.value, false);
        assert.equal(h.submitError.value, "服务暂不可用");
        assert.equal(h.navigations.length, 0);
        await h.submit();
        assert.equal(calls, 2);
      } finally {
        h.dispose();
      }
    }
  );
  test(kind + ": invalid form never reaches API", async () => {
    let calls = 0;
    const h = editor(kind, {
      addCommodityUsingPost: async () => {
        calls++;
      },
      addPostUsingPost: async () => {
        calls++;
      }
    });
    try {
      h.formRef.value.validate = async () => false;
      await h.submit();
      assert.equal(calls, 0);
      assert.equal(h.saving.value, false);
    } finally {
      h.dispose();
    }
  });
}
test("cover upload failure preserves previous cover and other fields; retry replaces only cover", async () => {
  let calls = 0;
  const pending = deferred();
  const h = editor("commodity", {
    uploadFileUsingPost: () => {
      calls++;
      return pending.promise;
    }
  });
  try {
    h.form.commodityAvatar = "old-cover";
    h.form.commodityName = "旧书";
    const upload = h.upload({ file: { name: "cover.png" } });
    await h.upload({ file: {} });
    await h.submit();
    assert.equal(calls, 1);
    pending.reject(new Error("offline"));
    await upload;
    assert.equal(h.form.commodityAvatar, "old-cover");
    assert.equal(h.form.commodityName, "旧书");
    assert.equal(h.uploading.value, false);
    assert.match(h.uploadError.value, /上传失败/);
  } finally {
    h.dispose();
  }
});
test("post edit checks ownership and does not submit after failed load", async () => {
  let calls = 0;
  const h = editor("post", {
    getPostVoByIdUsingGet: async () => ({
      code: 200,
      data: { userId: "2", title: "他人攻略" }
    }),
    editPostUsingPost: async () => {
      calls++;
    }
  });
  try {
    h.route.params.id = "7";
    await flush();
    await h.load();
    assert.match(h.loadError.value, /只能编辑/);
    await h.submit();
    assert.equal(calls, 0);
  } finally {
    h.dispose();
  }
});
test("post topic limits are field-level validation", () => {
  const h = editor("post");
  try {
    let error;
    h.validateTags(null, ["too-long-topic"], (e) => (error = e));
    assert.match(error.message, /10/);
    h.validateTags(null, ["旧书"], (e) => (error = e));
    assert.equal(error, undefined);
  } finally {
    h.dispose();
  }
});
test("remote panel ignores stale data and supports local retry without resetting other panels", async () => {
  const scope = vue.effectScope();
  const a = deferred(),
    b = deferred();
  let n = 0;
  const h = scope.run(() =>
    remote.useRemote([], () => (++n === 1 ? a.promise : b.promise))
  );
  try {
    const first = h.load(),
      second = h.load();
    b.resolve(["new"]);
    await second;
    a.resolve(["old"]);
    await first;
    assert.deepEqual(h.data.value, ["new"]);
  } finally {
    scope.stop();
  }
});
test("unsaved guard handles cancel, discard, route update, busy and browser refresh", async () => {
  let leave,
    update,
    mounted,
    cleanup,
    confirmCount = 0,
    accept = false;
  const listeners = {};
  const dirty = vue.ref(true),
    busy = vue.ref(false);
  const sandbox = {
    exports: {},
    window: {
      addEventListener: (name, fn) => (listeners[name] = fn),
      removeEventListener: (name) => delete listeners[name]
    },
    require: (name) =>
      ({
        vue: {
          onMounted: (fn) => (mounted = fn),
          onBeforeUnmount: (fn) => (cleanup = fn)
        },
        "vue-router": {
          onBeforeRouteLeave: (fn) => (leave = fn),
          onBeforeRouteUpdate: (fn) => (update = fn)
        },
        "element-plus": {
          ElMessageBox: {
            confirm: async () => {
              confirmCount++;
              if (!accept) throw "cancel";
            }
          }
        }
      }[name])
  };
  vm.runInNewContext(
    compile(read("composables/useUnsavedChanges.ts")),
    sandbox
  );
  sandbox.exports.useUnsavedChanges(dirty, busy);
  mounted();
  assert.equal(await leave(), false);
  accept = true;
  assert.equal(await leave(), true);
  assert.equal(
    await update({ path: "/user/post/2/edit" }, { path: "/user/post/1/edit" }),
    true
  );
  busy.value = true;
  assert.equal(await leave(), false);
  assert.equal(confirmCount, 3);
  const event = {
    preventDefault() {
      this.prevented = true;
    }
  };
  listeners.beforeunload(event);
  assert.equal(event.prevented, true);
  assert.equal(event.returnValue, "");
  dirty.value = false;
  busy.value = false;
  assert.equal(await leave(), true);
  cleanup();
  assert.equal(listeners.beforeunload, undefined);
});

test("native cover selection resets file input after failure so the same file can be selected again", async () => {
  let calls = 0;
  const h = editor("commodity", {
    uploadFileUsingPost: async () => {
      calls++;
      return { code: 500, message: "上传失败" };
    }
  });
  try {
    const input = { files: [{ name: "cover.png" }], value: "cover.png" };
    await h.selectCover({ target: input });
    assert.equal(input.value, "");
    input.value = "cover.png";
    await h.selectCover({ target: input });
    assert.equal(calls, 2);
    assert.equal(input.value, "");
  } finally {
    h.dispose();
  }
});
