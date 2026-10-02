const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const MarkdownIt = require('markdown-it');
const sandbox = {exports:{},require};
vm.runInNewContext(ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../src/utils/agentMarkdown.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020,esModuleInterop:true}}).outputText,sandbox);
const {cjkStrongPlugin,agentMarkdownPlugins,splitMarkdownTypingUnits}=sandbox.exports;
const md = new MarkdownIt().use(cjkStrongPlugin);
test('renders paired strong around CJK punctuation without changing source text',()=>{
 for(const [text,html] of [
 ['**《活着》**在预算内','<strong>《活着》</strong>在预算内'],
 ['推荐**《活着》**这本书','推荐<strong>《活着》</strong>这本书'],
 ['**注意：**先验货','<strong>注意：</strong>先验货'],
 ['**价格：**12 校园币','<strong>价格：</strong>12 校园币'],
 ['**接口：**USB-C','<strong>接口：</strong>USB-C'],
 ['预算**“三十元”**以内','预算<strong>“三十元”</strong>以内'],
 ['**价格**和**成色**都合适','<strong>价格</strong>和<strong>成色</strong>都合适'],
 ['**推荐 *认真检查*：**先验货','<strong>推荐 <em>认真检查</em>：</strong>先验货']
 ]) assert.equal(md.renderInline(text),html,text);
});
test('preserves code, escaped markers, unpaired or spaced delimiters and standard English Markdown',()=>{
 const standard=new MarkdownIt();
 for(const text of ['`**《活着》**`','\\*\\*注意：\\*\\*先验货','**未闭合','** 加粗 **','***nested*** and **bold**','word**punctuation!**word','[**link**](https://example.com)','![**alt**](/img.png)','1. first\n2. **second**','| a | b |\n|---|---|\n|1|2|','```md\n**注意：**先验货\n```']) assert.equal(md.render(text),standard.render(text),text);
 assert.ok(!md.renderInline('**<script>alert(1)</script>**').includes('<script>'));
});
test('only Agent instances receive the compatibility plugin and all built-ins remain',()=>{
 const base=[{type:'codeTabs',options:{editorId:'post-preview'},plugin:()=>{}},{type:'heading',options:{},plugin:()=>{}}];
 assert.equal(agentMarkdownPlugins(base),base);
 const agent=[{...base[0],options:{editorId:'agent-answer-123'}},base[1]];
 const configured=agentMarkdownPlugins(agent);assert.equal(configured.length,3);assert.equal(configured[0],agent[0]);assert.equal(configured[1],agent[1]);assert.equal(agent.length,2);
});
test('typing units reveal formatted blocks whole, preserve source and keep plain text progressive',()=>{
 const source='先看看\n\n**《活着》**在预算内\n\n[商品](https://example.com)\n\n```md\n**原样显示**\n```\n\n- **成色**：九成新\n- 价格：12\n\n最后聊聊';
 const units=splitMarkdownTypingUnits(source,Array.from);
 assert.equal(units.join(''),source);assert.equal(units[0],'先');
 assert.ok(units.includes('**《活着》**在预算内\n'));assert.ok(units.includes('```md\n**原样显示**\n```\n'));
 assert.ok(units.includes('- **成色**：九成新\n- 价格：12\n\n'));
 const multiline='**重要\n提醒：**先验货';assert.equal(splitMarkdownTypingUnits(multiline,Array.from).length,1);
 assert.equal(splitMarkdownTypingUnits('段落\r\n\r\n**重点**',Array.from).join(''),'段落\n\n**重点**');
});
