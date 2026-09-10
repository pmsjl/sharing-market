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

    <section class="fresh-section" aria-labelledby="fresh-title">
      <div class="market-page-header">
        <div>
          <span class="market-eyebrow">今日好物橱窗</span>
          <h2 id="fresh-title" class="market-title">市集精选</h2>
          <p class="market-subtitle">
            书籍、数码、穿搭与宿舍生活，发现适合自己的校园好物。
          </p>
        </div>
        <el-button @click="$router.push('/user/commodity')"
          >逛全部商品 →</el-button
        >
      </div>
      <el-skeleton
        v-if="freshLoading"
        :rows="5"
        animated
        aria-label="正在加载精选商品"
      />
      <div
        v-else-if="freshFailed"
        class="fresh-fallback market-panel"
        role="status"
      >
        <p>精选商品暂时没有加载出来，再试一次吧。</p>
        <el-button @click="loadFreshCommodities">重新加载</el-button>
      </div>
      <CommodityList
        v-else-if="freshCommodities.length"
        :commodityList="freshCommodities"
      />
      <div v-else class="fresh-fallback market-panel">
        <p>暂时没有可展示的商品照片，先去集市逛逛吧。</p>
        <el-button @click="loadFreshCommodities">重新加载</el-button>
        <el-button @click="$router.push('/user/commodity')">去逛商品</el-button>
      </div>
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
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import CommodityList from "@/components/CommodityList/index.vue";
import { listCommodityVoByPageUsingPost } from "@/api/commodityController";
import { useRouter } from "vue-router";
import { animateIn, parallaxFloat } from "@/utils/motion";
import illBuy from "@/assets/illustrations/textbook.svg";
import illAgent from "@/assets/illustrations/ai-lamp.svg";
import illOrder from "@/assets/illustrations/ticket-stub.svg";
import illPost from "@/assets/illustrations/notice-pin.svg";

const $router = useRouter();
const pageRef = ref<HTMLElement | null>(null);
let cleanupParallax: (() => void) | undefined;

const freshCommodities = ref<API.CommodityVO[]>([]);
const freshLoading = ref(true);
const freshFailed = ref(false);
const showcaseCommodityIds = new Set([
  "2060023541910327297", // 登录页：耐克 Air Zoom 跑步鞋
  "2060034286219800578" // 登录页：索尼 WH-1000XM5 降噪耳机
]);
const preferredHomepageCommodityIds = [
  "2064027734698377223", // 书籍：活着
  "2064027734698377229", // 数码：小米无线鼠标
  "2064027734698377235", // 优衣库纯色卫衣
  "2064027734698377240" // 宿舍生活：收纳箱三件套
];
// 首页橱窗只陈列可加载的真实封面；不以场景图替代商品实拍。
const hasUsableCover = (src: string): Promise<boolean> =>
  new Promise((resolve) => {
    const cover = new Image();
    const finish = (available: boolean) => {
      window.clearTimeout(timer);
      cover.onload = null;
      cover.onerror = null;
      resolve(available);
    };
    const timer = window.setTimeout(() => finish(false), 4000);
    cover.onload = () => finish(cover.naturalWidth > 0);
    cover.onerror = () => finish(false);
    cover.src = src;
  });

const loadFreshCommodities = async () => {
  freshLoading.value = true;
  freshFailed.value = false;
  try {
    const available: API.CommodityVO[] = [];
    // 单独读取所选商品，避免它们不在最新一页时失去优先展示资格。
    const preferredResults = await Promise.allSettled(
      preferredHomepageCommodityIds.map(async (id) => {
        const result = await listCommodityVoByPageUsingPost(
          { id, current: 1, pageSize: 1, isListed: 1 },
          { silent: true }
        );
        if (result.code !== 200) throw new Error("商品加载失败");
        const item = result.data?.records?.find(
          (record) => String(record.id) === id && record.isListed !== 0
        );
        if (
          item?.commodityAvatar?.trim() &&
          (await hasUsableCover(item.commodityAvatar.trim()))
        ) {
          return item;
        }
        return undefined;
      })
    );
    for (const result of preferredResults) {
      if (result.status === "fulfilled" && result.value)
        available.push(result.value);
    }
    // 下架、删除或缺图时，以最新上架的其他真实商品补位。
    if (available.length < 4) {
      const result = await listCommodityVoByPageUsingPost(
        {
          current: 1,
          pageSize: 12,
          isListed: 1,
          sortField: "createTime",
          sortOrder: "desc"
        },
        { silent: true }
      ).catch(() => null);
      if (!result || result.code !== 200) {
        if (!available.length) throw new Error("商品加载失败");
      } else {
        const excludedIds = new Set([
          ...showcaseCommodityIds,
          ...preferredHomepageCommodityIds
        ]);
        const candidates = (result.data?.records || []).filter(
          (item) =>
            item.commodityAvatar?.trim() &&
            item.isListed !== 0 &&
            !excludedIds.has(String(item.id))
        );
        for (
          let offset = 0;
          offset < candidates.length && available.length < 4;
          offset += 4
        ) {
          const batch = candidates.slice(offset, offset + 4);
          const checks = await Promise.all(
            batch.map((item) => hasUsableCover(item.commodityAvatar!.trim()))
          );
          available.push(...batch.filter((_, index) => checks[index]));
        }
      }
    }
    freshCommodities.value = available.slice(0, 4);
  } catch {
    freshFailed.value = true;
  } finally {
    freshLoading.value = false;
  }
};

const images = [
  {
    src: "/generated/carousel-textbooks.png",
    title: "教材与学习用品"
  },
  {
    src: "/generated/carousel-digital.png",
    title: "数码与桌面装备"
  },
  {
    src: "/generated/carousel-dorm.png",
    title: "宿舍生活好物"
  },
  {
    src: "/generated/carousel-handoff.png",
    title: "校内轻松流转"
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
  void loadFreshCommodities();
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
.fresh-section {
  margin: 8px 0;
}
.fresh-fallback {
  display: grid;
  justify-items: center;
  gap: 16px;
  padding: 32px;
  color: var(--market-muted);
}

.home-page {
  display: grid;
  gap: 22px;
}

.home-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 430px);
  gap: 28px;
  min-height: 320px;
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
    font-size: clamp(30px, 3.3vw, 44px);
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
  background: #17365f;
  box-shadow: 0 6px 12px rgba(62, 45, 24, 0.17);
  transform: translateX(-50%) rotate(-0.6deg);

  span {
    color: #e8eef7;
    font-family: var(--market-font-mono);
    font-size: 12px;
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
    color: var(--market-orange-text);
    font-family: var(--market-font-mono);
    font-size: 12px;
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
  color: var(--market-on-primary);
  background: var(--market-primary);
  transform: rotate(4deg);
}

.card-tech small {
  color: inherit;
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
    color: var(--market-orange-text);
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
}
</style>
