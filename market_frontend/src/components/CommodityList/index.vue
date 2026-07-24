<template>
  <div class="commodity-list">
    <div v-if="!props.commodityList.length" class="empty-stall">
      <img
        src="@/assets/illustrations/empty-stall.svg"
        alt="空摊位"
        class="empty-illustration"
      />
      <p class="empty-title">公告栏暂时还没有商品</p>
      <p class="empty-desc">换个筛选条件试试，或者成为第一个摆摊的人</p>
    </div>
    <div v-else class="commodity-grid">
      <article
        v-for="item in props.commodityList"
        :key="item.id"
        class="commodity-note"
        role="button"
        tabindex="0"
        @click="goCommodityDetail(item.id)"
        @keydown.enter="goCommodityDetail(item.id)"
      >
        <div class="stall-awning" aria-hidden="true"></div>
        <div class="image-wrap">
          <img
            v-if="item.commodityAvatar"
            :src="item.commodityAvatar"
            :alt="item.commodityName"
          />
          <div v-else class="image-placeholder">校园好物</div>
          <span class="price-tag">
            <i class="price-hole" aria-hidden="true"></i>
            ￥{{ item.price || 0 }}
          </span>
        </div>
        <div class="content">
          <div class="title">{{ item.commodityName }}</div>
          <p class="description">
            {{ item.commodityDescription || "暂无简介" }}
          </p>
          <div class="meta-row">
            <span class="meta-item">
              <el-icon><Collection /></el-icon>
              {{ item.degree || "成色未知" }}
            </span>
            <span class="meta-item">
              <el-icon><Box /></el-icon>
              余 {{ item.commodityInventory ?? 0 }}
            </span>
            <span class="meta-item meta-type">
              {{ item.commodityTypeName || "未分类" }}
            </span>
          </div>
          <div class="stats">
            <span>
              <el-icon><View /></el-icon>
              {{ item.viewNum || 0 }}
            </span>
            <span>
              <el-icon><Star /></el-icon>
              {{ item.favourNum || 0 }}
            </span>
          </div>
          <div class="seller-row" v-if="item.adminName">
            <span>卖家 {{ item.adminName || "同学" }}</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { Box, Collection, View, Star } from "@element-plus/icons-vue";

const $router = useRouter();
const props = defineProps({
  commodityList: {
    type: Array as any,
    required: true
  }
});

const goCommodityDetail = (id?: string) => {
  if (!id) return;
  $router.push("/user/commodity/detail/" + id);
};
</script>

<style scoped lang="scss">
.empty-stall {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 48px 20px;
  border: 1px dashed var(--market-line);
  border-radius: var(--market-radius);
  background: var(--market-surface);

  .empty-illustration {
    width: 180px;
    color: var(--market-muted);
    opacity: 0.9;
  }

  .empty-title {
    color: var(--market-ink);
    font-family: var(--market-font-display);
    font-size: 20px;
    font-weight: 900;
  }

  .empty-desc {
    color: var(--market-muted);
    font-size: 14px;
  }
}

.commodity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}

.commodity-note {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 358px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    box-shadow: var(--market-shadow-lift);
    transform: translateY(-4px) rotate(-0.3deg);
  }
}

// 摊位雨棚
.stall-awning {
  flex: 0 0 10px;
  @include awning-strip(10px);
}

.image-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 5;
  background: var(--market-paper-deep);
  overflow: hidden;

  img,
  .image-placeholder {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    object-position: center;
  }
}

.image-placeholder {
  display: grid;
  place-items: center;
  color: var(--market-muted);
  font-family: var(--market-font-display);
  font-size: 18px;
  font-weight: 900;
}

// 价格吊牌：旋转 + 打孔
.price-tag {
  position: absolute;
  top: 12px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px 6px 8px;
  border-radius: 6px;
  color: #fff;
  font-family: var(--market-font-mono);
  font-size: 16px;
  font-weight: 900;
  background: var(--market-orange);
  box-shadow: var(--market-shadow-soft);
  transform: rotate(4deg);
  transition: transform var(--market-dur-fast) var(--market-ease-spring);

  .price-hole {
    width: 8px;
    height: 8px;
    border: 2px solid rgba(255, 255, 255, 0.85);
    border-radius: 50%;
    background: rgba(62, 45, 24, 0.35);
  }
}

.commodity-note:hover .price-tag {
  transform: rotate(0deg) scale(1.04);
}

.content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px 18px;
}

.title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--market-ink);
  font-family: var(--market-font-display);
  font-size: 19px;
  font-weight: 900;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.description {
  display: -webkit-box;
  min-height: 44px;
  overflow: hidden;
  color: var(--market-muted);
  font-size: 14px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

// 图标元信息行：替代三个并排 tag
.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  color: var(--market-muted);
  font-size: 13px;
  font-weight: 700;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;

  .el-icon {
    color: var(--market-green);
  }
}

.meta-type {
  margin-left: auto;
  padding: 2px 9px;
  border: 1px solid rgba(43, 110, 80, 0.3);
  border-radius: 999px;
  color: var(--market-green);
  font-size: 12px;
  background: var(--market-note-green-bg);
}

.stats {
  display: flex;
  gap: 14px;
  margin-top: auto;
  color: var(--market-muted);
  font-size: 14px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
}

.seller-row {
  display: flex;
  align-items: center;
  margin-top: 2px;
  padding-top: 10px;
  border-top: 1px dashed var(--market-line);
  color: var(--market-muted);
  font-size: 13px;
  font-weight: 800;
}
</style>
