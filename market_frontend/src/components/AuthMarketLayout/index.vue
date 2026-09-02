<template>
  <div class="auth-market-page" ref="layoutRoot">
    <header class="auth-market-header">
      <div class="auth-market-brand">
        <img :src="setting.logo" alt="校园二手交易平台标识" />
        <div>
          <strong>{{ setting.title }}</strong>
          <span>Campus Market</span>
        </div>
      </div>
    </header>

    <section class="auth-market-shell">
      <main class="auth-market-panel" aria-label="账号通行证">
        <slot></slot>
      </main>

      <aside class="auth-market-showcase" aria-labelledby="showcase-title">
        <div class="showcase-copy">
          <span class="market-eyebrow">今日市集开放中</span>
          <h1 id="showcase-title">校园好物橱窗，总有一件正合适</h1>
          <p>登录后解锁发布、收藏、私信和个性化推荐。</p>
        </div>

        <div class="showcase-grid">
          <article
            v-for="(item, index) in showcaseItems"
            :key="item.id"
            class="showcase-card"
            :class="{ 'is-featured': index === 0 }"
          >
            <div class="showcase-media">
              <img
                v-if="!failedImages[item.id]"
                :src="item.image"
                :alt="item.name"
                width="800"
                height="600"
                loading="eager"
                @error="markImageFailed(item.id)"
              />
              <div
                v-else
                class="image-fallback"
                role="img"
                :aria-label="item.name"
              >
                <span aria-hidden="true">{{ item.name.slice(0, 1) }}</span>
                <small>图片暂不可用</small>
              </div>
              <span class="condition-badge">{{ item.degree }}</span>
            </div>

            <div class="showcase-card-copy">
              <div class="showcase-meta">
                <span>{{ formatDate(item.createTime) }} 上架</span>
                <strong>{{ formatPrice(item.price) }}</strong>
              </div>
              <h2>{{ item.name }}</h2>
              <p>{{ item.description }}</p>
              <span class="inventory">库存 {{ item.inventory }} 件</span>
            </div>
          </article>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import setting from "@/setting";
import { animateIn } from "@/utils/motion";

interface ShowcaseItem {
  id: string;
  name: string;
  description: string;
  image: string;
  degree: string;
  price: number;
  inventory: number;
  createTime: string;
}

const showcaseItems: ShowcaseItem[] = [
  {
    id: "2060023541910327297",
    name: "耐克 Air Zoom 跑步鞋",
    description: "专业缓震跑步鞋，透气网面设计，适合日常训练和长跑使用。",
    image:
      "https://img.pmsjl.com/2026/05/c654905adae77bb51e5727e62c44a46a.avif",
    degree: "95新",
    price: 96,
    inventory: 6,
    createTime: "2026-05-28 23:41:00"
  },
  {
    id: "2060034286219800578",
    name: "索尼 WH-1000XM5 降噪耳机",
    description: "头戴式无线降噪耳机，30 小时续航并支持快充，日常使用正常。",
    image: "https://img.pmsjl.com/2026/05/49ea38ade3208fc869bb4822526330a2.png",
    degree: "八五新",
    price: 1899,
    inventory: 1,
    createTime: "2026-05-29 00:23:42"
  },
  {
    id: "2064027734698377217",
    name: "瓦尔登湖",
    description: "基本全新，没有明显勾画痕迹，适合收藏或日常阅读。",
    image:
      "https://pmsjl-01.oss-cn-shenzhen.aliyuncs.com/commodity_avatar/2058864659104120834/T3dWoD8W-MTYxMzk4NjA3MDI0Nl_kuIrlnJYuanBn.jpg",
    degree: "九九新",
    price: 10,
    inventory: 4,
    createTime: "2026-06-09 00:52:14"
  }
];

const layoutRoot = ref<HTMLElement | null>(null);
const failedImages = reactive<Record<string, boolean>>({});

const markImageFailed = (id: string) => {
  failedImages[id] = true;
};

const formatPrice = (price: number) =>
  new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 0
  }).format(price);

const formatDate = (dateTime: string) =>
  dateTime.slice(0, 10).replaceAll("-", ".");

onMounted(() => {
  animateIn(
    layoutRoot.value?.querySelectorAll(
      ".auth-market-showcase, .auth-market-panel"
    ) || []
  );
});
</script>

<style scoped lang="scss">
.auth-market-page {
  display: flex;
  min-height: 100dvh;
  padding: 28px clamp(24px, 4vw, 64px);
  flex-direction: column;
  overflow-x: hidden;
  color: var(--market-ink);
  background: var(--market-body-bg);
}

.auth-market-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: min(1460px, 100%);
  margin: 0 auto;
}

.auth-market-brand {
  display: flex;
  align-items: center;
  gap: 12px;

  img {
    width: 46px;
    height: 46px;
    border: 1px solid var(--market-line);
    border-radius: 14px;
    object-fit: contain;
    background: var(--market-surface);
    box-shadow: var(--market-shadow-soft);
  }

  div {
    display: grid;
    gap: 1px;
  }

  strong {
    font-family: var(--market-font-display);
    font-size: 18px;
    font-weight: 900;
    line-height: 1.3;
  }

  span {
    color: var(--market-muted);
    font-family: var(--market-font-mono);
    font-size: 11px;
    letter-spacing: 0.08em;
  }
}

.auth-market-shell {
  display: grid;
  grid-template-areas: "showcase panel";
  grid-template-columns: minmax(0, 1.64fr) minmax(410px, 0.95fr);
  width: min(1460px, 100%);
  height: calc(100dvh - 124px);
  min-height: 680px;
  max-height: 900px;
  margin: 22px auto 0;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 28px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-lift);
}

.auth-market-panel {
  display: grid;
  grid-area: panel;
  padding: clamp(28px, 4vw, 56px);
  place-items: center;
  border-left: 1px solid var(--market-line);
  background: var(--market-surface);
}

.auth-market-showcase {
  position: relative;
  display: flex;
  grid-area: showcase;
  min-width: 0;
  padding: clamp(30px, 4vw, 54px);
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  background: radial-gradient(
      circle at 88% 8%,
      var(--market-yellow-soft),
      transparent 28%
    ),
    linear-gradient(
      135deg,
      var(--market-primary-soft),
      var(--market-surface-soft)
    );

  &::before {
    position: absolute;
    right: -70px;
    bottom: -105px;
    width: 310px;
    height: 310px;
    border: 42px solid var(--market-wash);
    border-radius: 50%;
    content: "";
  }

  &::after {
    position: absolute;
    top: 44%;
    right: -44px;
    color: var(--market-wash);
    font-family: var(--market-font-display);
    font-size: clamp(72px, 8vw, 130px);
    font-weight: 900;
    line-height: 1;
    content: "MARKET";
    transform: rotate(-90deg);
    pointer-events: none;
  }
}

.showcase-copy,
.showcase-grid {
  position: relative;
  z-index: 1;
}

.showcase-copy {
  h1 {
    max-width: 680px;
    margin: 16px 0 10px;
    font-family: var(--market-font-display);
    font-size: clamp(34px, 3.7vw, 52px);
    font-weight: 900;
    letter-spacing: -0.035em;
    line-height: 1.08;
  }

  p {
    margin: 0;
    color: var(--market-muted);
    font-size: 15px;
    line-height: 1.7;
  }
}

.showcase-grid {
  display: grid;
  grid-template-areas:
    "featured secondary-a"
    "featured secondary-b";
  grid-template-columns: minmax(250px, 1.08fr) minmax(230px, 0.92fr);
  grid-template-rows: repeat(2, minmax(150px, 1fr));
  gap: 16px;
  min-height: 382px;
  margin-top: 28px;
}

.showcase-card {
  position: relative;
  display: grid;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 20px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);

  &:nth-child(2) {
    grid-area: secondary-a;
  }

  &:nth-child(3) {
    grid-area: secondary-b;
  }

  &:not(.is-featured) {
    grid-template-columns: minmax(112px, 42%) minmax(0, 1fr);

    .showcase-media {
      min-height: 100%;
      border-radius: 0;
    }

    .showcase-card-copy {
      padding: 17px;
    }

    .showcase-meta {
      display: block;

      span {
        display: none;
      }

      strong {
        display: block;
        margin-bottom: 7px;
        font-size: 18px;
      }
    }

    h2 {
      display: -webkit-box;
      margin-bottom: 7px;
      overflow: hidden;
      font-size: 16px;
      line-height: 1.35;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
    }

    p {
      display: none;
    }
  }
}

.showcase-card.is-featured {
  grid-area: featured;
  color: #fff;
  background: #153e98;

  .showcase-media {
    position: absolute;
    inset: 0;

    &::after {
      position: absolute;
      inset: 30% 0 0;
      background: linear-gradient(180deg, transparent, rgba(7, 22, 53, 0.94));
      content: "";
    }
  }

  .showcase-card-copy {
    position: relative;
    z-index: 1;
    display: flex;
    padding: 24px;
    align-self: end;
    flex-direction: column;
    justify-content: flex-end;
  }

  .showcase-meta span,
  p,
  .inventory {
    color: rgba(255, 255, 255, 0.78);
  }

  .showcase-meta strong {
    color: var(--market-yellow);
  }

  h2 {
    margin: 8px 0;
    font-size: clamp(22px, 2vw, 29px);
  }

  p {
    display: -webkit-box;
    margin: 0 0 14px;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.65;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
}

.showcase-media {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background: var(--market-primary-soft);

  img,
  .image-fallback {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.image-fallback {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--market-primary);
  background: linear-gradient(
    145deg,
    var(--market-primary-soft),
    var(--market-yellow-soft)
  );

  span {
    display: grid;
    width: 52px;
    height: 52px;
    place-items: center;
    border: 2px solid currentColor;
    border-radius: 16px;
    font-family: var(--market-font-display);
    font-size: 25px;
    font-weight: 900;
  }

  small {
    font-size: 12px;
    font-weight: 700;
  }
}

.condition-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
  padding: 5px 9px;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 999px;
  color: #10213a;
  font-size: 11px;
  font-weight: 900;
  line-height: 1;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 5px 12px rgba(19, 44, 91, 0.16);
}

.showcase-card-copy {
  min-width: 0;
}

.showcase-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  span {
    color: var(--market-muted);
    font-family: var(--market-font-mono);
    font-size: 11px;
  }

  strong {
    color: var(--market-orange);
    font-family: var(--market-font-mono);
    font-size: 20px;
    font-variant-numeric: tabular-nums;
  }
}

.showcase-card h2 {
  color: inherit;
  font-family: var(--market-font-display);
  font-weight: 900;
}

.inventory {
  color: var(--market-muted);
  font-size: 12px;
  font-weight: 700;
}

:deep(.auth-entry-form) {
  width: min(410px, 100%);
}

:deep(.auth-form-kicker) {
  display: inline-flex;
  min-height: 30px;
  margin-bottom: 18px;
  padding: 4px 11px;
  align-items: center;
  gap: 7px;
  border-radius: 999px;
  color: var(--market-primary);
  font-size: 13px;
  font-weight: 800;
  background: var(--market-primary-soft);
}

:deep(.auth-form-kicker::before) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

:deep(.auth-form-heading) {
  margin-bottom: 28px;
}

:deep(.auth-form-heading h2) {
  margin: 0;
  color: var(--market-ink);
  font-family: var(--market-font-display);
  font-size: clamp(29px, 3vw, 36px);
  font-weight: 900;
  letter-spacing: -0.025em;
  line-height: 1.2;
}

:deep(.auth-form-heading p) {
  margin: 9px 0 0;
  color: var(--market-muted);
  font-size: 14px;
  line-height: 1.7;
}

:deep(.auth-entry-form .el-form-item) {
  margin-bottom: 20px;
}

:deep(.auth-entry-form .el-form-item__label) {
  padding-bottom: 8px;
  color: var(--market-ink);
  font-weight: 800;
  line-height: 1.3;
}

:deep(.auth-entry-form .el-input__wrapper) {
  min-height: 50px;
  padding: 1px 15px;
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--market-line) inset;
  transition: box-shadow var(--market-dur-fast) var(--market-ease-standard),
    background var(--market-dur-fast) var(--market-ease-standard);
}

:deep(.auth-entry-form .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--market-line-strong) inset;
}

:deep(.auth-entry-form .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--market-primary) inset, var(--market-focus);
}

:deep(.auth-entry-form .el-input__inner) {
  min-width: 0;
  font-size: 15px;
}

:deep(.auth-submit) {
  width: 100%;
  min-height: 50px;
  margin-top: 4px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 900;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
}

:deep(.auth-switch) {
  margin: 18px 0 0;
  color: var(--market-muted);
  font-size: 14px;
  text-align: center;
}

:deep(.auth-switch-link) {
  min-height: 44px;
  padding: 0 6px;
  border: 0;
  color: var(--market-primary);
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}

:deep(.auth-switch-link:hover) {
  color: var(--market-primary-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}

:deep(.auth-switch-link:disabled) {
  opacity: 0.45;
  cursor: not-allowed;
}

:deep(.auth-security) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  color: var(--market-faint);
  font-size: 12px;
}

:deep(.auth-security::before),
:deep(.auth-security::after) {
  height: 1px;
  flex: 1;
  background: var(--market-line);
  content: "";
}

@media (min-width: 1024px) and (max-width: 1199px) {
  .auth-market-page {
    padding-right: 24px;
    padding-left: 24px;
  }

  .auth-market-shell {
    grid-template-columns: minmax(0, 1fr) minmax(390px, 0.72fr);
  }

  .auth-market-showcase {
    padding: 30px;
  }

  .showcase-copy h1 {
    font-size: 34px;
  }

  .showcase-grid {
    grid-template-columns: minmax(220px, 1fr) minmax(200px, 0.9fr);
  }

  .showcase-card:not(.is-featured) {
    grid-template-columns: minmax(92px, 40%) minmax(0, 1fr);

    .showcase-card-copy {
      padding: 13px;
    }

    .inventory {
      display: none;
    }
  }
}

@media (max-width: 1023px) {
  .auth-market-page {
    padding: 20px;
  }

  .auth-market-shell {
    grid-template-areas:
      "panel"
      "showcase";
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 0;
    max-height: none;
  }

  .auth-market-panel {
    min-height: 560px;
    padding: 48px;
    border-bottom: 1px solid var(--market-line);
    border-left: 0;
  }

  .auth-market-showcase {
    min-height: 660px;
  }

  .showcase-grid {
    min-height: 380px;
  }
}

@media (max-width: 767px) {
  .auth-market-page {
    padding: 14px 12px 20px;
  }

  .auth-market-header {
    padding: 0 4px;
  }

  .auth-market-brand img {
    width: 42px;
    height: 42px;
  }

  .auth-market-brand strong {
    font-size: 16px;
  }

  .auth-market-brand span {
    display: none;
  }

  .auth-market-shell {
    margin-top: 14px;
    border-radius: 20px;
  }

  .auth-market-panel {
    min-height: 0;
    padding: 30px 20px 34px;
  }

  .auth-market-showcase {
    min-height: 0;
    padding: 28px 20px 30px;
  }

  .auth-market-showcase::after {
    display: none;
  }

  .showcase-copy h1 {
    margin-top: 12px;
    font-size: 30px;
  }

  .showcase-copy p {
    font-size: 14px;
  }

  .showcase-grid {
    display: grid;
    grid-template-areas: none;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: none;
    gap: 12px;
    min-height: 0;
    margin-top: 22px;
  }

  .showcase-card,
  .showcase-card.is-featured,
  .showcase-card:not(.is-featured) {
    display: grid;
    grid-area: auto;
    grid-template-columns: 104px minmax(0, 1fr);
    min-height: 112px;
    color: var(--market-ink);
    background: var(--market-surface);

    .showcase-media {
      position: relative;
      inset: auto;
      min-height: 112px;
    }

    .showcase-media::after {
      display: none;
    }

    .showcase-card-copy {
      position: relative;
      display: block;
      padding: 14px;
      align-self: auto;
    }

    .showcase-meta {
      display: block;
    }

    .showcase-meta span {
      display: none;
    }

    .showcase-meta strong {
      display: block;
      margin-bottom: 5px;
      color: var(--market-orange);
      font-size: 17px;
    }

    h2 {
      display: -webkit-box;
      margin: 0 0 7px;
      overflow: hidden;
      color: var(--market-ink);
      font-size: 15px;
      line-height: 1.35;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
    }

    p {
      display: none;
    }

    .inventory {
      display: inline;
      color: var(--market-muted);
    }
  }

  :deep(.auth-form-heading) {
    margin-bottom: 24px;
  }

  :deep(.auth-form-heading h2) {
    font-size: 30px;
  }

  :deep(.auth-entry-form .el-input__wrapper) {
    min-height: 48px;
  }
}
</style>
