<template>
  <div class="market-page home-page" ref="pageRef">
    <section class="home-hero market-board">
      <div class="hero-copy">
        <span class="market-eyebrow">今日校园集市</span>
        <h1>课间逛一圈，把同校好物带回宿舍</h1>
        <p>
          把教材、数码、运动装备和生活用品重新流转起来。先逛一圈，再决定要不要发布自己的闲置。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/user/commodity')">
            去逛商品
          </el-button>
          <el-button @click="$router.push('/user/account')">
            管理我的摊位
          </el-button>
        </div>
      </div>
      <div class="hero-board" aria-label="平台亮点">
        <div class="hero-board-title">
          <span>AFTER CLASS MARKET</span>
        </div>
        <div class="pin-card card-book">
          <small>TEXTBOOK</small>教材换季<br />价格友好
        </div>
        <div class="pin-card card-tech">
          <small>DIGITAL</small>数码闲置<br />先到先得
        </div>
        <div class="pin-card card-life">
          <small>LIFESTYLE</small>生活小物<br />校内流转
        </div>
      </div>
    </section>

    <section class="quick-grid">
      <button
        v-for="item in quickEntries"
        :key="item.title"
        class="quick-note"
        type="button"
        @click="$router.push(item.path)"
      >
        <img class="quick-illustration" :src="item.icon" :alt="item.title" />
        <span>{{ item.kicker }}</span>
        <strong>{{ item.title }}</strong>
        <em>{{ item.desc }}</em>
      </button>
    </section>

    <section class="carousel-note market-panel">
      <div class="section-heading">
        <span class="market-eyebrow">逛摊路线</span>
        <h2>从教材摊、数码摊逛到宿舍生活区</h2>
      </div>
      <div class="carousel-frame">
        <el-carousel height="320px" motion-blur>
          <el-carousel-item v-for="(item, index) in images" :key="index">
            <div
              class="photo-card"
              :class="{ 'is-missing': failedImages.includes(index) }"
            >
              <img
                v-if="!failedImages.includes(index)"
                :src="item.src"
                :alt="item.title"
                @error="handleImageError(index)"
              />
              <div v-else class="photo-placeholder">
                <span>照片暂未送达</span>
                <small>仍可继续浏览其他校园场景</small>
              </div>
              <div class="photo-caption">
                <span>{{ String(index + 1).padStart(2, "0") }}</span>
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.note }}</small>
                </div>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { animateIn, parallaxFloat } from "@/utils/motion";
import illBuy from "@/assets/illustrations/textbook.svg";
import illAgent from "@/assets/illustrations/ai-lamp.svg";
import illOrder from "@/assets/illustrations/ticket-stub.svg";
import illPost from "@/assets/illustrations/notice-pin.svg";

const $router = useRouter();
const pageRef = ref<HTMLElement | null>(null);
let cleanupParallax: (() => void) | undefined;

const images = [
  {
    src: "https://pic.yupi.icu/5563/202503111324950.jpeg",
    title: "教材与学习用品",
    note: "让上一学期的资料继续发挥作用"
  },
  {
    src: "https://pic.yupi.icu/5563/202502241324758.png",
    title: "数码与桌面装备",
    note: "先聊需求，再约时间当面验货"
  },
  {
    src: "https://pic.yupi.icu/5563/202502241324627.jpg",
    title: "宿舍生活好物",
    note: "把闲置交给真正需要的同学"
  },
  {
    src: "https://pic.yupi.icu/5563/202502241320811.jpg",
    title: "校内轻松流转",
    note: "近距离交易，沟通更直接"
  }
];

const failedImages = ref<number[]>([]);

const handleImageError = (index: number) => {
  if (!failedImages.value.includes(index)) failedImages.value.push(index);
};

const quickEntries = [
  {
    kicker: "BUY",
    title: "商品集市",
    desc: "查找教材、数码和生活用品",
    path: "/user/commodity",
    icon: illBuy
  },
  {
    kicker: "AGENT",
    title: "导购 Agent",
    desc: "按预算和用途整理购买建议",
    path: "/user/agentGuide",
    icon: illAgent
  },
  {
    kicker: "ORDER",
    title: "我的订单",
    desc: "查看支付状态和交易记录",
    path: "/user/orders",
    icon: illOrder
  },
  {
    kicker: "POST",
    title: "交易攻略",
    desc: "看看同学们的交易经验",
    path: "/user/post",
    icon: illPost
  }
];

onMounted(() => {
  animateIn(
    pageRef.value?.querySelectorAll(
      ".home-hero, .quick-note, .carousel-note"
    ) || []
  );

  const heroBoard = pageRef.value?.querySelector(".hero-board");
  if (heroBoard instanceof HTMLElement) {
    cleanupParallax = parallaxFloat(
      heroBoard,
      heroBoard.querySelectorAll(".pin-card"),
      5
    );
  }
});

onUnmounted(() => {
  cleanupParallax?.();
});
</script>

<style scoped lang="scss">
.home-page {
  display: grid;
  gap: 22px;
}

.home-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 430px);
  gap: 28px;
  min-height: 360px;
  padding: clamp(30px, 4vw, 48px);
  overflow: hidden;
  background: radial-gradient(
      circle at 12% 0,
      rgba(244, 201, 93, 0.18),
      transparent 30%
    ),
    linear-gradient(115deg, rgba(43, 110, 80, 0.06), transparent 45%),
    var(--market-card-bg);

  &::after {
    position: absolute;
    right: -25px;
    bottom: -31px;
    width: 150px;
    height: 70px;
    border: 2px solid rgba(192, 57, 43, 0.16);
    border-radius: 50%;
    content: "";
    transform: rotate(-12deg);
  }
}

.market-status {
  display: flex;
  width: fit-content;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding: 8px 12px;
  border: 1px solid rgba(43, 110, 80, 0.18);
  border-radius: 999px;
  color: var(--market-muted);
  font-size: 12px;
  background: var(--market-note-green-bg);

  i {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--market-green);
    box-shadow: 0 0 0 4px rgba(43, 110, 80, 0.1);
  }

  strong {
    color: var(--market-green);
  }
}

.hero-copy {
  align-self: center;

  h1 {
    max-width: 640px;
    margin: 16px 0;
    color: var(--market-ink);
    font-family: var(--market-font-display);
    font-size: clamp(34px, 5vw, 56px);
    font-weight: 900;
    line-height: 1.06;
  }

  p {
    max-width: 560px;
    color: var(--market-muted);
    font-size: 17px;
    line-height: 1.8;
  }
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.hero-board {
  position: relative;
  min-height: 292px;
  border: 10px solid rgba(101, 69, 47, 0.34);
  border-radius: 12px;
  background: linear-gradient(
      var(--market-board-overlay),
      var(--market-board-overlay)
    ),
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 27px,
      rgba(96, 67, 31, 0.09) 28px
    ),
    var(--market-board-overlay);
  box-shadow: inset 0 0 0 2px rgba(255, 246, 227, 0.18),
    inset 0 0 24px rgba(62, 45, 24, 0.12);
}

.hero-board-title {
  position: absolute;
  top: 15px;
  left: 50%;
  z-index: 2;
  display: grid;
  min-width: 142px;
  gap: 1px;
  padding: 7px 14px;
  color: var(--market-chalk);
  text-align: center;
  background: #284d3e;
  box-shadow: 0 6px 12px rgba(62, 45, 24, 0.17);
  transform: translateX(-50%) rotate(-0.6deg);

  span {
    color: rgba(253, 246, 227, 0.58);
    font-family: var(--market-font-mono);
    font-size: 8px;
    letter-spacing: 1.2px;
  }
}

.pin-card {
  position: absolute;
  display: grid;
  place-items: center;
  width: 142px;
  min-height: 112px;
  padding: 18px;
  border: 1px solid rgba(35, 49, 63, 0.1);
  border-radius: 8px;
  box-shadow: var(--market-shadow-soft);
  font-size: 20px;
  font-weight: 900;
  line-height: 1.35;
  text-align: center;

  small {
    display: block;
    margin-bottom: 6px;
    color: var(--market-orange);
    font-family: var(--market-font-mono);
    font-size: 8px;
    letter-spacing: 1.2px;
  }
}

.pin-card::before {
  position: absolute;
  top: -9px;
  width: 18px;
  height: 18px;
  border: 4px solid var(--market-pin-border);
  border-radius: 50%;
  background: var(--market-orange);
  content: "";
}

.card-book {
  top: 66px;
  left: 28px;
  background: var(--market-note-yellow-bg);
  transform: rotate(-5deg);
}

.card-tech {
  right: 34px;
  top: 86px;
  color: #fff;
  background: var(--market-blue);
  transform: rotate(4deg);
}

.card-tech small {
  color: #ffe0a3;
}

.card-life {
  bottom: 28px;
  left: 108px;
  background: var(--market-note-green-bg);
  transform: rotate(2deg);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.quick-note {
  position: relative;
  display: grid;
  align-content: start;
  gap: 8px;
  height: 100%;
  min-height: 174px;
  padding: 20px 20px 17px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  cursor: pointer;
  transform: rotate(-1.2deg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease,
    transform var(--market-dur-fast) var(--market-ease-spring);

  &:nth-child(2n) {
    transform: rotate(1deg);
  }

  &:hover {
    border-color: rgba(224, 101, 31, 0.45);
    box-shadow: var(--market-shadow-lift);
    transform: rotate(0deg);
  }

  .quick-illustration {
    width: 40px;
    height: 40px;
    color: var(--market-green);
  }

  span {
    color: var(--market-orange);
    font-size: 12px;
    font-weight: 900;
    letter-spacing: 2px;
  }

  strong {
    font-family: var(--market-font-display);
    font-size: 22px;
    font-weight: 900;
  }

  em {
    color: var(--market-muted);
    font-style: normal;
    line-height: 1.6;
  }

  b {
    align-self: end;
    margin-top: 7px;
    color: var(--market-green);
    font-size: 11px;
    font-weight: 800;
  }
}

.carousel-note {
  padding: 24px;
}

.section-heading {
  margin-bottom: 16px;

  h2 {
    margin-top: 8px;
    font-family: var(--market-font-display);
    font-size: 24px;
    font-weight: 900;
  }
}

// 牛皮纸相框
.carousel-frame {
  position: relative;
  padding: 12px;
  border: 1px solid rgba(143, 93, 51, 0.22);
  border-radius: 10px;
  background: var(--market-paper-deep);
  box-shadow: inset 0 2px 10px rgba(62, 45, 24, 0.1);

  &::before,
  &::after {
    position: absolute;
    z-index: 2;
    top: 4px;
    width: 68px;
    height: 14px;
    background: rgba(217, 173, 101, 0.55);
    content: "";
  }

  &::before {
    left: 8%;
    transform: rotate(-2deg);
  }

  &::after {
    right: 8%;
    transform: rotate(2deg);
  }
}

.el-carousel {
  border-radius: 6px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.photo-card {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--market-surface);
}

.photo-caption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 14px 18px;
  color: var(--market-chalk);
  background: linear-gradient(
    90deg,
    rgba(31, 68, 56, 0.96),
    rgba(31, 68, 56, 0.78)
  );

  > span {
    font-family: var(--market-font-mono);
    font-size: 22px;
    font-weight: 900;
  }

  div {
    display: grid;
    gap: 2px;
  }

  small {
    color: rgba(253, 246, 227, 0.68);
  }
}

.photo-placeholder {
  display: grid;
  width: 100%;
  height: 100%;
  place-content: center;
  gap: 5px;
  color: var(--market-ink);
  text-align: center;
  background: repeating-linear-gradient(
      0deg,
      transparent 0 27px,
      var(--market-line) 27px 28px
    ),
    var(--market-surface);

  span {
    font-family: var(--market-font-display);
    font-size: 22px;
  }

  small {
    color: var(--market-muted);
  }
}

@media (max-width: 980px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .home-hero {
    padding: 26px 20px;
  }

  .hero-board {
    display: grid;
    grid-auto-columns: 142px;
    grid-auto-flow: column;
    gap: 12px;
    min-height: 0;
    padding: 58px 16px 18px;
    overflow-x: auto;
    scroll-snap-type: x mandatory;
  }

  .pin-card {
    position: relative;
    inset: auto;
    width: 142px;
    min-height: 104px;
    scroll-snap-align: start;
  }

  .market-status {
    align-items: flex-start;
    border-radius: 8px;
    flex-wrap: wrap;

    span {
      width: 100%;
      padding-left: 16px;
    }
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }

  .carousel-note {
    padding: 16px;
  }

  .photo-caption {
    padding: 11px 13px;

    small {
      display: none;
    }
  }
}
</style>
