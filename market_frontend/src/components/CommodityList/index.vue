<template>
  <div class="commodity-list">
    <el-empty
      v-if="!props.commodityList.length"
      description="公告栏暂时还没有商品"
    />
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
        <div class="image-wrap">
          <img
            v-if="item.commodityAvatar"
            :src="item.commodityAvatar"
            :alt="item.commodityName"
          />
          <div v-else class="image-placeholder">校园好物</div>
          <span class="price">￥{{ item.price || 0 }}</span>
        </div>
        <div class="content">
          <div class="title">{{ item.commodityName }}</div>
          <p class="description">
            {{ item.commodityDescription || "暂无简介" }}
          </p>
          <div class="tags">
            <el-tag type="info">{{ item.degree || "成色未知" }}</el-tag>
            <el-tag type="success"
              >库存 {{ item.commodityInventory ?? 0 }}</el-tag
            >
            <el-tag type="primary">{{
              item.commodityTypeName || "未分类"
            }}</el-tag>
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
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { View, Star } from "@element-plus/icons-vue";

const $router = useRouter();
const props = defineProps({
  commodityList: {
    type: Array as any,
    required: true
  }
});

const goCommodityDetail = (id: number) => {
  $router.push("/user/commodity/detail/" + id);
};
</script>

<style scoped lang="scss">
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

  &::before {
    position: absolute;
    top: 8px;
    left: 50%;
    width: 22px;
    height: 22px;
    border: 5px solid rgba(255, 255, 255, 0.74);
    border-radius: 50%;
    background: var(--market-orange);
    content: "";
    transform: translateX(-50%);
    z-index: 1;
  }

  &:hover {
    box-shadow: var(--market-shadow);
    transform: translateY(-4px) rotate(-0.3deg);
  }
}

.image-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #f1dec0;

  img,
  .image-placeholder {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.image-placeholder {
  display: grid;
  place-items: center;
  color: rgba(35, 49, 63, 0.58);
  font-size: 18px;
  font-weight: 900;
}

.price {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 6px 10px;
  border-radius: 999px;
  color: #fff;
  font-weight: 900;
  background: var(--market-orange);
  box-shadow: var(--market-shadow-soft);
}

.content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
}

.title {
  color: var(--market-ink);
  font-size: 19px;
  font-weight: 900;
  line-height: 1.35;
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

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
</style>
