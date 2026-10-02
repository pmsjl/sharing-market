const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../src/utils/theme.ts'), 'utf8');
function fixture() {
 const storage=new Map(), props=new Map(), classes=new Set();
 const html={dataset:{},style:{setProperty:(k,v)=>props.set(k,v)},classList:{toggle:(k,on)=>on?classes.add(k):classes.delete(k)}};
 const sandbox={exports:{},document:{documentElement:html},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)}};
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,sandbox);
 return {...sandbox.exports,storage,props,html,classes};
}
const linear=n=>(n/=255)<=0.04045?n/12.92:((n+0.055)/1.055)**2.4;
const luminance=hex=>{const rgb=hex.match(/[a-f\d]{2}/gi).map(v=>linear(parseInt(v,16)));return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;};
const contrast=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);};
const scss=fs.readFileSync(path.join(__dirname,'../src/styles/variable.scss'),'utf8').split('html.dark,')[1];
const color=name=>scss.match(new RegExp('--'+name+':\\s*(#[a-f\\d]{6})','i'))[1];
test('night text and accent colors remain readable on all content surfaces',()=>{
 const f=fixture();const backgrounds=['market-canvas','market-surface','market-surface-soft','market-surface-raised'].map(color);
 for(const name of ['market-heading','market-ink','market-muted','market-faint','market-success','market-warning','market-danger']) for(const bg of backgrounds) assert.ok(contrast(color(name),bg)>=4.5,`${name} on ${bg}: ${contrast(color(name),bg)}`);
 for(const scale of Object.values(f.THEME_ACCENTS).map(x=>x.night)){
  for(const bg of backgrounds)assert.ok(contrast(scale.primary,bg)>=4.5);
  for(const bg of [scale.primary,scale.hover])assert.ok(contrast(color('market-on-primary'),bg)>=4.5);
  assert.ok(contrast(color('market-ink'),scale.soft)>=4.5);
 }
});
test('control boundaries and keyboard focus remain visible on raised surfaces',()=>{
 const bg=color('market-surface-raised');assert.ok(contrast(color('market-control-border'),bg)>=3);
 for(const scale of Object.values(fixture().THEME_ACCENTS).map(x=>x.night))assert.ok(contrast(scale.focus,bg)>=3);
});
test('all saved accents survive night/light switching and restore without leaking night colors',()=>{
 const f=fixture(), light={ 'campus-blue':'#2563eb',indigo:'#4f46e5','lake-blue':'#0369a1' };
 for(const preset of Object.keys(light)){
  f.applyAccentPreset(preset);f.applyThemeMode('night');assert.ok(f.classes.has('dark'));
  assert.equal(f.props.get('--market-primary'),f.THEME_ACCENTS[preset].night.primary);
  f.restoreTheme();assert.equal(f.html.dataset.accent,preset);assert.equal(f.html.dataset.theme,'night');
  f.applyThemeMode('light');assert.equal(f.props.get('--market-primary'),light[preset]);assert.equal(f.classes.has('dark'),false);
  assert.equal(f.props.get('--el-color-primary-light-9'),f.THEME_ACCENTS[preset].light.light9);
 }
});
test('invalid persisted values restore the existing light/campus-blue defaults',()=>{
 const f=fixture();f.storage.set('market-theme-mode','unknown');f.storage.set('market-theme-accent','removed');f.restoreTheme();
 assert.equal(f.html.dataset.theme,'light');assert.equal(f.html.dataset.accent,'campus-blue');
});
