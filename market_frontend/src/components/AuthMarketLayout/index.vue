<template>
  <div
    ref="layoutRoot"
    class="auth-market-page"
    :class="{ 'auth-motion-static': !canAnimate }"
  >
    <div class="campus-landscape" aria-hidden="true">
      <svg
        class="campus-buildings"
        viewBox="0 0 1440 240"
        preserveAspectRatio="none"
      >
        <g fill="currentColor">
          <path
            d="M0 95 58 70 118 95v145H0Z M128 151h90v89h-90Z M245 184h160v56H245Z M1160 170h105v70h-105Z M1312 100l60-30 68 30v140h-128Z"
          />
          <path d="M42 48h6v45h-6Z M1338 48h6v49h-6Z M980 208h140v32H980Z" />
        </g>
        <g fill="var(--auth-paper)">
          <path
            d="M16 112h14v20H16Z M47 112h14v20H47Z M78 112h14v20H78Z M16 153h14v20H16Z M47 153h14v20H47Z M78 153h14v20H78Z M148 171h15v22h-15Z M182 171h15v22h-15Z M1330 116h15v22h-15Z M1364 116h15v22h-15Z M1400 116h15v22h-15Z M1330 157h15v22h-15Z M1364 157h15v22h-15Z M1400 157h15v22h-15Z"
          />
        </g>
      </svg>
      <div class="campus-ground"></div>
      <div class="campus-ground-line"></div>
    </div>
    <header class="auth-market-header">
      <div class="auth-market-brand">
        <img :src="setting.logo" alt="校园集市标识" width="48" height="48" />
        <div>
          <strong>{{ setting.title }}</strong
          ><span>SHARING MARKET</span>
        </div>
      </div>
    </header>

    <section class="auth-market-shell">
      <main class="auth-market-panel" aria-label="账号通行证">
        <div class="auth-panel-content">
          <slot></slot>
        </div>
      </main>
      <aside class="auth-market-showcase" aria-labelledby="showcase-title">
        <div class="showcase-copy">
          <h1 id="showcase-title">
            你的下一件喜欢，<br /><span>正在校园里。</span>
          </h1>
        </div>
        <div class="campus-scene">
          <div class="poster-stage" aria-label="校园生活灵感橱窗">
            <div class="poster-entrance">
              <div class="poster-tilt">
                <div
                  v-for="(row, rowIndex) in posterRows"
                  :key="rowIndex"
                  class="poster-row"
                  :class="{ 'poster-row-reverse': rowIndex === 1 }"
                  :data-speed="rowIndex === 0 ? 10 : 8"
                  :data-reverse="rowIndex === 1"
                >
                  <div class="poster-track">
                    <div
                      v-for="copy in copyCounts[rowIndex]"
                      :key="copy"
                      class="poster-group"
                      :aria-hidden="copy > 1 ? 'true' : undefined"
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
                          <span class="poster-object-label">{{
                            tile.name
                          }}</span>
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
          <CampusPartners :protecting="isProtecting" />
          <span class="scene-spark scene-spark-blue" aria-hidden="true">✦</span>
          <span class="scene-spark scene-spark-yellow" aria-hidden="true"
            >✦</span
          >
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
  Basketball
} from "@element-plus/icons-vue";
import setting from "@/setting";
import CampusPartners from "./CampusPartners.vue";
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
const { canAnimate, isProtecting, copyCounts } =
  useAuthShowcaseMotion(layoutRoot);
</script>

<style scoped lang="scss" src="./scene.scss"></style>

<style scoped lang="scss">
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
@media (max-width: 480px) {
  :deep(.auth-form-heading h2) {
    font-size: 29px;
  }
}
</style>
