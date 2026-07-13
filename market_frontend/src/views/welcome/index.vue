<template>
  <div class="market-page welcome-page" ref="pageRef">
    <section class="welcome-hero market-board">
      <div>
        <span class="market-eyebrow">Campus Bulletin</span>
        <h1>欢迎来到智能 AI 校园二手交易平台</h1>
        <p>
          这里是面向校园场景的二手交易公告栏：买家快速发现好物，卖家清晰发布商品，管理员集中维护秩序。
        </p>
      </div>
      <el-button type="primary" @click="$router.push(homePath)">
        {{ primaryActionText }}
      </el-button>
    </section>

    <section class="feature-grid">
      <article v-for="item in features" :key="item.title" class="feature-card">
        <span>{{ item.kicker }}</span>
        <h2>{{ item.title }}</h2>
        <p>{{ item.desc }}</p>
      </article>
    </section>

    <section class="role-grid">
      <div class="role-note market-note">
        <h2>普通用户</h2>
        <p>
          浏览商品、发布闲置、收藏攻略、管理订单、查看购物日历，并用智能导购整理购买思路。
        </p>
      </div>
      <div class="role-note market-note">
        <h2>平台管理员</h2>
        <p>
          维护用户、商品、订单、公告与帖子内容，保证校园二手交易流程稳定清晰。
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { animateIn } from "@/utils/motion";
import { GET_ROLE } from "@/utils/token";
import { getRoleHomePath } from "@/utils/roleHome";

const $router = useRouter();
const pageRef = ref<HTMLElement | null>(null);
const userRole = computed(() => GET_ROLE());
const isAdmin = computed(() => userRole.value === "admin");
const homePath = computed(() => getRoleHomePath(userRole.value));
const primaryActionText = computed(() =>
  isAdmin.value ? "进入管理台" : "进入校园集市"
);

const userFeatures = [
  {
    kicker: "DISCOVER",
    title: "校园好物发现",
    desc: "按分类、名称、新旧程度筛选商品，让教材、数码、生活用品快速被需要的人看见。"
  },
  {
    kicker: "GUIDE",
    title: "智能导购助手",
    desc: "把预算、用途和避雷点整理成一张导购单，先想清楚再下单。"
  },
  {
    kicker: "TRADE",
    title: "订单与支付管理",
    desc: "商品购买、待支付订单、支付状态和购物日历集中展示，交易进度更清楚。"
  },
  {
    kicker: "COMMUNITY",
    title: "攻略与留言交流",
    desc: "通过帖子、评论、私信和公告沉淀交易经验，减少信息不对称。"
  }
];

const adminFeatures = [
  {
    kicker: "USERS",
    title: "用户管理",
    desc: "维护用户资料、角色与账号状态，先把平台秩序稳住。"
  },
  {
    kicker: "GOODS",
    title: "商品管理",
    desc: "审核和维护商品、分类、上架状态、库存与展示信息。"
  },
  {
    kicker: "ORDERS",
    title: "订单管理",
    desc: "查看平台订单、支付状态和交易备注，处理异常交易数据。"
  },
  {
    kicker: "CONTENT",
    title: "内容管理",
    desc: "集中维护公告和攻略帖子内容，避免用户端信息混乱。"
  }
];

const features = computed(() => (isAdmin.value ? adminFeatures : userFeatures));

onMounted(() => {
  animateIn(
    pageRef.value?.querySelectorAll(
      ".welcome-hero, .feature-card, .role-note"
    ) || []
  );
});
</script>

<style scoped lang="scss">
.welcome-page {
  display: grid;
  gap: 22px;
}

.welcome-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 38px;
  background: var(--market-card-bg);

  h1 {
    max-width: 760px;
    margin: 14px 0 12px;
    font-size: clamp(32px, 5vw, 52px);
    font-weight: 900;
    line-height: 1.08;
  }

  p {
    max-width: 720px;
    color: var(--market-muted);
    font-size: 17px;
    line-height: 1.8;
  }
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.feature-card,
.role-note {
  padding: 22px;
}

.feature-card {
  min-height: 220px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);

  span {
    color: var(--market-orange);
    font-size: 12px;
    font-weight: 900;
  }

  h2 {
    margin: 12px 0;
    font-size: 22px;
    font-weight: 900;
  }

  p {
    color: var(--market-muted);
    line-height: 1.8;
  }
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.role-note {
  h2 {
    margin-bottom: 10px;
    font-size: 24px;
    font-weight: 900;
  }

  p {
    color: var(--market-muted);
    line-height: 1.8;
  }
}

@media (max-width: 980px) {
  .welcome-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .feature-grid,
  .role-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .welcome-hero {
    padding: 28px 20px;
  }

  .feature-grid,
  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
