<template>
  <div class="market-page discover-home">
    <section class="discovery-hero" aria-labelledby="discovery-title">
      <div class="discovery-copy">
        <span class="discovery-kicker"><i></i>课间，来逛逛。</span>
        <h1 id="discovery-title">
          好物不毕业，<br /><span>陪你下一程。</span>
        </h1>
        <p>
          从翻过的书，到心动的数码。<br
            class="mobile-break"
          />让闲置在同学之间，遇见新的日常。
        </p>
        <form
          class="discovery-search"
          role="search"
          @submit.prevent="searchMarket"
        >
          <el-icon aria-hidden="true"><Search /></el-icon>
          <input
            v-model="searchText"
            aria-label="搜索校园好物"
            placeholder="想找什么好物？"
            type="search"
          />
          <button type="submit" aria-label="搜索好物">
            <el-icon><ArrowRight /></el-icon>
          </button>
        </form>
        <div class="search-suggestions">
          <span>试试搜</span
          ><router-link
            v-for="word in ['教材', '耳机', '台灯']"
            :key="word"
            :to="{ path: '/user/commodity', query: { q: word } }"
            >{{ word }}<el-icon><TopRight /></el-icon
          ></router-link>
        </div>
      </div>
      <router-link
        to="/user/commodity"
        class="discovery-scene"
        aria-label="去发现校园好物"
      >
        <img
          v-if="!sceneFailed"
          src="/generated/carousel-dorm.png"
          alt="阳光下的校园宿舍，桌上摆着台灯、书籍和生活用品"
          width="2172"
          height="724"
          fetchpriority="high"
          @error="sceneFailed = true"
        />
        <div v-else class="scene-fallback">把喜欢，继续传下去。</div>
        <span class="scene-caption"
          >给生活一点新鲜感 <el-icon><TopRight /></el-icon
        ></span>
        <span class="scene-sticker"
          ><small>GOOD THINGS, AGAIN.</small>闲置好物<br /><strong
            >重新出场！</strong
          ></span
        >
        <span class="scene-spark" aria-hidden="true">✳</span>
      </router-link>
    </section>

    <section class="discovery-listings" aria-labelledby="fresh-title">
      <div class="discovery-section-heading">
        <div>
          <span class="section-index">01 / DISCOVER</span>
          <h2 id="fresh-title">
            下一件心动好物<span class="heading-dot"></span>
          </h2>
        </div>
        <router-link class="text-link" to="/user/commodity"
          >逛全部好物 <el-icon><ArrowRight /></el-icon
        ></router-link>
      </div>
      <div
        v-if="freshLoading"
        class="discovery-skeleton"
        role="status"
        aria-label="正在寻找好物"
      >
        <el-skeleton v-for="n in 4" :key="n" animated
          ><template #template
            ><el-skeleton-item
              variant="image"
              class="skeleton-cover" /><el-skeleton-item
              variant="h3" /><el-skeleton-item variant="text" /></template
        ></el-skeleton>
      </div>
      <div v-else-if="freshFailed" class="discovery-empty" role="status">
        <p>好物还在路上，稍后再试试。</p>
        <el-button @click="loadFreshCommodities">重新加载</el-button>
      </div>
      <CommodityList
        v-else-if="freshCommodities.length"
        :commodity-list="freshCommodities"
      />
      <div v-else class="discovery-empty">
        <h3>下一件好物，也许就来自你。</h3>
        <p>暂时没有可展示的精选商品，去集市看看，或分享一件闲置。</p>
        <router-link class="text-link" to="/user/commodity"
          >去逛集市 <el-icon><ArrowRight /></el-icon
        ></router-link>
      </div>
    </section>

    <section class="discovery-extras" aria-label="校园好物指南">
      <router-link class="guide-invitation" to="/user/agentGuide">
        <div>
          <span class="invitation-kicker">你的选物搭子 · AI</span>
          <h2>有点心动，<br />又不知道怎么选？</h2>
          <p>聊聊预算和用途，一起缩小选择范围。</p>
          <span class="invitation-action"
            >帮我挑一挑 <el-icon><ArrowRight /></el-icon
          ></span>
        </div>
        <span class="guide-sticker" aria-hidden="true"
          ><el-icon><MagicStick /></el-icon
          ><small>一起<br />选好物</small></span
        >
      </router-link>
      <router-link class="community-invitation" to="/user/post">
        <div>
          <span class="invitation-kicker">同学的经验，比参数更有用</span>
          <h2>先看攻略，<br />少走一点弯路。</h2>
          <p>验机、教材、交易心得，都可以聊。</p>
          <span class="invitation-action"
            >看看大家怎么说 <el-icon><ArrowRight /></el-icon
          ></span>
        </div>
        <span class="community-sticker" aria-hidden="true"
          ><el-icon><ChatDotRound /></el-icon
        ></span>
      </router-link>
    </section>
    <footer class="discovery-footer">
      <span>好物循环，校园日常。</span
      ><router-link to="/user/notice"
        >校园公告 <el-icon><TopRight /></el-icon
      ></router-link>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Search,
  ArrowRight,
  TopRight,
  MagicStick,
  ChatDotRound
} from "@element-plus/icons-vue";
import CommodityList from "@/components/CommodityList/index.vue";
import { listCommodityVoByPageUsingPost } from "@/api/commodityController";
const router = useRouter();
const searchText = ref("");
const sceneFailed = ref(false);
const searchMarket = () =>
  router.push({
    path: "/user/commodity",
    query: searchText.value.trim() ? { q: searchText.value.trim() } : {}
  });
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

onMounted(() => void loadFreshCommodities());
</script>

<style scoped lang="scss">
.discover-home {
  display: grid;
  gap: 38px;
}
.discovery-hero {
  display: grid;
  grid-template-columns: 0.95fr 1.05fr;
  gap: 44px;
  align-items: center;
}
.discovery-copy {
  padding: 8px 0;
}
.discovery-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  i {
    width: 8px;
    height: 8px;
    background: var(--market-primary);
    border-radius: 50%;
  }
}
h1 {
  margin: 16px 0;
  font-size: clamp(36px, 3.8vw, 54px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -2px;
  span {
    position: relative;
    z-index: 0;
  }
  span::after {
    content: "";
    position: absolute;
    z-index: -1;
    left: 0;
    right: 0;
    bottom: 3px;
    height: 15px;
    background: var(--market-sticker-yellow);
    transform: rotate(-2deg);
    border-radius: 3px;
  }
}
.discovery-copy > p {
  color: var(--market-muted);
  font-size: 14px;
  line-height: 1.8;
}
.mobile-break {
  display: none;
}
.discovery-search {
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 54px;
  max-width: 450px;
  margin-top: 22px;
  padding: 5px 6px 5px 18px;
  border: 1px solid var(--market-line-strong);
  border-radius: 999px;
  background: var(--market-surface);
  transition: border-color 180ms;
  &:focus-within {
    border-color: var(--market-primary);
    box-shadow: var(--market-focus);
  }
  > .el-icon {
    color: var(--market-muted);
    font-size: 20px;
  }
  input {
    width: 100%;
    min-width: 0;
    padding: 7px 0;
    border: 0;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: var(--market-ink);
    &::placeholder {
      color: var(--market-muted);
    }
  }
  button {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    width: 42px;
    height: 42px;
    border: 0;
    border-radius: 50%;
    background: var(--market-primary);
    color: var(--market-on-primary);
    cursor: pointer;
    font-size: 20px;
  }
}
.search-suggestions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 9px;
  font-size: 11px;
  color: var(--market-muted);
  a {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    min-height: 28px;
    &:hover {
      color: var(--market-primary);
    }
  }
}
.discovery-scene {
  position: relative;
  display: block;
  height: 324px;
  border-radius: 20px;
  background: var(--market-surface-soft);
  isolation: isolate;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: 50% center;
    border-radius: inherit;
  }
  &::after {
    content: "";
    position: absolute;
    inset: 0;
    border-radius: inherit;
    box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.06);
    pointer-events: none;
  }
}
.scene-fallback {
  display: grid;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--market-muted);
}
.scene-caption {
  position: absolute;
  top: 16px;
  left: 16px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: #fffaf0;
  color: #253348;
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 11px;
  font-weight: 600;
}
.scene-sticker {
  position: absolute;
  right: -10px;
  bottom: 18px;
  padding: 16px 22px;
  border: 2px solid #fff9e7;
  border-radius: 3px;
  background: var(--market-sticker-yellow);
  color: var(--market-sticker-ink);
  font-family: var(--market-playful-font);
  font-size: 26px;
  line-height: 1.15;
  transform: rotate(7deg);
  box-shadow: 0 5px 12px rgba(30, 35, 45, 0.12);
  small {
    display: block;
    font-family: var(--market-font-body);
    font-size: 7px;
    letter-spacing: 1px;
    margin-bottom: 9px;
  }
  strong {
    font-weight: 700;
  }
}
.scene-spark {
  position: absolute;
  right: 10px;
  top: -22px;
  color: var(--market-primary);
  font-size: 62px;
  line-height: 1;
  transform: rotate(12deg);
}
.discovery-section-heading {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 20px;
  h2 {
    font-size: 26px;
    letter-spacing: -1px;
    font-weight: 750;
    line-height: 1.4;
  }
}
.section-index {
  display: block;
  margin-bottom: 6px;
  color: var(--market-muted);
  font-size: 9px;
  letter-spacing: 1.8px;
  font-weight: 600;
}
.heading-dot {
  display: inline-block;
  margin-left: 7px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--market-primary);
}
.text-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  color: var(--market-ink);
  font-size: 12px;
  white-space: nowrap;
  &:hover {
    color: var(--market-primary);
  }
}
.discovery-skeleton {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
  .skeleton-cover {
    width: 100%;
    height: auto;
    aspect-ratio: 1.15;
    border-radius: 14px;
    margin-bottom: 12px;
  }
}
.discovery-empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 38px 20px;
  background: var(--market-surface-soft);
  border-radius: 18px;
  text-align: center;
  h3 {
    font-size: 20px;
    font-weight: 700;
  }
  p {
    color: var(--market-muted);
    font-size: 14px;
  }
}
.discovery-extras {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.guide-invitation,
.community-invitation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 32px;
  border-radius: 18px;
  overflow: hidden;
  h2 {
    margin: 13px 0 10px;
    font-size: 27px;
    line-height: 1.35;
    font-weight: 700;
    letter-spacing: -1px;
  }
  p {
    font-size: 12px;
    opacity: 0.85;
  }
}
.guide-invitation {
  background: var(--market-editorial-blue);
  color: #fff;
}
.community-invitation {
  background: var(--market-sticker-yellow);
  color: var(--market-sticker-ink);
}
.invitation-kicker {
  font-size: 11px;
  letter-spacing: 1px;
}
.invitation-action {
  display: inline-flex;
  align-items: center;
  gap: 20px;
  margin-top: 23px;
  font-size: 12px;
  font-weight: 650;
}
.guide-sticker {
  display: grid;
  place-items: center;
  gap: 8px;
  flex-shrink: 0;
  width: 108px;
  height: 134px;
  transform: rotate(9deg);
  background: #e4edff;
  color: #234fdd;
  border: 5px solid #fff;
  border-radius: 9px;
  .el-icon {
    font-size: 35px;
  }
  small {
    font-family: var(--market-playful-font);
    font-size: 19px;
    line-height: 1.2;
  }
}
.community-sticker {
  display: grid;
  place-items: center;
  width: 98px;
  height: 98px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--market-sticker-coral);
  color: var(--market-sticker-ink);
  transform: rotate(-10deg);
  .el-icon {
    font-size: 48px;
  }
}
.discovery-footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--market-line);
  padding-top: 24px;
  color: var(--market-muted);
  font-size: 11px;
  a {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-height: 30px;
  }
}
@media (max-width: 1000px) {
  .discovery-hero {
    gap: 28px;
  }
  h1 {
    font-size: 40px;
  }
  .discovery-scene {
    height: 300px;
  }
  .guide-invitation,
  .community-invitation {
    padding: 24px;
  }
  .guide-sticker,
  .community-sticker {
    display: none;
  }
}
@media (max-width: 760px) {
  .discover-home {
    gap: 28px;
  }
  .discovery-hero {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .discovery-copy {
    padding: 0;
  }
  .discovery-kicker {
    font-size: 11px;
  }
  h1 {
    font-size: 38px;
    margin: 12px 0;
    line-height: 1.2;
  }
  .discovery-copy > p {
    font-size: 12px;
  }
  .discovery-search {
    margin-top: 16px;
    max-width: none;
  }
  .discovery-scene {
    height: 154px;
    border-radius: 14px;
  }
  .scene-caption {
    top: 12px;
    left: 12px;
    font-size: 9px;
  }
  .scene-sticker {
    right: 10px;
    bottom: 10px;
    padding: 10px 15px;
    font-size: 20px;
    small {
      font-size: 6px;
      margin-bottom: 4px;
    }
  }
  .scene-spark {
    font-size: 40px;
    top: -14px;
    right: 6px;
  }
  .discovery-section-heading {
    margin-bottom: 16px;
    gap: 8px;
    h2 {
      font-size: 22px;
    }
  }
  .section-index {
    font-size: 8px;
  }
  .text-link {
    font-size: 11px;
    gap: 5px;
  }
  .discovery-skeleton {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .discovery-extras {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .guide-sticker,
  .community-sticker {
    display: grid;
    width: 78px;
  }
  .guide-sticker {
    height: 102px;
  }
  .community-sticker {
    height: 78px;
  }
  .guide-invitation,
  .community-invitation {
    h2 {
      font-size: 24px;
    }
  }
}
</style>
