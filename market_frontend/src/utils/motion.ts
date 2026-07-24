import gsap from "gsap";

const reduceMotion =
  typeof window !== "undefined" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export const animateIn = (target: gsap.TweenTarget, delay = 0) => {
  if (reduceMotion) {
    return;
  }

  gsap.from(target, {
    autoAlpha: 0,
    y: 18,
    duration: 0.48,
    delay,
    ease: "power3.out",
    stagger: 0.06
  });
};

export const liftOnHover = (target: HTMLElement | null) => {
  if (!target || reduceMotion) {
    return;
  }

  const enter = () =>
    gsap.to(target, { y: -4, duration: 0.22, ease: "power2.out" });
  const leave = () =>
    gsap.to(target, { y: 0, duration: 0.22, ease: "power2.out" });

  target.addEventListener("mouseenter", enter);
  target.addEventListener("mouseleave", leave);
};

/**
 * 盖章动画：印章从 1.6 倍大小、-12° 倾斜落下并轻微回弹。
 * 用于收藏、点赞、支付状态等「确认」时刻。
 */
export const stampIn = (target: gsap.TweenTarget, delay = 0) => {
  if (reduceMotion) {
    return;
  }

  gsap.fromTo(
    target,
    { autoAlpha: 0, scale: 1.6, rotate: -12 },
    {
      autoAlpha: 1,
      scale: 1,
      rotate: -8,
      duration: 0.42,
      delay,
      ease: "back.out(2.2)"
    }
  );
};

/**
 * 便签贴上动画：从上方 -10px 带随机微旋转落定。
 */
export const pinOn = (target: gsap.TweenTarget, delay = 0) => {
  if (reduceMotion) {
    return;
  }

  gsap.from(target, {
    autoAlpha: 0,
    y: -10,
    rotate: () => gsap.utils.random(-3, 3),
    duration: 0.38,
    delay,
    ease: "back.out(1.8)",
    stagger: 0.08
  });
};

/**
 * 视差微浮：鼠标在容器内移动时，目标元素按深度系数 ±max px 漂移。
 * 返回清理函数；reduced-motion 下不绑定任何监听。
 */
export const parallaxFloat = (
  container: HTMLElement | null,
  target: gsap.TweenTarget,
  max = 6
) => {
  if (!container || reduceMotion) {
    return () => undefined;
  }

  const xTo = gsap.quickTo(target, "x", { duration: 0.5, ease: "power3.out" });
  const yTo = gsap.quickTo(target, "y", { duration: 0.5, ease: "power3.out" });

  const onMove = (event: MouseEvent) => {
    const rect = container.getBoundingClientRect();
    const relX = (event.clientX - rect.left) / rect.width - 0.5;
    const relY = (event.clientY - rect.top) / rect.height - 0.5;
    xTo(relX * max * 2);
    yTo(relY * max * 2);
  };

  const onLeave = () => {
    xTo(0);
    yTo(0);
  };

  container.addEventListener("mousemove", onMove);
  container.addEventListener("mouseleave", onLeave);

  return () => {
    container.removeEventListener("mousemove", onMove);
    container.removeEventListener("mouseleave", onLeave);
  };
};
