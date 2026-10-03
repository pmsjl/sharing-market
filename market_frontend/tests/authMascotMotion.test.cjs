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
const motion = load("utils/authMascotMotion.ts");
const { AUTH_MASCOT_SPRITES: sprites } = load("generated/authMascotSprites.ts");
const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-8, `${actual} != ${expected}`);

test("gaits advance with distance, including exact loop boundaries and reversal", () => {
  for (const gait of ["walk", "run"]) {
    const stride = motion.MASCOT_GAITS[gait].strideDistance;
    assert.equal(motion.frameAtDistance(0, gait), 0);
    assert.equal(motion.frameAtDistance(stride, gait), 0);
    assert.equal(motion.frameAtDistance(2 * stride, gait), 0);
    assert.equal(motion.frameAtDistance(stride - 0.0001, gait), 5);
    for (let i = 0; i < 60; i++) {
      const distance = stride * (i + 0.25) / 6;
      assert.equal(motion.frameAtDistance(distance, gait), i % 6);
      assert.equal(motion.frameAtDistance(-distance, gait), i % 6);
    }
  }
});

test("motion accelerates and brakes continuously with exact endpoints", () => {
  for (const length of [0, 1, 12, 60, 140, 500]) {
    for (const speed of [90, 330]) {
      const p = motion.createMotionProfile(length, speed);
      close(motion.distanceAtTime(-1, p), 0);
      close(motion.distanceAtTime(0, p), 0);
      close(motion.distanceAtTime(p.duration, p), length);
      close(motion.distanceAtTime(p.duration + 1, p), length);
      let previous = 0;
      for (let i = 0; i <= 100; i++) {
        const d = motion.distanceAtTime(p.duration * i / 100, p);
        assert.ok(d >= previous && d <= length);
        previous = d;
      }
      close(motion.distanceAtTime(p.duration / 2, p), length / 2);
      if (length) {
        close(motion.distanceAtTime(p.ramp, p), speed * p.ramp / 2);
        close(motion.distanceAtTime(p.duration - p.ramp, p), length - speed * p.ramp / 2);
        assert.ok(Math.abs(motion.distanceAtTime(p.ramp - 1e-8, p) - motion.distanceAtTime(p.ramp + 1e-8, p)) < 0.00001);
      }
    }
  }
});

test("short legs shrink ramps and negative destinations retain the same speed profile", () => {
  const short = motion.createMotionProfile(1, 330);
  close(short.ramp, 1 / 330);
  close(short.duration, 2 / 330);
  const reverse = motion.createMotionProfile(-140, 90);
  assert.equal(reverse.length, 140);
  close(reverse.ramp, 0.12);
});

test("stopping selects a contact pose, including wraparound", () => {
  assert.equal(motion.nearestContactFrame(4, "run"), 5);
  for (const gait of ["walk", "run"]) {
    for (let frame = 0; frame < 6; frame++) {
      assert.ok(sprites[gait].contactFrames.includes(motion.nearestContactFrame(frame, gait)));
    }
  }
});

test("display windows and background offsets share the generated frame dimensions", () => {
  for (const gait of ["walk", "run"]) {
    const layout = motion.spriteLayout(gait);
    const style = motion.spriteStyle(gait);
    assert.equal(sprites[gait].frameCount, 6);
    assert.ok(Number.isInteger(sprites[gait].frameWidth));
    close(parseFloat(style.width), layout.width);
    close(parseFloat(style.backgroundSize), layout.width * 6);
    close(parseFloat(style.marginLeft), layout.offset - layout.anchor);
    assert.ok(style.backgroundImage.includes(`mascot-${gait}-clean.webp`));
  }
});

// Exercise the actual composable's hit check, without substituting CSS :hover.
const showcaseSource = fs.readFileSync(path.join(__dirname, "../src/composables/useAuthShowcaseMotion.ts"), "utf8");
const showcaseAst = ts.createSourceFile("showcase.ts", showcaseSource, ts.ScriptTarget.Latest, true);
let hoverExpression;
let visibilityExpression;
function findHover(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText(showcaseAst) === "refreshMascotHover") {
    hoverExpression = node.initializer.getText(showcaseAst);
  }
  if (ts.isVariableDeclaration(node) && node.name.getText(showcaseAst) === "visibilityChanged") {
    visibilityExpression = node.initializer.getText(showcaseAst);
  }
  ts.forEachChild(node, findHover);
}
findHover(showcaseAst);

test("stationary mouse is detected when the character returns beneath it", () => {
  let rect = { left: 50, right: 150, top: 100, bottom: 300 };
  const sandbox = {
    pointerInPage: true, pointer: { x: 100, y: 200 }, mascotHovered: false,
    travel: { getBoundingClientRect: () => rect }
  };
  const hit = () => vm.runInNewContext(`(${hoverExpression})()`, sandbox);
  assert.equal(hit(), true);
  rect = { ...rect, left: -150, right: -50 };
  assert.equal(hit(), false);
  rect = { ...rect, left: 50, right: 150 };
  assert.equal(hit(), true);
  sandbox.pointerInPage = false;
  assert.equal(hit(), false);
});

test("hover boundaries stay rectangular and missing elements cannot trigger sway", () => {
  const sandbox = {
    pointerInPage: true, pointer: { x: 50, y: 100 }, mascotHovered: false,
    travel: { getBoundingClientRect: () => ({ left: 50, right: 150, top: 100, bottom: 300 }) }
  };
  const hit = () => vm.runInNewContext(`(${hoverExpression})()`, sandbox);
  assert.equal(hit(), true);
  sandbox.pointer.x = 150; sandbox.pointer.y = 300;
  assert.equal(hit(), true);
  sandbox.pointer.x = 150.1;
  assert.equal(hit(), false);
  sandbox.travel = undefined;
  assert.equal(hit(), false);
});

test("restoring visibility starts pending hover without resetting unrelated idle timers", () => {
  let hover = true;
  let settled = 0;
  const sandbox = {
    document: { hidden: false }, entrance: undefined, entranceSuspended: false,
    actor: { dataset: { action: "rest" } }, mascotTimer: {}, mascotHovered: false,
    refreshMascotHover: () => { sandbox.mascotHovered = hover; },
    settleIdle: () => { settled++; }, stopHoverSway: () => {}, syncPlayback: () => {}
  };
  const restore = () => vm.runInNewContext(`(${visibilityExpression})()`, sandbox);
  restore();
  assert.equal(settled, 1);
  hover = false;
  restore();
  assert.equal(settled, 1);
  sandbox.mascotTimer = undefined;
  restore();
  assert.equal(settled, 2);
});
