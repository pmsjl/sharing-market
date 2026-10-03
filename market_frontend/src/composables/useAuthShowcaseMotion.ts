import { onBeforeUnmount, onMounted, ref, watch, type Ref } from "vue";
import { useRouter } from "vue-router";
import gsap from "gsap";

// Retain only presentation state while switching between authentication routes.
const session = { phases: [0, 0], paused: false, entered: false };
const isAuthRoute = (path: string) => path === "/login" || path === "/register";

export const useAuthShowcaseMotion = (root: Ref<HTMLElement | null>) => {
  const router = useRouter();
  const isPaused = ref(session.paused);
  const canAnimate = ref(false);
  let media: ReturnType<typeof gsap.matchMedia> | undefined;

  const togglePaused = () => {
    isPaused.value = !isPaused.value;
    session.paused = isPaused.value;
  };

  onMounted(() => {
    const page = root.value;
    if (!page) return;

    let hasEntered = session.entered;
    session.entered = true;
    media = gsap.matchMedia();
    media.add(
      {
        motion: "(prefers-reduced-motion: no-preference)",
        desktop: "(min-width: 1024px) and (hover: hover) and (pointer: fine)"
      },
      (context) => {
        const allowed = !!context.conditions?.motion;
        canAnimate.value = allowed && !!context.conditions?.desktop;
        const entranceTargets = (
          hasEntered
            ? [".auth-panel-content"]
            : [".showcase-copy", ".poster-entrance", ".auth-panel-content"]
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
        if (!canAnimate.value) {
          let suspended = false;
          const visibilityChanged = () => {
            if (document.hidden && entrance?.isActive()) {
              entrance.pause();
              suspended = true;
            } else if (!document.hidden && suspended) {
              entrance?.resume();
              suspended = false;
            }
          };
          document.addEventListener("visibilitychange", visibilityChanged);
          visibilityChanged();
          return () => {
            document.removeEventListener("visibilitychange", visibilityChanged);
            entrance?.kill();
          };
        }

        const showcase = page.querySelector<HTMLElement>(
          ".auth-market-showcase"
        );
        const panel = page.querySelector<HTMLElement>(".auth-market-panel");
        const rows = Array.from(
          page.querySelectorAll<HTMLElement>(".poster-row")
        );
        const loops: Array<gsap.core.Tween | undefined> = [];
        let hovered = false;
        let disposed = false;
        let resizeFrame = 0;
        let focusFrame = 0;
        let entranceSuspended = false;

        const syncPlayback = (smooth = false) => {
          if (disposed) return;
          const frozen =
            isPaused.value ||
            document.hidden ||
            !!panel?.contains(document.activeElement);
          loops.forEach((loop) => {
            if (!loop) return;
            gsap.killTweensOf(loop);
            if (frozen) {
              loop.pause();
            } else {
              loop.resume();
              if (smooth) {
                gsap.to(loop, {
                  timeScale: hovered ? 0.25 : 1,
                  duration: 0.4,
                  ease: "power2.out"
                });
              } else loop.timeScale(hovered ? 0.25 : 1);
            }
          });
        };

        const rebuildLoops = () => {
          if (disposed) return;
          rows.forEach((row, index) => {
            const track = row.querySelector<HTMLElement>(".poster-track");
            const group = row.querySelector<HTMLElement>(".poster-group");
            if (!track || !group) return;
            const distance = group.offsetWidth;
            if (!distance) return;
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
        const images = Array.from(page.querySelectorAll(".poster-tile img"));
        images.forEach((image) =>
          image.addEventListener("load", scheduleRebuild)
        );
        // Own every loop explicitly so media reversion cannot rewind its phase
        // before cleanup saves it, including the first synchronous build.
        context.ignore(rebuildLoops);

        const pointerEnter = (event: PointerEvent) => {
          if (event.pointerType !== "mouse") return;
          hovered = true;
          syncPlayback(true);
        };
        const pointerLeave = () => {
          hovered = false;
          syncPlayback(true);
        };
        const focusIn = () => syncPlayback();
        const focusOut = () => {
          cancelAnimationFrame(focusFrame);
          focusFrame = requestAnimationFrame(() => syncPlayback());
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
        const stopWatch = watch(isPaused, () => syncPlayback());
        showcase?.addEventListener("pointerenter", pointerEnter);
        showcase?.addEventListener("pointerleave", pointerLeave);
        panel?.addEventListener("focusin", focusIn);
        panel?.addEventListener("focusout", focusOut);
        document.addEventListener("visibilitychange", visibilityChanged);
        visibilityChanged();

        return () => {
          disposed = true;
          loops.forEach((loop, index) => {
            if (!loop) return;
            session.phases[index] = loop.progress();
            gsap.killTweensOf(loop);
            loop.kill();
          });
          stopWatch();
          observer.disconnect();
          cancelAnimationFrame(resizeFrame);
          cancelAnimationFrame(focusFrame);
          images.forEach((image) =>
            image.removeEventListener("load", scheduleRebuild)
          );
          showcase?.removeEventListener("pointerenter", pointerEnter);
          showcase?.removeEventListener("pointerleave", pointerLeave);
          panel?.removeEventListener("focusin", focusIn);
          panel?.removeEventListener("focusout", focusOut);
          document.removeEventListener("visibilitychange", visibilityChanged);
          entrance?.kill();
          gsap.set(page.querySelectorAll(".poster-track"), {
            clearProps: "transform"
          });
        };
      },
      page
    );
  });

  onBeforeUnmount(() => {
    media?.revert();
    if (!isAuthRoute(router.currentRoute.value.path)) {
      session.phases = [0, 0];
      session.paused = false;
      session.entered = false;
    }
  });

  return { canAnimate, isPaused, togglePaused };
};
