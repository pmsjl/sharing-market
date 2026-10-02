const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/utils/shareUrl.ts'), 'utf8');
const sandbox = { exports: {}, URL, URLSearchParams };
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, sandbox);
const build = sandbox.exports.buildPublicShareUrl;
test('hash share URLs remove conversation context while preserving destination and other parameters', () => {
  const result = new URL(build('https://market.example/?from=agent&lang=zh#/user/post/7?conversationId=private&from=agent&topic=book'));
  assert.equal(result.search, '?lang=zh');
  assert.equal(result.hash, '#/user/post/7?topic=book');
});
test('share URLs with only navigation context have no empty query suffix', () => {
  assert.equal(build('https://market.example/#/user/commodity/detail/9?from=agent&conversationId=private'), 'https://market.example/#/user/commodity/detail/9');
});
test('ordinary and encoded share destinations survive unchanged', () => {
  const url = 'https://market.example/user/post/7?q=%E4%B9%A6#chapter';
  assert.equal(build(url), url);
});
