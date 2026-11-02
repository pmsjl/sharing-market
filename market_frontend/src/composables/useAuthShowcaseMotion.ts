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
import {
  blinkDelay,
  BREATH_LIFT_PERCENT,
  BREATH_SCALE,
  faceAnchor,
  gazeTarget,
  waveDelay
} from "@/utils/authMascotRig";

// Presentation state lasts only while moving between the two auth routes.
const session = { phases: [0, 0], entered: false };
const isAuthRoute = (path: string) => path === "/login" || path === "/register";

export type MascotReaction = "error" | "success";

export const useAuthShowcaseMotion = (root: Ref<HTMLElement | null>) => {
  const router = useRouter();
  const isProtecting = ref(false);
  // Follows isProtecting, but switches halfway through the animated turn.
  const facingAway = ref(false);
  const canAnimate = ref(false);
  const copyCounts = ref([2, 2]);
  let media: ReturnType<typeof gsap.matchMedia> | undefined;
  let removePrivacyListeners: (() => void) | undefined;
  let reactor: ((kind: MascotReaction, done: () => void) => void) | undefined;

  // Resolves at once when the mascot cannot animate, so callers never wait.
  const react = (kind: MascotReaction) =>
    new Promise<void>((resolve) => {
      if (reactor) reactor(kind, resolve);
      else resolve();
    });

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
    facingAway.value = isProtecting.value;
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
        const firstEntrance = !hasEntered;
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
        const form = page.querySelector<HTMLElement>(".auth-panel-content");
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
        const frame = page.querySelector<HTMLElement>(".mascot-frame");
        const pose = page.querySelector<HTMLElement>(".mascot-pose");
        const shadow = page.querySelector<HTMLElement>(".mascot-shadow");
        const rig = page.querySelector<HTMLElement>(".mascot-rig");
        const part = (selector: string) =>
          rig?.querySelector<HTMLElement>(selector) ?? null;
        const parts = {
          torso: part(".rig-torso"),
          lifts: Array.from(
            rig?.querySelectorAll<HTMLElement>(".rig-lift") || []
          ),
          head: part(".rig-head"),
          tailFollow: part(".rig-tail-follow"),
          tail: part(".rig-tail"),
          charm: part(".rig-charm"),
          arm: part(".rig-arm"),
          irises: part(".rig-irises")
        };
        // Idle loops, gaze, and actions each own their values; one ticker sums them.
        const idle = { breath: 0, drift: 0, tail: 0, charm: 0 };
        const gaze = { rotation: 0, irisX: 0, irisY: 0 };
        const act = { head: 0, tail: 0, charm: 0, arm: 0 };
        const face = { blink: false, hold: false, mouth: "smile" };
        let dirty = true;
        const markDirty = () => (dirty = true);
        // Rotation setters need an explicit unit; GSAP renders it verbatim.
        const setter = (
          target: Element | Element[] | null,
          property: string,
          unit?: string
        ) =>
          target ? gsap.quickSetter(target, property, unit) : () => undefined;
        const setRig = {
          breath: setter(parts.torso, "scaleY"),
          lift: setter(parts.lifts, "yPercent"),
          head: setter(parts.head, "rotation", "deg"),
          tailFollow: setter(parts.tailFollow, "rotation", "deg"),
          tail: setter(parts.tail, "rotation", "deg"),
          charm: setter(parts.charm, "rotation", "deg"),
          arm: setter(parts.arm, "rotation", "deg"),
          irisX: setter(parts.irises, "xPercent"),
          irisY: setter(parts.irises, "yPercent")
        };
        const renderRig = () => {
          if (!dirty || frozen()) return;
          dirty = false;
          const head = idle.drift + gaze.rotation + act.head;
          setRig.breath(1 + idle.breath * (BREATH_SCALE - 1));
          setRig.lift(idle.breath * BREATH_LIFT_PERCENT);
          setRig.head(head);
          setRig.tailFollow(head);
          setRig.tail(idle.tail + act.tail);
          setRig.charm(idle.charm + act.charm);
          setRig.arm(act.arm);
          setRig.irisX(gaze.irisX);
          setRig.irisY(gaze.irisY);
        };
        const applyFace = () => {
          if (!rig) return;
          rig.dataset.eyes = face.blink || face.hold ? "closed" : "open";
          rig.dataset.mouth = face.mouth;
        };
        const gazeTo = {
          rotation: gsap.quickTo(gaze, "rotation", {
            duration: 0.35,
            ease: "power2.out",
            onUpdate: markDirty
          }),
          irisX: gsap.quickTo(gaze, "irisX", {
            duration: 0.2,
            onUpdate: markDirty
          }),
          irisY: gsap.quickTo(gaze, "irisY", {
            duration: 0.2,
            onUpdate: markDirty
          })
        };

        let blinkTimer: gsap.core.Tween | undefined;
        let blinkAction: gsap.core.Timeline | undefined;
        let waveTimer: gsap.core.Tween | undefined;
        let waveAction: gsap.core.Timeline | undefined;
        let turn: gsap.core.Timeline | undefined;
        let reaction: gsap.core.Timeline | undefined;
        let finishReaction: (() => void) | undefined;
        let pointerInPage = false;
        let hovered = false;
        let formFocused = false;
        let lookAtForm = false;
        let resizeFrame = 0;
        let pointerFrame = 0;
        let pointer = { x: 0, y: 0 };
        let centers: Array<{ x: number; y: number }> = [];
        let faceCenter: { x: number; y: number } | undefined;
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
          const slow = hovered || formFocused;
          loops.forEach((loop) => {
            if (!loop) return;
            gsap.killTweensOf(loop);
            if (paused) loop.pause();
            else {
              loop.resume();
              if (smooth)
                gsap.to(loop, {
                  timeScale: slow ? 0.25 : 1,
                  duration: 0.4,
                  ease: "power2.out"
                });
              else loop.timeScale(slow ? 0.25 : 1);
            }
          });
          floats.forEach((float) => (paused ? float.pause() : float.resume()));
          [blinkTimer, blinkAction].forEach((timer) =>
            paused ? timer?.pause() : timer?.resume()
          );
          if (paused || isProtecting.value) {
            waveTimer?.pause();
            waveAction?.pause();
          } else {
            waveTimer?.resume();
            waveAction?.resume();
          }
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
          faceCenter = rig
            ? faceAnchor(rig.getBoundingClientRect())
            : undefined;
        };
        const rebuildLoops = () => {
          if (disposed) return;
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

        const scheduleBlink = () => {
          blinkTimer?.kill();
          blinkTimer = gsap.delayedCall(blinkDelay(Math.random()), () => {
            blinkTimer = undefined;
            const twice = Math.random() < 0.2;
            const setBlink = (closed: boolean) => () => {
              face.blink = closed;
              applyFace();
            };
            blinkAction = gsap
              .timeline({
                onComplete: () => {
                  blinkAction = undefined;
                  if (!disposed) scheduleBlink();
                }
              })
              .add(setBlink(true))
              .add(setBlink(false), 0.12);
            if (twice)
              blinkAction.add(setBlink(true), 0.28).add(setBlink(false), 0.4);
            syncPlayback();
          });
          syncPlayback();
        };
        const scheduleWave = (delay = waveDelay(Math.random())) => {
          waveTimer?.kill();
          waveTimer = gsap.delayedCall(delay, () => {
            waveTimer = undefined;
            startWave();
          });
          syncPlayback();
        };
        const startWave = () => {
          if (disposed || !rig || waveAction) return;
          if (pointerFrozen() || turn || reaction) {
            if (!waveTimer) scheduleWave();
            return;
          }
          waveTimer?.kill();
          waveTimer = undefined;
          waveAction = gsap
            .timeline({
              onComplete: () => {
                waveAction = undefined;
                if (!disposed) scheduleWave();
              }
            })
            .to(act, {
              arm: -18,
              head: 3,
              duration: 0.22,
              ease: "sine.out",
              onUpdate: markDirty
            })
            .to(act, {
              arm: 18,
              duration: 0.26,
              ease: "sine.inOut",
              repeat: 4,
              yoyo: true,
              onUpdate: markDirty
            })
            .to(act, {
              arm: 0,
              head: 0,
              duration: 0.3,
              ease: "sine.inOut",
              onUpdate: markDirty
            });
          syncPlayback();
        };
        const stopWave = () => {
          waveAction?.kill();
          waveAction = undefined;
        };
        // Close the eyes and lean, then swap images at the narrowest point.
        const turnTo = (away: boolean) => {
          turn?.kill();
          stopWave();
          turn = gsap.timeline({
            onComplete: () => {
              turn = undefined;
              if (!away && !disposed && !waveTimer) scheduleWave();
            }
          });
          if (away) {
            turn
              .add(() => {
                face.hold = true;
                applyFace();
              })
              .to(act, {
                head: -5,
                arm: 0,
                duration: 0.12,
                ease: "power2.out",
                onUpdate: markDirty
              });
          }
          if (facingAway.value !== away) {
            turn
              .to(pose, { scaleX: 0.92, duration: 0.11, ease: "power1.in" })
              .add(() => {
                facingAway.value = away;
              })
              .to(pose, { scaleX: 1, duration: 0.11, ease: "power1.out" });
          } else {
            turn.to(pose, { scaleX: 1, duration: 0.1 });
          }
          if (!away) {
            turn
              .add(() => {
                face.hold = false;
                applyFace();
              })
              .to(act, {
                head: 0,
                duration: 0.2,
                ease: "sine.out",
                onUpdate: markDirty
              });
          }
        };
        const endReaction = () => {
          reaction = undefined;
          const finish = finishReaction;
          finishReaction = undefined;
          finish?.();
        };
        const buildReaction = (kind: MascotReaction) => {
          const timeline = gsap.timeline({ onComplete: endReaction });
          if (kind === "error") {
            return timeline
              .add(() => {
                face.mouth = "o";
                face.blink = true;
                applyFace();
              })
              .add(() => {
                face.blink = false;
                applyFace();
              }, 0.12)
              .to(pose, { x: -4, rotation: -2, duration: 0.14 }, 0)
              .to(
                act,
                { head: -6, arm: 0, duration: 0.14, onUpdate: markDirty },
                0
              )
              .to(pose, { x: 0, rotation: 0, duration: 0.24 }, 0.36)
              .to(act, { head: 0, duration: 0.24, onUpdate: markDirty }, 0.36)
              .add(() => {
                face.mouth = "smile";
                applyFace();
              });
          }
          // Crouch, jump with the hair and charm trailing, then land.
          return timeline
            .add(() => {
              face.mouth = "open";
              applyFace();
            })
            .to(pose, { scaleY: 0.95, duration: 0.1, ease: "power2.in" })
            .to(pose, {
              y: -22,
              scaleY: 1.03,
              duration: 0.2,
              ease: "power2.out"
            })
            .to(
              act,
              {
                tail: -8,
                charm: 12,
                arm: 0,
                duration: 0.2,
                ease: "power1.out",
                onUpdate: markDirty
              },
              "<"
            )
            .to(shadow, { opacity: 0.08, scaleX: 0.8, duration: 0.2 }, "<")
            .to(pose, { y: 0, scaleY: 1, duration: 0.18, ease: "power2.in" })
            .to(
              act,
              { tail: 5, charm: -8, duration: 0.18, onUpdate: markDirty },
              "<"
            )
            .to(shadow, { opacity: 0.2, scaleX: 1, duration: 0.18 }, "<")
            .to(pose, { scaleY: 0.98, duration: 0.05 })
            .to(pose, { scaleY: 1, duration: 0.05 })
            .to(
              act,
              { tail: 0, charm: 0, duration: 0.1, onUpdate: markDirty },
              "<"
            );
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
            if (!rig) return;
            const idleLoop = (
              key: keyof typeof idle,
              from: number,
              to: number,
              duration: number
            ) =>
              floats.push(
                gsap.fromTo(
                  idle,
                  { [key]: from },
                  {
                    [key]: to,
                    duration,
                    repeat: -1,
                    yoyo: true,
                    ease: "sine.inOut",
                    paused: true,
                    onUpdate: markDirty
                  }
                )
              );
            idleLoop("breath", 0, 1, 1.6);
            idleLoop("drift", -1, 1, 4.6);
            idleLoop("tail", 2.5, -2.5, 1.9);
            idleLoop("charm", -6, 6, 1.3);
            gsap.ticker.add(renderRig);
            applyFace();
            scheduleBlink();
            // The first visit waves once the entrance has settled.
            scheduleWave(firstEntrance ? 2 : waveDelay(Math.random()));
            reactor = (kind, done) => {
              if (disposed || frozen()) {
                done();
                return;
              }
              reaction?.kill();
              finishReaction?.();
              stopWave();
              finishReaction = done;
              reaction = buildReaction(kind);
            };
          });

        const updatePointer = () => {
          pointerFrame = 0;
          if (disposed || pointerFrozen()) return;
          updateGaze();
          if (!pointerInPage) return;
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
        // Look at the form while typing an account, otherwise at the mouse.
        const updateGaze = () => {
          if (!rig || !faceCenter) return;
          let target: { x: number; y: number } | undefined;
          if (lookAtForm && form) {
            const rect = form.getBoundingClientRect();
            target = {
              x: rect.left + rect.width / 2,
              y: rect.top + rect.height * 0.4
            };
          } else if (pointerInPage) target = pointer;
          const next = target
            ? gazeTarget(target.x - faceCenter.x, target.y - faceCenter.y)
            : { rotation: 0, irisX: 0, irisY: 0 };
          gazeTo.rotation(next.rotation);
          gazeTo.irisX(next.irisX);
          gazeTo.irisY(next.irisY);
        };
        const requestPointerFrame = () => {
          if (!pointerFrame)
            pointerFrame = requestAnimationFrame(updatePointer);
        };
        const pointerMove = (event: PointerEvent) => {
          if (event.pointerType !== "mouse") return;
          pointerInPage = true;
          pointer = { x: event.clientX, y: event.clientY };
          if (pointerFrozen()) return;
          requestPointerFrame();
        };
        const resetPointer = () => {
          pointerInPage = false;
          if (pointerFrozen()) return;
          gsap.to([...aims, ...pupils], {
            rotation: 0,
            x: 0,
            y: 0,
            duration: 0.3,
            overwrite: true
          });
          updateGaze();
        };
        const windowBlur = () => {
          pointerInPage = false;
          if (!pointerFrozen()) updateGaze();
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
        const mascotEnter = (event: PointerEvent) => {
          if (event.pointerType === "mouse") startWave();
        };
        const focusChanged = () => {
          const active = document.activeElement;
          formFocused =
            active instanceof HTMLInputElement && !!panel?.contains(active);
          lookAtForm = formFocused && !isProtecting.value;
          syncPlayback(true);
          if (!pointerFrozen()) requestPointerFrame();
        };
        const privacyChanged = () => {
          if (isProtecting.value) {
            stopPointer();
            gsap.set([...aims, ...pupils], { rotation: 0, x: 0, y: 0 });
            gazeTo.rotation(0);
            gazeTo.irisX(0);
            gazeTo.irisY(0);
          }
          if (allowed && rig && !disposed) turnTo(isProtecting.value);
          else facingAway.value = isProtecting.value;
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
          frame?.addEventListener("pointerenter", mascotEnter);
          window.addEventListener("blur", windowBlur);
          showcase?.addEventListener("pointerenter", pointerEnter);
          showcase?.addEventListener("pointerleave", pointerLeave);
          page.addEventListener("pointermove", pointerMove);
          page.addEventListener("pointerleave", resetPointer);
          panel?.addEventListener("focusin", focusChanged);
          panel?.addEventListener("focusout", focusChanged);
        }
        document.addEventListener("visibilitychange", visibilityChanged);
        facingAway.value = isProtecting.value;
        if (isProtecting.value) privacyChanged();
        visibilityChanged();

        return () => {
          disposed = true;
          reactor = undefined;
          loops.forEach((loop, index) => {
            if (!loop) return;
            session.phases[index] = loop.progress();
            gsap.killTweensOf(loop);
            loop.kill();
          });
          floats.forEach((float) => float.kill());
          [
            blinkTimer,
            blinkAction,
            waveTimer,
            waveAction,
            turn,
            reaction
          ].forEach((animation) => animation?.kill());
          finishReaction?.();
          finishReaction = undefined;
          gsap.ticker.remove(renderRig);
          gsap.killTweensOf([idle, gaze, act]);
          stopPointer();
          facingAway.value = isProtecting.value;
          if (rig) {
            delete rig.dataset.eyes;
            delete rig.dataset.mouth;
          }
          stopPrivacyWatch();
          observer.disconnect();
          cancelAnimationFrame(resizeFrame);
          images.forEach((image) =>
            image.removeEventListener("load", scheduleRebuild)
          );
          frame?.removeEventListener("pointerenter", mascotEnter);
          window.removeEventListener("blur", windowBlur);
          showcase?.removeEventListener("pointerenter", pointerEnter);
          showcase?.removeEventListener("pointerleave", pointerLeave);
          page.removeEventListener("pointermove", pointerMove);
          page.removeEventListener("pointerleave", resetPointer);
          panel?.removeEventListener("focusin", focusChanged);
          panel?.removeEventListener("focusout", focusChanged);
          document.removeEventListener("visibilitychange", visibilityChanged);
          entrance?.kill();
          // Only transforms are cleared; layer origins come from inline styles.
          gsap.set(
            page.querySelectorAll(
              ".poster-track, .partner-float, .partner-aim, .partner-pupils, .mascot-pose, .mascot-rig .rig-group, .mascot-rig img"
            ),
            { clearProps: "transform" }
          );
          gsap.set(page.querySelectorAll(".mascot-shadow"), {
            clearProps: "transform,opacity"
          });
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
  return { canAnimate, isProtecting, facingAway, copyCounts, react };
};
