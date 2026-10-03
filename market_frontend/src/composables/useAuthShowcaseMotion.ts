import {
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type Ref
} from "vue";
import { useRouter } from "vue-router";
import gsap from "gsap";

// Presentation state lasts only while moving between the two auth routes.
const session = { phases: [0, 0], entered: false };
const isAuthRoute = (path: string) => path === "/login" || path === "/register";

// Locomotion speeds in display pixels per second; playback stays in step with the sprite cycles.
const RUN_SPEED = 380;
const WALK_SPEED = 130;

export const useAuthShowcaseMotion = (root: Ref<HTMLElement | null>) => {
  const router = useRouter();
  const isProtecting = ref(false);
  const canAnimate = ref(false);
  const copyCounts = ref([2, 2]);
  let media: ReturnType<typeof gsap.matchMedia> | undefined;
  let removePrivacyListeners: (() => void) | undefined;

  onMounted(() => {
    const page = root.value;
    if (!page) return;
    const panel = page.querySelector<HTMLElement>(".auth-market-panel");
    const passwords = () =>
      Array.from(
        panel?.querySelectorAll<HTMLInputElement>("input[name*='password']") ||
          []
      );
    const syncPrivacy = () => {
      isProtecting.value = passwords().some(
        (field) => field === document.activeElement || field.type === "text"
      );
    };
    // Privacy works independently of animations, including touch and reduced motion.
    const privacyObserver = new MutationObserver(syncPrivacy);
    if (panel)
      privacyObserver.observe(panel, {
        attributes: true,
        attributeFilter: ["type"],
        subtree: true,
        childList: true
      });
    panel?.addEventListener("focusin", syncPrivacy);
    panel?.addEventListener("focusout", syncPrivacy);
    syncPrivacy();
    removePrivacyListeners = () => {
      privacyObserver.disconnect();
      panel?.removeEventListener("focusin", syncPrivacy);
      panel?.removeEventListener("focusout", syncPrivacy);
    };
    let hasEntered = session.entered;
    session.entered = true;
    media = gsap.matchMedia();
    media.add(
      {
        motion: "(prefers-reduced-motion: no-preference)",
        desktop: "(min-width: 1024px) and (hover: hover) and (pointer: fine)"
      },
      (context) => {
        let disposed = false;
        const allowed =
          !!context.conditions?.motion && !!context.conditions?.desktop;
        canAnimate.value = allowed;
        const entranceTargets = (
          hasEntered
            ? [".auth-panel-content"]
            : [
                ".showcase-copy",
                ".poster-entrance",
                ".partners-entrance",
                ".auth-panel-content"
              ]
        )
          .map((selector) => page.querySelector<HTMLElement>(selector))
          .filter((element): element is HTMLElement => element !== null);
        const entrance = allowed
          ? gsap.from(entranceTargets, {
              autoAlpha: 0,
              y: hasEntered ? 10 : 20,
              duration: hasEntered ? 0.25 : 0.45,
              stagger: hasEntered ? 0 : 0.12,
              ease: "power3.out"
            })
          : undefined;
        hasEntered = true;

        const showcase = page.querySelector<HTMLElement>(
          ".auth-market-showcase"
        );
        const rows = Array.from(
          page.querySelectorAll<HTMLElement>(".poster-row")
        );
        const loops: Array<gsap.core.Tween | undefined> = [];
        const floats: gsap.core.Tween[] = [];
        const aims = Array.from(
          page.querySelectorAll<HTMLElement>(".partner-aim")
        );
        const pupils = Array.from(
          page.querySelectorAll<SVGElement>(".partner-pupils")
        );
        const actor = page.querySelector<HTMLElement>(".campus-mascot");
        const travel = page.querySelector<HTMLElement>(".mascot-travel");
        const pose = page.querySelector<HTMLElement>(".mascot-pose");
        const shadow = page.querySelector<HTMLElement>(".mascot-shadow");
        const spriteRun = page.querySelector<HTMLElement>(".mascot-run");
        const spriteWalk = page.querySelector<HTMLElement>(".mascot-walk");
        const spritesReady = { run: false, walk: false };
        const setMoving = (gait: "run" | "walk" | null, flipped = false) => {
          spriteRun?.classList.remove("is-active", "is-left");
          spriteWalk?.classList.remove("is-active", "is-left");
          const sprite =
            gait === "run" ? spriteRun : gait === "walk" ? spriteWalk : null;
          if (sprite && gait && spritesReady[gait]) {
            sprite.classList.add("is-active");
            if (flipped) sprite.classList.add("is-left");
            actor?.classList.add("is-moving");
          } else {
            actor?.classList.remove("is-moving");
          }
        };
        let mascotAction: gsap.core.Timeline | undefined;
        let mascotTimer: gsap.core.Tween | undefined;
        let lastAction = -1;
        let hovered = false;
        let resizeFrame = 0;
        let pointerFrame = 0;
        let pointer = { x: 0, y: 0 };
        let centers: Array<{ x: number; y: number }> = [];
        let peek = { x: 0 };
        let entranceSuspended = false;
        const frozen = () => document.hidden;
        const pointerFrozen = () => frozen() || isProtecting.value;
        const stopPointer = () => {
          cancelAnimationFrame(pointerFrame);
          pointerFrame = 0;
          gsap.killTweensOf([...aims, ...pupils]);
        };
        const syncPlayback = (smooth = false) => {
          if (disposed) return;
          const paused = frozen();
          loops.forEach((loop) => {
            if (!loop) return;
            gsap.killTweensOf(loop);
            if (paused) loop.pause();
            else {
              loop.resume();
              if (smooth)
                gsap.to(loop, {
                  timeScale: hovered ? 0.25 : 1,
                  duration: 0.4,
                  ease: "power2.out"
                });
              else loop.timeScale(hovered ? 0.25 : 1);
            }
          });
          floats.forEach((float) => (paused ? float.pause() : float.resume()));
          if (paused || isProtecting.value) {
            mascotAction?.pause();
            mascotTimer?.pause();
          } else {
            mascotAction?.resume();
            mascotTimer?.resume();
          }
          spriteRun?.classList.toggle("is-paused", paused);
          spriteWalk?.classList.toggle("is-paused", paused);
          if (pointerFrozen()) stopPointer();
        };
        const measureScene = () => {
          centers = aims.map((aim) => {
            const rect = aim.getBoundingClientRect();
            return {
              x: rect.left + rect.width / 2,
              y: rect.top + rect.height * 0.35
            };
          });
          const actorRect = actor?.getBoundingClientRect();
          const formRect = page
            .querySelector(".auth-panel-content")
            ?.getBoundingClientRect();
          if (actorRect && formRect) {
            // Keep the entire sprite outside the form's left edge.
            peek = { x: Math.max(0, formRect.left - actorRect.right - 48) };
          }
        };
        const rebuildLoops = () => {
          if (disposed) return;
          if (actor?.dataset.action === "peek") {
            // A resized form can move into the old destination. Reset before measuring.
            mascotAction?.kill();
            mascotAction = undefined;
            gsap.set([travel, pose].filter(Boolean), {
              x: 0,
              y: 0,
              rotation: 0,
              scaleX: 1,
              scaleY: 1
            });
            if (shadow) gsap.set(shadow, { opacity: 0.2 });
            actor.dataset.action = "rest";
            setMoving(null);
            void nextTick().then(() => {
              if (!disposed) scheduleMascot();
            });
          }
          measureScene();
          rows.forEach((row, index) => {
            const track = row.querySelector<HTMLElement>(".poster-track");
            const group = row.querySelector<HTMLElement>(".poster-group");
            if (!track || !group) return;
            const distance = group.offsetWidth;
            if (!distance) return;
            const required = Math.max(
              2,
              Math.ceil(
                (row.parentElement?.clientWidth || row.clientWidth) / distance
              ) + 2
            );
            if (copyCounts.value[index] !== required) {
              copyCounts.value[index] = required;
              void nextTick().then(() => {
                if (!disposed) scheduleRebuild();
              });
            }
            if (!allowed) return;
            const previous = loops[index];
            if (previous) {
              session.phases[index] = previous.progress();
              gsap.killTweensOf(previous);
              previous.kill();
            }
            const reverse = row.dataset.reverse === "true";
            loops[index] = gsap
              .fromTo(
                track,
                { x: reverse ? -distance : 0 },
                {
                  x: reverse ? 0 : -distance,
                  duration: distance / Number(row.dataset.speed),
                  ease: "none",
                  repeat: -1,
                  paused: true
                }
              )
              .progress(session.phases[index]);
          });
          syncPlayback();
        };
        const scheduleRebuild = () => {
          cancelAnimationFrame(resizeFrame);
          resizeFrame = requestAnimationFrame(rebuildLoops);
        };
        const observer = new ResizeObserver(scheduleRebuild);
        rows.forEach((row) => {
          const group = row.querySelector(".poster-group");
          if (group) observer.observe(group);
        });
        if (showcase) observer.observe(showcase);
        if (panel) observer.observe(panel);
        const images = Array.from(page.querySelectorAll("img"));
        images.forEach((image) =>
          image.addEventListener("load", scheduleRebuild)
        );
        // Own loops explicitly: media reversion must not rewind saved progress.
        context.ignore(rebuildLoops);

        const scheduleMascot = (first = false) => {
          if (disposed || !allowed || !actor || !travel || !pose) return;
          mascotTimer?.kill();
          mascotTimer = gsap.delayedCall(
            first ? 4 + Math.random() * 3 : 10 + Math.random() * 6,
            () => {
              if (disposed) return;
              let action = Math.floor(Math.random() * 4);
              if (action === lastAction) action = (action + 1) % 4;
              lastAction = action;
              actor.dataset.action = ["sway", "hop", "peek", "walk"][action];
              mascotAction = gsap.timeline({
                onComplete: () => {
                  actor.dataset.action = "rest";
                  setMoving(null);
                  scheduleMascot();
                }
              });
              if (action === 0) {
                mascotAction
                  .to(pose, {
                    rotation: -4,
                    duration: 0.45,
                    ease: "sine.inOut"
                  })
                  .to(pose, {
                    rotation: 4,
                    duration: 0.65,
                    repeat: 2,
                    yoyo: true,
                    ease: "sine.inOut"
                  })
                  .to(pose, { rotation: 0, duration: 0.45 });
              } else if (action === 1) {
                mascotAction
                  .to(pose, { scaleY: 0.96, scaleX: 1.03, duration: 0.13 })
                  .to(pose, {
                    y: -26,
                    scaleY: 1.02,
                    scaleX: 0.99,
                    rotation: -2,
                    duration: 0.27,
                    ease: "power2.out"
                  })
                  .to(pose, {
                    y: 0,
                    scaleY: 1,
                    scaleX: 1,
                    rotation: 0,
                    duration: 0.4,
                    ease: "bounce.out"
                  });
              } else if (action === 2) {
                // Peek: run over to the form side on the ground line, pause, run back.
                const distance = Math.max(60, peek.x);
                const runTime = distance / RUN_SPEED;
                gsap.set(travel, { x: 0 });
                mascotAction
                  .add(() => setMoving("run"))
                  .to(travel, { x: distance, duration: runTime, ease: "none" })
                  .add(() => setMoving(null))
                  .to(shadow, { opacity: 0, duration: 0.25 }, 0)
                  .to(pose, { rotation: 7, duration: 0.3 })
                  .to(pose, { rotation: -3, duration: 0.45 }, "+=0.9")
                  .to(pose, { rotation: 0, duration: 0.3 })
                  .add(() => setMoving("run", true))
                  .to(travel, { x: 0, duration: runTime, ease: "none" })
                  .to(shadow, { opacity: 0.2, duration: 0.3 }, "<");
              } else {
                // Stroll: walk a random short distance along the ground, then back.
                const distance = 60 + Math.random() * 80;
                const walkTime = distance / WALK_SPEED;
                gsap.set(travel, { x: 0 });
                mascotAction
                  .add(() => setMoving("walk", true))
                  .to(travel, {
                    x: -distance,
                    duration: walkTime,
                    ease: "none"
                  })
                  .add(() => setMoving(null))
                  .to(shadow, { opacity: 0, duration: 0.25 }, 0)
                  .to(pose, { rotation: -3, duration: 0.45 }, "+=0.5")
                  .to(pose, { rotation: 0, duration: 0.35 })
                  .add(() => setMoving("walk"))
                  .to(travel, { x: 0, duration: walkTime, ease: "none" })
                  .to(shadow, { opacity: 0.2, duration: 0.3 }, "<");
              }
              syncPlayback();
            }
          );
          syncPlayback();
        };
        if (allowed)
          context.ignore(() => {
            page
              .querySelectorAll<HTMLElement>(".partner-float")
              .forEach((element, index) => {
                floats.push(
                  gsap.to(element, {
                    y: index % 2 ? -5 : -7,
                    rotation: index % 2 ? 0.6 : -0.6,
                    duration: (6 + index) / 2,
                    repeat: -1,
                    yoyo: true,
                    ease: "sine.inOut",
                    paused: true
                  })
                );
              });
            (["run", "walk"] as const).forEach((gait) => {
              const probe = new Image();
              probe.onload = () => {
                spritesReady[gait] = true;
              };
              probe.onerror = () => {
                spritesReady[gait] = false;
              };
              probe.src = `/generated/auth-partners/mascot-${gait}.webp`;
            });
            scheduleMascot(true);
          });

        const updatePointer = () => {
          pointerFrame = 0;
          if (disposed || pointerFrozen()) return;
          aims.forEach((aim, index) => {
            const center = centers[index];
            if (!center) return;
            const dx = pointer.x - center.x;
            const dy = pointer.y - center.y;
            const length = Math.max(1, Math.hypot(dx, dy));
            const reach = Math.min(4, length / 65);
            gsap.to(pupils[index], {
              x: (dx / length) * reach,
              y: (dy / length) * reach,
              duration: 0.22,
              overwrite: true
            });
            gsap.to(aim, {
              rotation: gsap.utils.clamp(-2, 2, dx / 220),
              duration: 0.3,
              overwrite: true,
              ease: "power2.out"
            });
          });
        };
        const pointerMove = (event: PointerEvent) => {
          if (event.pointerType !== "mouse" || pointerFrozen()) return;
          pointer = { x: event.clientX, y: event.clientY };
          if (!pointerFrame)
            pointerFrame = requestAnimationFrame(updatePointer);
        };
        const resetPointer = () => {
          if (pointerFrozen()) return;
          gsap.to([...aims, ...pupils], {
            rotation: 0,
            x: 0,
            y: 0,
            duration: 0.3,
            overwrite: true
          });
        };
        const pointerEnter = (event: PointerEvent) => {
          if (event.pointerType !== "mouse") return;
          hovered = true;
          syncPlayback(true);
        };
        const pointerLeave = () => {
          hovered = false;
          syncPlayback(true);
        };
        const privacyChanged = () => {
          if (isProtecting.value) {
            stopPointer();
            gsap.set([...aims, ...pupils], { rotation: 0, x: 0, y: 0 });
            mascotAction?.kill();
            mascotAction = undefined;
            gsap.set([travel, pose].filter(Boolean), {
              x: 0,
              y: 0,
              rotation: 0,
              scaleX: 1,
              scaleY: 1
            });
            if (shadow) gsap.set(shadow, { opacity: 0.2 });
            setMoving(null);
            if (actor) actor.dataset.action = "privacy";
          } else if (actor && allowed) {
            actor.dataset.action = "rest";
            scheduleMascot(true);
          }
          syncPlayback();
        };
        const visibilityChanged = () => {
          if (document.hidden && entrance?.isActive()) {
            entrance.pause();
            entranceSuspended = true;
          } else if (!document.hidden && entranceSuspended) {
            entrance?.resume();
            entranceSuspended = false;
          }
          syncPlayback();
        };
        const stopPrivacyWatch = watch(isProtecting, privacyChanged);
        if (allowed) {
          showcase?.addEventListener("pointerenter", pointerEnter);
          showcase?.addEventListener("pointerleave", pointerLeave);
          page.addEventListener("pointermove", pointerMove);
          page.addEventListener("pointerleave", resetPointer);
        }
        document.addEventListener("visibilitychange", visibilityChanged);
        privacyChanged();
        visibilityChanged();

        return () => {
          disposed = true;
          loops.forEach((loop, index) => {
            if (!loop) return;
            session.phases[index] = loop.progress();
            gsap.killTweensOf(loop);
            loop.kill();
          });
          floats.forEach((float) => float.kill());
          mascotAction?.kill();
          mascotTimer?.kill();
          stopPointer();
          setMoving(null);
          spriteRun?.classList.remove("is-paused");
          spriteWalk?.classList.remove("is-paused");
          stopPrivacyWatch();
          observer.disconnect();
          cancelAnimationFrame(resizeFrame);
          images.forEach((image) =>
            image.removeEventListener("load", scheduleRebuild)
          );
          showcase?.removeEventListener("pointerenter", pointerEnter);
          showcase?.removeEventListener("pointerleave", pointerLeave);
          page.removeEventListener("pointermove", pointerMove);
          page.removeEventListener("pointerleave", resetPointer);
          document.removeEventListener("visibilitychange", visibilityChanged);
          entrance?.kill();
          gsap.set(
            page.querySelectorAll(
              ".poster-track, .partner-float, .partner-aim, .partner-pupils, .mascot-travel, .mascot-pose, .mascot-shadow"
            ),
            { clearProps: "transform,opacity" }
          );
        };
      },
      page
    );
  });

  onBeforeUnmount(() => {
    media?.revert();
    removePrivacyListeners?.();
    if (!isAuthRoute(router.currentRoute.value.path)) {
      session.phases = [0, 0];
      session.entered = false;
    }
  });
  return { canAnimate, isProtecting, copyCounts };
};
