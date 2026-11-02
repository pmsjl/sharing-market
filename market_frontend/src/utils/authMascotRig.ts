import { AUTH_MASCOT_RIG } from "@/generated/authMascotRig";

export type RigLayer = keyof typeof AUTH_MASCOT_RIG.layers;
export type RigPivot = keyof typeof AUTH_MASCOT_RIG.pivots;

const { canvas, layers, pivots } = AUTH_MASCOT_RIG;
const percent = (value: number) => `${+(value * 100).toFixed(4)}%`;

// Groups fill the whole canvas, so their pivots are canvas percentages.
export const groupOrigin = (pivot: RigPivot) => {
  const [x, y] = pivots[pivot];
  return `${percent(x / canvas.width)} ${percent(y / canvas.height)}`;
};

// Positions a layer inside the canvas, or inside another layer's box.
export const layerStyle = (
  name: RigLayer,
  pivot?: RigPivot,
  within?: RigLayer
) => {
  const [x, y, width, height] = layers[name].box;
  const [left, top, outerWidth, outerHeight] = within
    ? layers[within].box
    : [0, 0, canvas.width, canvas.height];
  const style: Record<string, string> = {
    left: percent((x - left) / outerWidth),
    top: percent((y - top) / outerHeight),
    width: percent(width / outerWidth),
    height: percent(height / outerHeight)
  };
  if (pivot) {
    const [px, py] = pivots[pivot];
    style.transformOrigin = `${percent((px - x) / width)} ${percent(
      (py - y) / height
    )}`;
  }
  return style;
};

export const layerSrc = (name: RigLayer) => layers[name].src;

// Canvas point between the eyes, used as the origin of the gaze.
export const FACE_CENTER = { x: 271, y: 222 } as const;
// Degrees of head tilt, and iris travel as a percent of the iris layer.
export const GAZE_LIMITS = { rotation: 4, iris: 8 } as const;

export const faceAnchor = (rect: {
  left: number;
  top: number;
  width: number;
  height: number;
}) => ({
  x: rect.left + (rect.width * FACE_CENTER.x) / canvas.width,
  y: rect.top + (rect.height * FACE_CENTER.y) / canvas.height
});

// Head tilt and iris offset (percent of the iris layer) toward a screen point.
export const gazeTarget = (dx: number, dy: number) => {
  const length = Math.max(1, Math.hypot(dx, dy));
  const reach = Math.min(1, length / 240);
  const clamp = (value: number, limit: number) =>
    Math.max(-limit, Math.min(limit, value));
  return {
    rotation: clamp(dx / 90, GAZE_LIMITS.rotation),
    irisX: (dx / length) * reach * GAZE_LIMITS.iris,
    irisY: (dy / length) * reach * GAZE_LIMITS.iris
  };
};

// Delays in seconds; `random` is a value in [0, 1).
export const blinkDelay = (random: number) => 2.5 + random * 3.5;
export const waveDelay = (random: number) => 14 + random * 8;
// Breathing lifts the shoulders by 1.2% of the distance from the feet.
export const BREATH_SCALE = 1.012;
export const BREATH_LIFT_PERCENT =
  -((pivots.feet[1] - pivots.neck[1]) / canvas.height) *
  (BREATH_SCALE - 1) *
  100;
