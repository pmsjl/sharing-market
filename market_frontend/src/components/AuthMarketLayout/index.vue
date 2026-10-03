<template>
  <div ref="layoutRoot" class="auth-market-page">
    <header class="auth-market-header">
      <div class="auth-market-brand">
        <img :src="setting.logo" alt="校园集市标识" width="48" height="48" />
        <div>
          <strong>{{ setting.title }}</strong
          ><span>SHARING MARKET</span>
        </div>
      </div>
      <span class="auth-header-note"
        ><i aria-hidden="true"></i>就在同学之间</span
      >
    </header>

    <section class="auth-market-shell">
      <main class="auth-market-panel" aria-label="账号通行证">
        <div class="auth-panel-content">
          <slot></slot>
          <div class="auth-panel-footer">
            <span class="auth-footer-mark" aria-hidden="true">SM /</span>
            <span>把闲置分享，让喜欢延续。</span>
          </div>
        </div>
      </main>
      <aside class="auth-market-showcase" aria-labelledby="showcase-title">
        <div class="showcase-copy">
          <span class="showcase-eyebrow"
            ><span aria-hidden="true">01 /</span> 校园生活，循环上新</span
          >
          <h1 id="showcase-title">
            好物换个主人，<br /><span>喜欢继续发生。</span>
          </h1>
          <p>分享闲置，也遇见新的喜欢。</p>
        </div>
        <div class="poster-stage" aria-label="校园生活灵感海报">
          <div class="poster-entrance">
            <div class="poster-tilt">
              <div
                v-for="(row, rowIndex) in posterRows"
                :key="rowIndex"
                class="poster-row"
                :class="{ 'poster-row-reverse': rowIndex === 1 }"
                :data-speed="rowIndex === 0 ? 12 : 9"
                :data-reverse="rowIndex === 1"
              >
                <div class="poster-track">
                  <div
                    v-for="copy in 2"
                    :key="copy"
                    class="poster-group"
                    :aria-hidden="copy === 2 ? 'true' : undefined"
                  >
                    <article
                      v-for="tile in row"
                      :key="tile.id"
                      class="poster-tile"
                      :class="[
                        'poster-tile-' + tile.kind,
                        'poster-tile-' + tile.tone,
                        'poster-tile-' + tile.id
                      ]"
                    >
                      <template v-if="tile.kind === 'photo'">
                        <img
                          v-if="!failedImages[tile.id]"
                          :src="tile.image"
                          :alt="tile.name"
                          width="800"
                          height="600"
                          decoding="async"
                          @error="failedImages[tile.id] = true"
                        />
                        <div v-else class="poster-image-fallback">
                          <el-icon aria-hidden="true"
                            ><component :is="tile.icon"
                          /></el-icon>
                          <span>{{ tile.name }}</span>
                        </div>
                        <span class="poster-photo-label" aria-hidden="true">{{
                          tile.name
                        }}</span>
                      </template>
                      <template v-else-if="tile.kind === 'text'">
                        <span class="poster-tile-kicker">{{
                          tile.caption
                        }}</span>
                        <strong>{{ tile.name }}</strong>
                        <span class="poster-tile-sign" aria-hidden="true"
                          >↗</span
                        >
                      </template>
                      <template v-else>
                        <el-icon class="poster-object" aria-hidden="true"
                          ><component :is="tile.icon"
                        /></el-icon>
                        <span class="poster-object-label">{{ tile.name }}</span>
                        <span
                          class="poster-object-dot"
                          aria-hidden="true"
                        ></span>
                      </template>
                    </article>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="showcase-bottom">
          <span><i aria-hidden="true">↗</i> 好物灵感 · 让闲置有下一站</span>
          <button
            v-if="canAnimate"
            type="button"
            class="poster-motion-toggle"
            :aria-pressed="isPaused"
            @click="togglePaused"
          >
            <el-icon aria-hidden="true"
              ><VideoPlay v-if="isPaused" /><VideoPause v-else
            /></el-icon>
            {{ isPaused ? "播放动效" : "暂停动效" }}
          </button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import {
  Headset,
  Notebook,
  ShoppingBag,
  VideoPause,
  VideoPlay,
  Basketball
} from "@element-plus/icons-vue";
import setting from "@/setting";
import { useAuthShowcaseMotion } from "@/composables/useAuthShowcaseMotion";

const posterRows = [
  [
    {
      id: "shoes",
      kind: "photo",
      tone: "paper",
      name: "耐克跑步鞋",
      icon: Basketball,
      image:
        "https://img.pmsjl.com/2026/05/c654905adae77bb51e5727e62c44a46a.avif"
    },
    {
      id: "reading",
      kind: "text",
      tone: "blue",
      name: "书里\n有答案",
      caption: "A NEW CHAPTER"
    },
    {
      id: "headphones",
      kind: "photo",
      tone: "paper",
      name: "索尼降噪耳机",
      icon: Headset,
      image:
        "https://img.pmsjl.com/2026/05/49ea38ade3208fc869bb4822526330a2.png"
    },
    {
      id: "book-icon",
      kind: "graphic",
      tone: "soft",
      name: "翻开下一页",
      icon: Notebook
    }
  ],
  [
    {
      id: "sports",
      kind: "text",
      tone: "yellow",
      name: "运动\n一下",
      caption: "MOVE & REPEAT"
    },
    {
      id: "book",
      kind: "photo",
      tone: "paper",
      name: "瓦尔登湖",
      icon: Notebook,
      image:
        "https://pmsjl-01.oss-cn-shenzhen.aliyuncs.com/commodity_avatar/2058864659104120834/T3dWoD8W-MTYxMzk4NjA3MDI0Nl_kuIrlnJYuanBn.jpg"
    },
    {
      id: "bag-icon",
      kind: "graphic",
      tone: "blue",
      name: "装下新喜欢",
      icon: ShoppingBag
    },
    {
      id: "digital",
      kind: "text",
      tone: "paper",
      name: "数码\n新搭子",
      caption: "FIND YOUR MATCH"
    }
  ]
];
const layoutRoot = ref<HTMLElement | null>(null);
const failedImages = reactive<Record<string, boolean>>({});
const { canAnimate, isPaused, togglePaused } =
  useAuthShowcaseMotion(layoutRoot);
</script>

<style scoped lang="scss">
.auth-market-page {
  --auth-paper: var(--market-canvas);
  --auth-yellow: var(--market-yellow-soft);
  --auth-yellow-ink: var(--market-ink);
  display: flex;
  min-height: 100dvh;
  padding: 28px clamp(24px, 4vw, 64px) 32px;
  flex-direction: column;
  color: var(--market-ink);
  background: var(--auth-paper);
}
html:not(.dark):not([data-theme="night"]) .auth-market-page {
  --auth-paper: #faf9f6;
  --auth-yellow: #ffe58b;
  --auth-yellow-ink: #253348;
  --market-line: #e5e5df;
  --market-line-strong: #c8cbc9;
  --market-ink: #202b3d;
  --market-muted: #626b78;
}
.auth-market-header {
  display: flex;
  width: min(1280px, 100%);
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.auth-market-brand {
  display: flex;
  align-items: center;
  gap: var(--market-logo-gap);
  img {
    width: var(--market-logo-size);
    height: var(--market-logo-size);
    object-fit: contain;
  }
  div {
    display: grid;
    gap: 3px;
  }
  strong {
    font-size: 18px;
    font-weight: 750;
    line-height: 1.3;
  }
  span {
    color: var(--market-muted);
    font-size: 10px;
    letter-spacing: 0.16em;
  }
}
.auth-header-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--market-muted);
  font-size: 12px;
  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--market-primary);
  }
}
.auth-market-shell {
  display: grid;
  grid-template-areas: "showcase panel";
  grid-template-columns: minmax(0, 62fr) minmax(0, 38fr);
  width: min(1280px, 100%);
  margin: auto;
  padding-block: 32px 12px;
  align-items: center;
  gap: 24px;
}
.auth-market-panel {
  grid-area: panel;
  min-width: 0;
  padding: 32px;
  border: 1px solid var(--market-line);
  border-radius: 24px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}
.auth-panel-content {
  width: min(380px, 100%);
  margin: 0 auto;
}
.auth-panel-footer {
  display: flex;
  margin-top: 22px;
  padding-top: 18px;
  align-items: center;
  gap: 12px;
  border-top: 1px solid var(--market-line);
  color: var(--market-muted);
  font-size: 12px;
}
.auth-footer-mark {
  color: var(--market-primary);
  font-weight: 800;
  letter-spacing: -1px;
}
.auth-market-showcase {
  grid-area: showcase;
  min-width: 0;
}
.showcase-copy {
  padding: 12px 24px 0 8px;
}
.showcase-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--market-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  span {
    color: var(--market-primary);
    font-family: var(--market-font-mono);
    font-weight: 700;
  }
}
.showcase-copy h1 {
  margin: 18px 0 14px;
  font-size: clamp(40px, 4vw, 56px);
  font-weight: 800;
  letter-spacing: -0.05em;
  line-height: 1.22;
  span {
    position: relative;
    z-index: 0;
    color: var(--market-primary);
    &::after {
      position: absolute;
      z-index: -1;
      right: 0;
      bottom: 0.06em;
      left: 0;
      height: 0.15em;
      border-radius: 2px;
      background: var(--auth-yellow);
      content: "";
      transform: rotate(-1.2deg);
    }
  }
}
.showcase-copy p {
  margin: 0;
  color: var(--market-muted);
  font-size: 14px;
  line-height: 1.7;
}
.poster-stage {
  position: relative;
  height: 360px;
  margin-top: 12px;
  overflow: hidden;
  isolation: isolate;
  // Clip the mural locally so it never crosses into the form.
  &::after {
    position: absolute;
    z-index: 2;
    inset: 0;
    background: linear-gradient(
      90deg,
      var(--auth-paper),
      transparent 5%,
      transparent 87%,
      var(--auth-paper)
    );
    content: "";
    pointer-events: none;
  }
}
.poster-entrance {
  position: absolute;
  inset: 0;
}
.poster-tilt {
  position: absolute;
  top: 10px;
  left: -30px;
  width: calc(100% + 160px);
  transform: rotate(-6deg);
  transform-origin: 50% 50%;
}
.poster-row {
  height: 148px;
}
.poster-row-reverse {
  position: relative;
  left: 30px;
  margin-top: 16px;
}
.poster-track,
.poster-group {
  display: flex;
  width: max-content;
}
.poster-group {
  padding-right: 16px;
  flex: 0 0 auto;
  gap: 16px;
}
.poster-tile {
  position: relative;
  width: 204px;
  height: 148px;
  flex: 0 0 auto;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 16px;
  color: var(--market-ink);
  background: var(--market-surface);
  transition: transform 240ms ease-out, box-shadow 240ms ease-out;
}
.poster-tile-blue {
  border-color: transparent;
  color: var(--market-on-primary);
  background: var(--market-primary);
}
.poster-tile-yellow {
  border-color: transparent;
  color: var(--auth-yellow-ink);
  background: var(--auth-yellow);
}
.poster-tile-soft {
  border-color: transparent;
  color: var(--market-primary);
  background: var(--market-primary-soft);
}
.poster-tile-photo {
  width: 240px;
  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}
.poster-tile-shoes {
  width: 272px;
}
.poster-tile-book {
  width: 224px;
}
.poster-photo-label {
  position: absolute;
  bottom: 9px;
  left: 12px;
  padding: 4px 8px;
  border: 1px solid var(--market-line);
  border-radius: 6px;
  color: var(--market-ink);
  font-size: 10px;
  background: var(--market-surface);
}
.poster-tile-text {
  display: flex;
  padding: 18px 20px;
  flex-direction: column;
  justify-content: space-between;
  strong {
    font-family: "Smiley Sans", var(--market-font-body);
    font-size: 36px;
    font-weight: 400;
    letter-spacing: 0.02em;
    line-height: 1.13;
    white-space: pre-line;
  }
}
.poster-tile-digital {
  width: 224px;
}
.poster-tile-kicker {
  font-family: var(--market-font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
}
.poster-tile-sign {
  position: absolute;
  right: 17px;
  bottom: 17px;
  font-size: 31px;
  line-height: 1;
}
.poster-tile-graphic {
  display: flex;
  width: 176px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
}
.poster-object {
  font-size: 74px;
  transform: rotate(12deg);
}
.poster-object-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
}
.poster-object-dot {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--auth-yellow);
}
.poster-image-fallback {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  color: var(--market-primary);
  background: var(--market-primary-soft);
  .el-icon {
    font-size: 56px;
  }
  span {
    font-size: 12px;
  }
}
.showcase-bottom {
  display: flex;
  min-height: 44px;
  padding: 0 12px 0 8px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--market-muted);
  font-size: 11px;
  i {
    margin-right: 6px;
    color: var(--market-primary);
    font-size: 17px;
    font-style: normal;
  }
}
.poster-motion-toggle {
  display: inline-flex;
  min-height: 44px;
  padding: 0 8px;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  color: var(--market-muted);
  font: inherit;
  background: transparent;
  cursor: pointer;
  &:hover {
    color: var(--market-primary);
  }
  &:focus-visible {
    outline: 2px solid var(--market-primary);
    outline-offset: 2px;
  }
}
:deep(.auth-entry-form) {
  width: 100%;
}
:deep(.auth-form-kicker) {
  display: inline-block;
  margin-bottom: 12px;
  color: var(--market-primary);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.08em;
}
:deep(.auth-form-heading) {
  margin-bottom: 26px;
}
:deep(.auth-form-heading h2) {
  margin: 0;
  color: var(--market-ink);
  font-size: 30px;
  font-weight: 750;
  letter-spacing: -1px;
  line-height: 1.3;
}
:deep(.auth-form-heading p) {
  margin: 9px 0 0;
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.8;
}
:deep(.auth-entry-form .el-form-item) {
  margin-bottom: 24px;
}
:deep(.auth-entry-form .el-form-item__label) {
  padding-bottom: 8px;
  color: var(--market-ink);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}
:deep(.auth-entry-form .el-input__wrapper) {
  min-height: 48px;
  padding: 1px 14px;
  border-radius: 10px;
  background: var(--auth-paper);
  box-shadow: 0 0 0 1px var(--market-line) inset;
  transition: box-shadow 200ms ease-out;
}
:deep(.auth-entry-form .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--market-line-strong) inset;
}
:deep(.auth-entry-form .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--market-primary) inset, var(--market-focus);
}
:deep(.auth-entry-form .el-input__inner) {
  min-width: 0;
  font-size: 16px;
}
:deep(.auth-submit) {
  width: 100%;
  min-height: 48px;
  margin-top: 3px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 650;
}
:deep(.auth-switch) {
  margin: 12px 0 0;
  color: var(--market-muted);
  font-size: 13px;
  text-align: center;
}
:deep(.auth-switch-link) {
  min-height: 44px;
  padding: 0 6px;
  border: 0;
  border-radius: 4px;
  color: var(--market-primary);
  font: inherit;
  font-weight: 600;
  background: transparent;
  cursor: pointer;
  &:focus-visible {
    outline: 2px solid var(--market-primary);
    outline-offset: 2px;
  }
}
:deep(.auth-switch-link:hover) {
  color: var(--market-primary-hover);
  text-decoration: underline;
  text-underline-offset: 4px;
}
:deep(.auth-switch-link:disabled) {
  opacity: 0.45;
  cursor: not-allowed;
}
:deep(.auth-security) {
  margin-top: 8px;
  color: var(--market-muted);
  font-size: 12px;
  text-align: center;
}
@media (min-width: 1024px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .poster-tile:hover {
    transform: translateY(-6px) scale(1.025);
    box-shadow: var(--market-shadow-lift);
  }
}
@media (min-width: 1024px) and (max-width: 1199px) {
  .auth-market-page {
    padding-inline: 24px;
  }
  .auth-market-panel {
    padding: 28px 22px;
  }
  :deep(.auth-form-heading h2) {
    font-size: 28px;
  }
}
@media (max-width: 1023px) {
  .auth-market-page {
    padding: 20px 24px 24px;
  }
  .auth-market-shell {
    grid-template-areas: "panel" "showcase";
    grid-template-columns: minmax(0, 1fr);
    width: min(560px, 100%);
    margin: 0 auto;
    padding-top: 24px;
    gap: 24px;
  }
  .auth-market-panel {
    padding: 28px 32px;
  }
  .auth-market-showcase {
    position: relative;
    height: 220px;
    overflow: hidden;
    border-radius: 18px;
    background: var(--market-primary-soft);
  }
  .showcase-copy {
    position: relative;
    z-index: 3;
    padding: 18px 20px 0;
  }
  .showcase-eyebrow,
  .showcase-copy p,
  .showcase-bottom {
    display: none;
  }
  .showcase-copy h1 {
    margin: 0;
    font-size: 22px;
    line-height: 1.2;
  }
  .poster-stage {
    height: 152px;
    margin-top: 0;
    &::after {
      background: linear-gradient(
        90deg,
        var(--market-primary-soft),
        transparent 4%,
        transparent 92%,
        var(--market-primary-soft)
      );
    }
  }
  .poster-tilt {
    top: 12px;
    left: -20px;
    width: 100%;
  }
  .poster-row,
  .poster-tile {
    height: 60px;
  }
  .poster-group {
    padding-right: 8px;
    gap: 8px;
  }
  .poster-row-reverse {
    left: 24px;
    margin-top: 8px;
  }
  .poster-tile {
    width: 100px;
    border-radius: 10px;
  }
  .poster-tile-photo {
    width: 112px;
  }
  .poster-tile-graphic {
    width: 92px;
    gap: 4px;
  }
  .poster-tile-text {
    padding: 7px 10px;
    strong {
      font-size: 19px;
    }
  }
  .poster-tile-kicker {
    display: none;
  }
  .poster-tile-sign {
    right: 9px;
    bottom: 8px;
    font-size: 18px;
  }
  .poster-photo-label {
    bottom: 4px;
    left: 5px;
    padding: 2px 4px;
    font-size: 7px;
  }
  .poster-object {
    font-size: 26px;
  }
  .poster-object-label {
    font-size: 7px;
  }
  .poster-object-dot {
    top: 8px;
    right: 8px;
    width: 5px;
    height: 5px;
  }
  .poster-image-fallback {
    gap: 4px;
    .el-icon {
      font-size: 28px;
    }
    span {
      font-size: 8px;
    }
  }
}
@media (max-width: 480px) {
  .auth-market-page {
    padding: 18px 16px 24px;
  }
  .auth-header-note {
    display: none;
  }
  .auth-market-shell {
    padding-top: 22px;
    gap: 18px;
  }
  .auth-market-panel {
    padding: 24px 20px;
    border-radius: 18px;
  }
  :deep(.auth-form-heading h2) {
    font-size: 27px;
  }
}
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition: none !important;
  }
}
</style>
