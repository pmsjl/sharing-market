import { AUTH_MASCOT_SPRITES } from "@/generated/authMascotSprites";

export type MascotGait = keyof typeof AUTH_MASCOT_SPRITES;
export const MASCOT_GAITS = {
  walk: { speed: 90, strideDistance: 108 },
  run: { speed: 330, strideDistance: 214.5 }
} as const;

export const frameAtDistance = (distance: number, gait: MascotGait) => {
  const phase = Math.abs(distance) / MASCOT_GAITS[gait].strideDistance;
  return Math.floor((phase % 1) * AUTH_MASCOT_SPRITES[gait].frameCount);
};

export const nearestContactFrame = (frame: number, gait: MascotGait) => {
  const { contactFrames, frameCount } = AUTH_MASCOT_SPRITES[gait];
  const gap = (candidate: number) => {
    const delta = Math.abs(candidate - frame);
    return Math.min(delta, frameCount - delta);
  };
  return (contactFrames as readonly number[]).reduce<number>(
    (best, candidate) => (gap(candidate) < gap(best) ? candidate : best),
    contactFrames[0]
  );
};

export const spriteLayout = (gait: MascotGait) => {
  const data = AUTH_MASCOT_SPRITES[gait];
  const scale = data.displayHeight / data.frameHeight;
  return {
    width: data.frameWidth * scale,
    height: data.displayHeight,
    anchor: data.anchorX * scale,
    offset: data.offsetX
  };
};

// A trapezoidal velocity profile, including triangular profiles for short legs.
export const createMotionProfile = (distance: number, speed: number) => {
  const length = Math.abs(distance);
  const ramp = Math.min(0.12, length / speed);
  return { length, speed, ramp, duration: length / speed + ramp };
};

export const distanceAtTime = (
  elapsed: number,
  profile: ReturnType<typeof createMotionProfile>
) => {
  const { length, speed, ramp, duration } = profile;
  if (!length || elapsed <= 0) return 0;
  if (elapsed >= duration) return length;
  if (elapsed < ramp) return (speed * elapsed * elapsed) / (2 * ramp);
  if (elapsed > duration - ramp) {
    return length - (speed * (duration - elapsed) ** 2) / (2 * ramp);
  }
  return speed * (elapsed - ramp / 2);
};

export const spriteStyle = (gait: MascotGait) => {
  const data = AUTH_MASCOT_SPRITES[gait];
  const layout = spriteLayout(gait);
  return {
    backgroundImage: `url(${data.src})`,
    width: `${layout.width}px`,
    height: `${layout.height}px`,
    marginLeft: `${layout.offset - layout.anchor}px`,
    backgroundSize: `${layout.width * data.frameCount}px ${layout.height}px`,
    transformOrigin: `${layout.anchor}px 100%`
  };
};
