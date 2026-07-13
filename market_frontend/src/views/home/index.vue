<template>
  <div class="market-page home-page" ref="pageRef">
    <section class="home-hero market-board">
      <div class="hero-copy">
        <span class="market-eyebrow">今日校园集市</span>
        <h1>在公告栏里发现同校好物</h1>
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
        <div class="pin-card card-book">教材换季<br />价格友好</div>
        <div class="pin-card card-tech">数码闲置<br />先到先得</div>
        <div class="pin-card card-life">生活小物<br />校内流转</div>
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
        <span>{{ item.kicker }}</span>
        <strong>{{ item.title }}</strong>
        <em>{{ item.desc }}</em>
      </button>
    </section>

    <section class="carousel-note market-panel">
      <div class="section-heading">
        <span class="market-eyebrow">公告栏精选</span>
        <h2>最近值得看看的校园交易场景</h2>
      </div>
      <el-carousel height="360px" motion-blur>
        <el-carousel-item v-for="(item, index) in images" :key="index">
          <img :src="item" alt="校园二手平台场景" />
        </el-carousel-item>
      </el-carousel>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { animateIn } from "@/utils/motion";

const $router = useRouter();
const pageRef = ref<HTMLElement | null>(null);

const images = [
  "https://pic.yupi.icu/5563/202503111324950.jpeg",
  "https://pic.yupi.icu/5563/202502241324758.png",
  "https://pic.yupi.icu/5563/202502241324627.jpg",
  "https://pic.yupi.icu/5563/202502241320811.jpg"
];

const quickEntries = [
  {
    kicker: "BUY",
    title: "商品集市",
    desc: "查找教材、数码和生活用品",
    path: "/user/commodity"
  },
  {
    kicker: "AGENT",
    title: "导购 Agent",
    desc: "按预算和用途整理购买建议",
    path: "/user/agentGuide"
  },
  {
    kicker: "ORDER",
    title: "我的订单",
    desc: "查看支付状态和交易记录",
    path: "/user/orders"
  },
  {
    kicker: "POST",
    title: "交易攻略",
    desc: "看看同学们的交易经验",
    path: "/user/post"
  }
];

onMounted(() => {
  animateIn(
    pageRef.value?.querySelectorAll(
      ".home-hero, .quick-note, .carousel-note"
    ) || []
  );
});
</script>

<style scoped lang="scss">
.home-page {
  display: grid;
  gap: 22px;
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 430px);
  gap: 28px;
  min-height: 360px;
  padding: 38px;
  overflow: hidden;
  background: var(--market-card-bg);
}

.hero-copy {
  align-self: center;

  h1 {
    max-width: 640px;
    margin: 16px 0;
    color: var(--market-ink);
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
  border: 10px solid rgba(143, 93, 51, 0.18);
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
    var(--market-paper-deep);
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
  top: 28px;
  left: 28px;
  background: var(--market-note-yellow-bg);
  transform: rotate(-5deg);
}

.card-tech {
  right: 34px;
  top: 72px;
  color: #fff;
  background: var(--market-blue);
  transform: rotate(4deg);
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
  display: grid;
  align-content: start;
  gap: 8px;
  height: 100%;
  min-height: 150px;
  padding: 20px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: rgba(217, 108, 44, 0.45);
    box-shadow: var(--market-shadow);
  }

  span {
    color: var(--market-orange);
    font-size: 12px;
    font-weight: 900;
  }

  strong {
    font-size: 22px;
    font-weight: 900;
  }

  em {
    color: var(--market-muted);
    font-style: normal;
    line-height: 1.6;
  }
}

.carousel-note {
  padding: 24px;
}

.section-heading {
  margin-bottom: 16px;

  h2 {
    margin-top: 8px;
    font-size: 24px;
    font-weight: 900;
  }
}

.el-carousel {
  border-radius: 8px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
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
    display: none;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
