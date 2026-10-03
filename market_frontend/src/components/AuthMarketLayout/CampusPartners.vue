<template>
  <div
    class="campus-partners"
    :class="{ 'is-protecting': protecting }"
    aria-hidden="true"
  >
    <div class="partners-entrance">
      <div
        v-for="partner in partners"
        :key="partner.id"
        class="campus-partner"
        :class="'partner-' + partner.id"
      >
        <div class="partner-shadow"></div>
        <div class="partner-float">
          <div class="partner-aim">
            <img
              v-if="!failed[partner.id]"
              class="partner-body"
              :src="'/generated/auth-partners/' + partner.id + '.webp'"
              alt=""
              :width="partner.width"
              :height="partner.height"
              decoding="async"
              @error="failed[partner.id] = true"
            />
            <svg
              v-else
              class="partner-body partner-fallback"
              viewBox="0 0 200 260"
              preserveAspectRatio="none"
            >
              <defs>
                <linearGradient
                  :id="'partner-shade-' + partner.id"
                  x1="0"
                  y1="0"
                  x2="1"
                  y2="1"
                >
                  <stop offset="0" stop-color="var(--partner-highlight)" />
                  <stop offset="0.48" stop-color="var(--partner-color)" />
                  <stop offset="1" stop-color="var(--partner-edge)" />
                </linearGradient>
              </defs>
              <path
                :d="partner.shape"
                :fill="'url(#partner-shade-' + partner.id + ')'"
              />
            </svg>
            <div class="partner-face">
              <svg class="partner-eyes" viewBox="0 0 86 54">
                <g class="partner-open-eyes">
                  <ellipse cx="22" cy="27" rx="15" ry="22" fill="#fffdf7" />
                  <ellipse cx="63" cy="27" rx="15" ry="22" fill="#fffdf7" />
                  <g class="partner-pupils" fill="#17243b">
                    <ellipse cx="25" cy="27" rx="6.5" ry="12" />
                    <ellipse cx="66" cy="27" rx="6.5" ry="12" />
                  </g>
                </g>
                <g
                  class="partner-closed-eyes"
                  fill="none"
                  stroke="#17243b"
                  stroke-width="4"
                  stroke-linecap="round"
                >
                  <path d="M10 26q12 11 24 0 M51 26q12 11 24 0" />
                </g>
              </svg>
            </div>
          </div>
        </div>
      </div>
      <div class="campus-mascot" data-action="rest">
        <div class="mascot-shadow"></div>
        <div class="mascot-travel">
          <div class="mascot-pose">
            <img
              v-if="!failed.mascot"
              class="mascot-image mascot-front"
              src="/generated/auth-partners/mascot.webp"
              alt=""
              width="315"
              height="640"
              decoding="async"
              @error="failed.mascot = true"
            />
            <img
              v-if="!failed.mascotAway"
              class="mascot-image mascot-away"
              src="/generated/auth-partners/mascot-away.webp"
              alt=""
              width="315"
              height="640"
              decoding="async"
              @error="failed.mascotAway = true"
            />
            <div class="mascot-run" :style="spriteStyle('run')"></div>
            <div class="mascot-walk" :style="spriteStyle('walk')"></div>
            <img
              v-if="failed.mascot || (protecting && failed.mascotAway)"
              class="mascot-logo-fallback"
              :src="setting.logo"
              alt=""
              width="160"
              height="160"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import setting from "@/setting";
import { spriteStyle } from "@/utils/authMascotMotion";
defineProps<{ protecting: boolean }>();
const failed = reactive<Record<string, boolean>>({});
const partners = [
  {
    id: "blue",
    width: 403,
    height: 640,
    shape:
      "M40 4H160Q196 4 196 44V216Q196 256 160 256H40Q4 256 4 216V44Q4 4 40 4Z"
  },
  {
    id: "yellow",
    width: 640,
    height: 335,
    shape: "M4 252V229C4 96 47 6 100 6S196 96 196 229V252Z"
  },
  {
    id: "sky",
    width: 520,
    height: 640,
    shape:
      "M44 5H156Q195 5 195 47V214Q195 255 156 255H44Q5 255 5 214V47Q5 5 44 5Z"
  },
  {
    id: "ink",
    width: 640,
    height: 368,
    shape: "M100 8C160 8 196 60 196 137S164 253 100 253 4 214 4 137 40 8 100 8Z"
  }
];
</script>

<style scoped lang="scss">
.campus-partners {
  position: absolute;
  z-index: 3;
  width: min(81%, 740px);
  bottom: 0;
  left: 50%;
  height: 250px;
  transform: translateX(-50%);
  pointer-events: none;
}
.partners-entrance {
  position: relative;
  width: 100%;
  height: 100%;
}
.campus-partner {
  position: absolute;
  bottom: 0;
}
.partner-float,
.partner-aim {
  width: 100%;
  height: 100%;
  transform-origin: 50% 94%;
}
.partner-body {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
}
.partner-body:not(.partner-fallback) {
  filter: hue-rotate(var(--partner-hue, 0deg))
    brightness(var(--partner-brightness, 1));
}
.partner-face {
  position: absolute;
  top: 28%;
  left: 38%;
  width: 45%;
  transition: transform 220ms ease-out;
}
.partner-eyes {
  display: block;
  width: 100%;
  overflow: visible;
}
.partner-closed-eyes {
  display: none;
}
.partner-shadow {
  position: absolute;
  right: -8%;
  bottom: -5%;
  left: -8%;
  height: 11%;
  border-radius: 50%;
  background: var(--market-primary);
  opacity: 0.18;
  filter: blur(10px);
}
.partner-blue {
  --partner-color: var(--market-primary);
  --partner-highlight: var(--market-primary-soft);
  --partner-edge: var(--market-primary-hover);
  z-index: 1;
  left: 0;
  width: 27%;
  height: 96%;
  transform: rotate(-6deg);
}
.partner-yellow {
  --partner-color: var(--market-yellow);
  --partner-highlight: var(--market-yellow-soft);
  --partner-edge: #d9a72e;
  z-index: 2;
  bottom: -2%;
  left: 19%;
  width: 36%;
  height: 62%;
  .partner-body {
    --partner-hue: 0deg;
  }
  .partner-face {
    top: 30%;
    left: 25%;
    width: 32%;
  }
}
.partner-sky {
  --partner-color: var(--market-primary-soft);
  --partner-highlight: var(--market-surface);
  --partner-edge: var(--market-primary);
  z-index: 1;
  right: 0;
  width: 28%;
  height: 72%;
  transform: rotate(7deg);
  .partner-face {
    top: 25%;
    left: 23%;
    width: 49%;
  }
}
.partner-ink {
  --partner-color: #27364f;
  --partner-highlight: #53627a;
  --partner-edge: #17243b;
  z-index: 4;
  right: 17%;
  bottom: -5%;
  width: 25%;
  height: 40%;
  .partner-body {
    --partner-hue: 0deg;
  }
  .partner-face {
    top: 24%;
    left: 29%;
    width: 43%;
  }
}
.campus-mascot {
  position: absolute;
  z-index: 5;
  bottom: -9%;
  left: 44%;
  width: 23%;
  height: 88%;
  pointer-events: none;
}
.mascot-travel,
.mascot-pose {
  position: relative;
  width: 100%;
  height: 100%;
  transform-origin: 50% 95%;
}
// The unrotated wrapper keeps hover stable while its child sways.
@media (min-width: 1024px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .mascot-travel {
    pointer-events: auto;
  }
}
.mascot-pose {
  pointer-events: none;
}
.mascot-image {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  filter: drop-shadow(0 6px 5px rgba(25, 48, 84, 0.1));
}
.mascot-away {
  visibility: hidden;
}
.mascot-run,
.mascot-walk {
  position: absolute;
  bottom: 0;
  left: 50%;
  visibility: hidden;
  background-repeat: no-repeat;
  pointer-events: none;
}
.mascot-run.is-active,
.mascot-walk.is-active {
  visibility: visible;
}
.mascot-run.is-left,
.mascot-walk.is-left {
  transform: scaleX(-1);
}
.campus-mascot.is-moving .mascot-front {
  visibility: hidden;
}
.mascot-shadow {
  position: absolute;
  right: 12%;
  bottom: -2%;
  left: 12%;
  height: 7%;
  border-radius: 50%;
  background: var(--market-primary);
  opacity: 0.2;
  filter: blur(7px);
}
.mascot-logo-fallback {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 100%;
  height: auto;
}
.is-protecting {
  .mascot-front {
    visibility: hidden;
  }
  .mascot-away {
    visibility: visible;
  }
}
.is-protecting {
  .partner-open-eyes {
    display: none;
  }
  .partner-closed-eyes {
    display: block;
  }
  .partner-face {
    transform: translateX(-8px) rotate(-8deg);
  }
}
:global(html[data-accent="indigo"]) .partner-blue,
:global(html[data-accent="indigo"]) .partner-sky {
  --partner-hue: 13deg;
}
:global(html[data-accent="lake-blue"]) .partner-blue,
:global(html[data-accent="lake-blue"]) .partner-sky {
  --partner-hue: -20deg;
}
:global(html.dark) .campus-partners,
:global(html[data-theme="night"]) .campus-partners {
  --partner-brightness: 0.82;
}
:global(.auth-motion-static) .partner-face {
  transition: none;
}
@media (min-width: 1024px) and (max-width: 1199px) {
  .campus-partners {
    width: 90%;
    height: 250px;
  }
}
@media (max-width: 1023px) {
  .campus-partners {
    width: 75%;
    bottom: 12px;
    left: 47.5%;
    height: 96px;
  }
  .partner-shadow {
    filter: blur(5px);
  }
  .partner-face {
    transition: none;
  }
  .is-protecting .partner-face {
    transform: translateX(-3px) rotate(-8deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .partner-face {
    transition: none;
  }
}
</style>
