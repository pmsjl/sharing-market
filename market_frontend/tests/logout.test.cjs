const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const axios = require('axios');
const vue = require('vue');

const read = file => fs.readFileSync(path.join(__dirname, '../src', file), 'utf8');
const compile = source => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText;
const flush = () => new Promise(resolve => setImmediate(resolve));

function requestFixture(token = 'session-a') {
  const store = { token }, notifications = [];
  const sandbox = { exports: {}, process: { env: {} }, require: name => {
    if (name === 'axios') return { default: axios };
    if (name === 'element-plus') return { ElMessage: message => notifications.push(message) };
    if (name === '@/store/modules/user') return { default: () => store };
    throw new Error(`Unexpected import: ${name}`);
  } };
  vm.runInNewContext(compile(read('utils/request.ts')), sandbox);
  const request = sandbox.exports.default;
  const pending = [];
  request.defaults.adapter = config => new Promise((resolve, reject) => pending.push({
    config,
    succeed: data => resolve({ data, status: 200, statusText: 'OK', headers: {}, config }),
    fail: (status, data = '') => reject(new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', config, {}, {
      data, status, statusText: 'Error', headers: {}, config
    }))
  }));
  return { store, request, pending, notifications };
}

test('requests use the current session and never send Bearer null after logout', async () => {
  const f = requestFixture();
  const signedIn = f.request.get('/profile');
  await flush();
  assert.equal(f.pending[0].config.headers.Authorization, 'Bearer session-a');
  assert.equal(f.pending[0].config.headers.satoken, 'session-a');
  f.pending[0].succeed({ code: 200 });
  await signedIn;
  f.store.token = '';
  const anonymous = f.request.get('/login-options');
  await flush();
  assert.equal(f.pending[1].config.headers.Authorization, undefined);
  assert.equal(f.pending[1].config.headers.satoken, undefined);
  f.pending[1].succeed({ code: 200 });
  await anonymous;
});

test('a pending 401 after logout or switching accounts rejects without an expiry notification', async () => {
  for (const nextToken of ['', 'session-b']) {
    const f = requestFixture();
    const outcome = f.request.get('/profile').catch(error => error);
    await flush();
    f.store.token = nextToken;
    f.pending[0].fail(401);
    const error = await outcome;
    assert.equal(error.response.status, 401);
    assert.equal(error.requestMessageShown, true);
    assert.deepEqual(f.notifications, []);
  }
});

test('real expiry still reports once; unauthenticated and silent requests retain their own behavior', async () => {
  for (const scenario of [
    { token: 'session-a', config: {}, message: '登录状态已失效，请重新登录' },
    { token: '', config: {}, message: '请先登录' },
    { token: 'session-a', config: { silent: true }, message: undefined }
  ]) {
    const f = requestFixture(scenario.token);
    const outcome = f.request.get('/protected', scenario.config).catch(error => error);
    await flush();
    f.pending[0].fail(401);
    const error = await outcome;
    assert.equal(error.response.status, 401);
    assert.deepEqual(f.notifications.map(item => item.message), scenario.message ? [scenario.message] : []);
  }
});

test('permission failures are not swallowed when the session ends', async () => {
  const f = requestFixture();
  const outcome = f.request.get('/admin').catch(error => error);
  await flush();
  f.store.token = '';
  f.pending[0].fail(403);
  await outcome;
  assert.deepEqual(f.notifications.map(item => item.message), ['无权访问']);
});

function profileFixture(load) {
  const store = { token: 'session-a', userName: '', avatar: '', userAccount: '' };
  const source = read('layout/tabbar/setting/index.vue').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const sandbox = { exports: {}, defineProps: () => {}, require: name => {
    if (name === 'vue') return { ...vue, onMounted: () => {} };
    if (name === 'vue-router') return { useRouter: () => ({ replace: async () => {} }) };
    if (name === '@/store/modules/user') return { default: () => store };
    if (name.includes('/store/modules/')) return { default: () => ({}) };
    if (name === '@/utils/token') return { GET_ID: () => '1' };
    if (name === '@/api/userController') return { getUserVoByIdUsingGet: load };
    if (name === '@/utils/theme') return { getStoredThemeMode: () => 'light', getStoredAccentPreset: () => 'campus-blue' };
    if (name === 'element-plus' || name === '@element-plus/icons-vue') return {};
    throw new Error(`Unexpected import: ${name}`);
  } };
  vm.runInNewContext(compile(source + '\nexport { getUserInformationById };'), sandbox);
  return { store, loadProfile: sandbox.exports.getUserInformationById };
}

test('a late profile response cannot restore identity after logout or in a different account', async () => {
  for (const nextToken of ['', 'session-b']) {
    let resolve;
    const f = profileFixture(() => new Promise(done => { resolve = done; }));
    const loading = f.loadProfile();
    f.store.token = nextToken;
    resolve({ code: 200, data: { userName: '旧账号', userAvatar: 'old.png', userAccount: 'old' } });
    await loading;
    assert.equal(f.store.userName, '');
    assert.equal(f.store.avatar, '');
    assert.equal(f.store.userAccount, '');
  }
});

test('profile loading handles rejection and skips requests once logged out', async () => {
  let calls = 0;
  const f = profileFixture(async () => { calls++; throw new Error('Unauthorized'); });
  await f.loadProfile();
  f.store.token = '';
  await f.loadProfile();
  assert.equal(calls, 1);
});
