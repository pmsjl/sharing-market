const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const sandbox = { exports: {}, require, URL };
vm.runInNewContext(ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../src/utils/postPreview.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,esModuleInterop:true,target:ts.ScriptTarget.ES2020}}).outputText,sandbox);
const {buildPostPreview: preview, safePostImage: safe}=sandbox.exports;
test('extracts readable Markdown text without syntax, URLs, HTML blocks or fenced code',()=>{
 const p=preview('## 验货\n\n**检查**[书脊](https://example.com)和 `版本`。\n\n```js\nsecret()\n```\n\n<div>HTML block</div>\n\n正文 <b>文字</b>');
 assert.equal(p.summary,'验货 检查书脊和 版本。 正文 文字');
});
test('first allowed Markdown image wins; reference images and relative paths are supported',()=>{
 const p=preview('![skip](//example.com/x.png)\n![书的封面][cover]\n![later](/later.jpg)\n\n[cover]: /images/book.jpg');
 assert.equal(p.thumbnail,'/images/book.jpg');assert.equal(p.imageAlt,'书的封面');assert.equal(p.summary,'');
});
test('rejects unsafe and ambiguous image sources',()=>{
 for(const src of ['javascript:alert(1)','data:image/png;base64,aaa','//evil/a','\\\\evil\\a','file:///a','https://user:pass@host/a','https://','a\nb.png','#image','?image'])assert.equal(safe(src),undefined,src);
 for(const src of ['/a.jpg','./a.jpg','images/a.jpg','https://example.com/a.jpg','http://example.com/a.png'])assert.equal(safe(src),src);
});
test('handles missing content and limits Unicode summaries to 160 characters',()=>{
 assert.equal(preview().summary,'');assert.equal(preview().thumbnail,undefined);
 const text=preview('书📚'.repeat(100)).summary;assert.equal(Array.from(text).length,160);assert.ok(text.endsWith('…'));assert.ok(!text.includes('\uFFFD'));
});
