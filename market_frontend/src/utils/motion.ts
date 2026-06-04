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
