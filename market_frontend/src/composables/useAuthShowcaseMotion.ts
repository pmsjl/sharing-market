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
import { AUTH_MASCOT_SPRITES } from "@/generated/authMascotSprites";
import {
  createMotionProfile,
  distanceAtTime,
  frameAtDistance,
  MASCOT_GAITS,
  nearestContactFrame,
  spriteLayout,
  type MascotGait
} from "@/utils/authMascotMotion";

// Presentation state lasts only while moving between the two auth routes.
const session = { phases: [0, 0], entered: false };
const isAuthRoute = (path: string) => path === "/login" || path === "/register";

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
        const probes: HTMLImageElement[] = [];
        const setFrame = (gait: MascotGait, frame: number) => {
          const sprite = gait === "run" ? spriteRun : spriteWalk;
          if (sprite)
            sprite.style.backgroundPositionX = `${
              -frame * spriteLayout(gait).width
            }px`;
        };
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
        let hoverSway: gsap.core.Timeline | undefined;
        let pointerInPage = false;
        let mascotHovered = false;
        let lastAction = -1;
        let hovered = false;
        let resizeFrame = 0;
        let pointerFrame = 0;
        let pointer = { x: 0, y: 0 };
        let centers: Array<{ x: number; y: number }> = [];
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
            if (!mascotHovered && !hoverSway) mascotTimer?.resume();
          }
          if (paused || isProtecting.value) hoverSway?.pause();
          else hoverSway?.resume();
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
        };
        const rebuildLoops = () => {
          if (disposed) return;
          if (
            actor?.dataset.action === "peek" ||
            actor?.dataset.action === "walk"
          ) {
            // A resized form can move into the old destination. Reset before measuring.
            mascotAction?.kill();
            mascotAction = undefined;
            hoverSway?.kill();
            hoverSway = undefined;
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
              if (!disposed) settleIdle();
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

        const refreshMascotHover = () => {
          // CSS :hover may lag when a transformed actor moves under a stationary
          // mouse. Use the stable wrapper and the latest real mouse coordinates.
          const rect = travel?.getBoundingClientRect();
          mascotHovered = !!(
            pointerInPage &&
            rect &&
            pointer.x >= rect.left &&
            pointer.x <= rect.right &&
            pointer.y >= rect.top &&
            pointer.y <= rect.bottom
          );
          return mascotHovered;
        };
        const startHoverSway = () => {
          if (
            disposed ||
            !allowed ||
            pointerFrozen() ||
            !pose ||
            !actor ||
            !mascotHovered ||
            mascotAction ||
            hoverSway
          )
            return;
          mascotTimer?.kill();
          mascotTimer = undefined;
          actor.dataset.action = "sway";
          hoverSway = gsap
            .timeline()
            .to(pose, { rotation: -4, duration: 0.45, ease: "sine.inOut" })
            .to(pose, {
              rotation: 4,
              duration: 0.65,
              ease: "sine.inOut",
              repeat: -1,
              yoyo: true
            });
          syncPlayback();
        };
        const settleIdle = (first = false) => {
          if (disposed || !allowed || !actor || isProtecting.value) return;
          refreshMascotHover();
          if (mascotHovered) startHoverSway();
          else scheduleMascot(first);
        };
        const stopHoverSway = () => {
          if (!hoverSway || !pose || !actor) return;
          hoverSway.kill();
          hoverSway = undefined;
          actor.dataset.action = "settle";
          hoverSway = gsap
            .timeline({
              onComplete: () => {
                hoverSway = undefined;
                actor.dataset.action = "rest";
                settleIdle();
              }
            })
            .to(pose, { rotation: 0, duration: 0.25, ease: "sine.out" });
          syncPlayback();
        };
        const mascotEnter = (event: PointerEvent) => {
          if (event.pointerType !== "mouse") return;
          pointerInPage = true;
          pointer = { x: event.clientX, y: event.clientY };
          mascotHovered = true;
          startHoverSway();
        };
        const mascotLeave = () => {
          mascotHovered = false;
          if (actor?.dataset.action === "sway") stopHoverSway();
        };
        const windowLeave = () => {
          pointerInPage = false;
          mascotLeave();
        };
        // Bounds include the mirrored sprite and standing image at the endpoint.
        const availableDistance = (gait: MascotGait, left: boolean) => {
          if (!actor) return 0;
          const rect = actor.getBoundingClientRect();
          const center = rect.left + rect.width / 2;
          const layout = spriteLayout(gait);
          const origin = center + layout.offset;
          // Include both orientations: turning around must also remain safe.
          const spriteLeft =
            origin - Math.max(layout.anchor, layout.width - layout.anchor);
          const spriteRight =
            origin + Math.max(layout.anchor, layout.width - layout.anchor);
          const scene = page.getBoundingClientRect();
          const form = page
            .querySelector(".auth-panel-content")
            ?.getBoundingClientRect();
          const safeRight = Math.min(
            scene.right - 8,
            (form?.left ?? scene.right) - 48
          );
          return left
            ? Math.max(0, Math.min(rect.left, spriteLeft) - scene.left - 8)
            : Math.max(0, safeRight - Math.max(rect.right, spriteRight));
        };
        const addTravelLeg = (
          timeline: gsap.core.Timeline,
          gait: MascotGait,
          from: number,
          to: number
        ) => {
          const profile = createMotionProfile(
            to - from,
            MASCOT_GAITS[gait].speed
          );
          const clock = { elapsed: 0 };
          const direction = to < from ? -1 : 1;
          timeline
            .add(() => {
              setFrame(gait, 0);
              setMoving(gait, direction < 0);
            })
            .to(clock, {
              elapsed: profile.duration,
              duration: profile.duration,
              ease: "none",
              onUpdate: () => {
                const distance = distanceAtTime(clock.elapsed, profile);
                gsap.set(travel, { x: from + direction * distance });
                setFrame(gait, frameAtDistance(distance, gait));
              }
            })
            .add(() => {
              gsap.set(travel, { x: to });
              setFrame(
                gait,
                nearestContactFrame(frameAtDistance(profile.length, gait), gait)
              );
            })
            .to({}, { duration: 0.08 })
            .add(() => setMoving(null));
        };
        const scheduleMascot = (first = false) => {
          if (disposed || !allowed || !actor || !travel || !pose) return;
          mascotTimer?.kill();
          mascotTimer = gsap.delayedCall(
            first ? 4 + Math.random() * 3 : 10 + Math.random() * 6,
            () => {
              mascotTimer = undefined;
              if (disposed || isProtecting.value) return;
              if (refreshMascotHover()) {
                startHoverSway();
                return;
              }
              const actions = ["hop", "peek", "walk"] as const;
              let action = Math.floor(Math.random() * actions.length);
              if (action === lastAction) action = (action + 1) % actions.length;
              lastAction = action;
              const name = actions[action];
              const gait = name === "peek" ? "run" : "walk";
              const distance =
                name === "hop"
                  ? 0
                  : Math.min(
                      availableDistance(gait, name === "walk"),
                      name === "walk" ? 60 + Math.random() * 80 : Infinity
                    );
              if (name !== "hop" && (!spritesReady[gait] || distance < 1)) {
                actor.dataset.action = "rest";
                settleIdle();
                return;
              }
              actor.dataset.action = name;
              mascotAction = gsap.timeline({
                onComplete: () => {
                  mascotAction = undefined;
                  actor.dataset.action = "rest";
                  setMoving(null);
                  settleIdle();
                }
              });
              if (name === "hop") {
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
              } else {
                const destination = name === "walk" ? -distance : distance;
                gsap.set(travel, { x: 0 });
                addTravelLeg(mascotAction, gait, 0, destination);
                mascotAction.to(shadow, { opacity: 0, duration: 0.25 }, 0);
                if (name === "peek") {
                  mascotAction
                    .to(pose, { rotation: 7, duration: 0.3 })
                    .to(pose, { rotation: -3, duration: 0.45 }, "+=0.9")
                    .to(pose, { rotation: 0, duration: 0.3 });
                } else {
                  mascotAction
                    .to(pose, { rotation: -3, duration: 0.45 }, "+=0.5")
                    .to(pose, { rotation: 0, duration: 0.35 });
                }
                addTravelLeg(mascotAction, gait, destination, 0);
                mascotAction.to(shadow, { opacity: 0.2, duration: 0.3 });
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
                if (!disposed) spritesReady[gait] = true;
              };
              probe.onerror = () => {
                if (!disposed) spritesReady[gait] = false;
              };
              probes.push(probe);
              probe.src = AUTH_MASCOT_SPRITES[gait].src;
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
          if (event.pointerType !== "mouse") return;
          pointerInPage = true;
          pointer = { x: event.clientX, y: event.clientY };
          if (pointerFrozen()) return;
          if (refreshMascotHover()) startHoverSway();
          if (!pointerFrame)
            pointerFrame = requestAnimationFrame(updatePointer);
        };
        const resetPointer = () => {
          windowLeave();
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
            hoverSway?.kill();
            hoverSway = undefined;
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
            settleIdle(true);
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
          if (!document.hidden) {
            refreshMascotHover();
            if (actor?.dataset.action === "sway" && !mascotHovered)
              stopHoverSway();
            else if (
              actor?.dataset.action === "rest" &&
              (mascotHovered || !mascotTimer)
            )
              settleIdle();
          }
          syncPlayback();
        };
        const stopPrivacyWatch = watch(isProtecting, privacyChanged);
        if (allowed) {
          travel?.addEventListener("pointerenter", mascotEnter);
          travel?.addEventListener("pointerleave", mascotLeave);
          window.addEventListener("blur", windowLeave);
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
          hoverSway?.kill();
          probes.forEach((probe) => {
            probe.onload = null;
            probe.onerror = null;
          });
          stopPointer();
          setMoving(null);
          spriteRun?.style.removeProperty("background-position-x");
          spriteWalk?.style.removeProperty("background-position-x");
          if (actor) actor.dataset.action = "rest";
          stopPrivacyWatch();
          observer.disconnect();
          cancelAnimationFrame(resizeFrame);
          images.forEach((image) =>
            image.removeEventListener("load", scheduleRebuild)
          );
          travel?.removeEventListener("pointerenter", mascotEnter);
          travel?.removeEventListener("pointerleave", mascotLeave);
          window.removeEventListener("blur", windowLeave);
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
