const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const cache = new Map();
function load(relative) {
  if (cache.has(relative)) return cache.get(relative);
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src", relative), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  const sandbox = { exports: {}, require: name => load(name.replace("@/", "") + ".ts") };
  vm.runInNewContext(code, sandbox);
  cache.set(relative, sandbox.exports);
  return sandbox.exports;
}
const rig = load("utils/authMascotRig.ts");
const { AUTH_MASCOT_RIG: data } = load("generated/authMascotRig.ts");
// Styles round percentages to four decimals, far below one canvas pixel.
const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 0.01, `${actual} != ${expected}`);

test("every layer sits inside the canvas and its file exists", () => {
  for (const [name, layer] of Object.entries(data.layers)) {
    const [x, y, width, height] = layer.box;
    assert.ok(x >= 0 && y >= 0 && width > 0 && height > 0, name);
    assert.ok(x + width <= data.canvas.width && y + height <= data.canvas.height, name);
    assert.ok(fs.existsSync(path.join(__dirname, "../public", layer.src)), layer.src);
  }
});

test("layer styles reproduce the box, and pivots map to the same canvas point", () => {
  for (const name of Object.keys(data.layers)) {
    const [x, y, width, height] = data.layers[name].box;
    const style = rig.layerStyle(name, "neck");
    close(parseFloat(style.left) / 100 * data.canvas.width, x);
    close(parseFloat(style.top) / 100 * data.canvas.height, y);
    close(parseFloat(style.width) / 100 * data.canvas.width, width);
    close(parseFloat(style.height) / 100 * data.canvas.height, height);
    const [ox, oy] = style.transformOrigin.split(" ").map(parseFloat);
    close(x + ox / 100 * width, data.pivots.neck[0]);
    close(y + oy / 100 * height, data.pivots.neck[1]);
  }
  const [gx, gy] = rig.groupOrigin("feet").split(" ").map(parseFloat);
  close(gx / 100 * data.canvas.width, data.pivots.feet[0]);
  close(gy / 100 * data.canvas.height, data.pivots.feet[1]);
});

test("nested layers are positioned relative to their container", () => {
  const style = rig.layerStyle("irises", undefined, "eye-whites");
  const [ix, iy, iw] = data.layers.irises.box;
  const [wx, wy, ww, wh] = data.layers["eye-whites"].box;
  close(parseFloat(style.left) / 100 * ww, ix - wx);
  close(parseFloat(style.top) / 100 * wh, iy - wy);
  close(parseFloat(style.width) / 100 * ww, iw);
  assert.equal(style.transformOrigin, undefined);
});

test("gaze turns toward the target and never exceeds its limits", () => {
  const { rotation, iris } = rig.GAZE_LIMITS;
  for (const [dx, dy] of [[0, 0], [5, 0], [-5, 3], [400, 0], [-900, -900], [0, 2000], [120, -40]]) {
    const gaze = rig.gazeTarget(dx, dy);
    assert.ok(Math.abs(gaze.rotation) <= rotation);
    assert.ok(Math.hypot(gaze.irisX, gaze.irisY) <= iris + 1e-9);
    if (dx) assert.equal(Math.sign(gaze.rotation), Math.sign(dx));
    if (dy) assert.equal(Math.sign(gaze.irisY), Math.sign(dy));
  }
  close(rig.gazeTarget(-10000, 0).rotation, -rotation);
  close(rig.gazeTarget(0, 10000).irisY, iris);
  const still = rig.gazeTarget(0, 0);
  assert.equal(still.rotation, 0);
  assert.equal(still.irisX, 0);
  assert.equal(still.irisY, 0);
});

test("the face anchor scales with the rendered canvas", () => {
  const anchor = rig.faceAnchor({ left: 100, top: 50, width: 315, height: 640 });
  close(anchor.x, 100 + rig.FACE_CENTER.x / 2);
  close(anchor.y, 50 + rig.FACE_CENTER.y / 2);
});

test("blink and wave delays stay within their ranges", () => {
  for (const random of [0, 0.25, 0.5, 0.999999]) {
    const blink = rig.blinkDelay(random);
    const wave = rig.waveDelay(random);
    assert.ok(blink >= 2.5 && blink < 6);
    assert.ok(wave >= 14 && wave < 22);
  }
});

test("breathing keeps the shoulders attached to the stretched torso", () => {
  const { feet, neck } = data.pivots;
  const stretched = (feet[1] - neck[1]) * (rig.BREATH_SCALE - 1);
  close(-rig.BREATH_LIFT_PERCENT / 100 * data.canvas.height, stretched);
});

// Exercise the composable's reaction gate without mounting Vue.
const showcaseSource = fs.readFileSync(path.join(__dirname, "../src/composables/useAuthShowcaseMotion.ts"), "utf8");
const showcaseAst = ts.createSourceFile("showcase.ts", showcaseSource, ts.ScriptTarget.Latest, true);
let reactExpression;
(function find(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText(showcaseAst) === "react") {
    reactExpression = ts
      .transpile(node.initializer.getText(showcaseAst), { target: ts.ScriptTarget.ES2020 })
      .trim()
      .replace(/;$/, "");
  }
  ts.forEachChild(node, find);
})(showcaseAst);

test("reactions resolve at once without an animated mascot, and after it finishes otherwise", async () => {
  const sandbox = { Promise, reactor: undefined };
  const react = vm.runInNewContext(`(${reactExpression})`, sandbox);
  await react("success");
  let finish;
  sandbox.reactor = (kind, done) => {
    assert.equal(kind, "error");
    finish = done;
  };
  let settled = false;
  const pending = react("error").then(() => (settled = true));
  await Promise.resolve();
  assert.equal(settled, false);
  finish();
  await pending;
  assert.equal(settled, true);
});
