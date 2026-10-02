<template>
  <div class="market-listings">
    <div v-if="!commodityList.length" class="listing-empty">
      <img
        src="@/assets/illustrations/empty-stall.svg"
        alt=""
        width="110"
        height="100"
      />
      <h3>还没遇见合适的好物</h3>
      <p>换个关键词或筛选条件试试。</p>
      <slot name="empty-action"></slot>
    </div>
    <div v-else class="listing-grid">
      <article
        v-for="item in commodityList"
        :key="item.id"
        class="listing-item"
      >
        <router-link
          :to="'/user/commodity/detail/' + item.id"
          class="listing-link"
        >
          <div class="listing-cover">
            <img
              v-if="item.commodityAvatar && !failedCovers[item.commodityAvatar]"
              :src="item.commodityAvatar"
              :alt="item.commodityName || '商品图片'"
              loading="lazy"
              decoding="async"
              width="480"
              height="420"
              @error="failedCovers[item.commodityAvatar] = true"
            />
            <div v-else class="listing-placeholder">
              <el-icon><Picture /></el-icon><span>图片暂未就绪</span>
            </div>
            <span v-if="item.degree" class="listing-condition">{{
              item.degree
            }}</span>
          </div>
          <div class="listing-copy">
            <span class="listing-category">{{
              item.commodityTypeName || "校园好物"
            }}</span>
            <h3>{{ item.commodityName || "未命名商品" }}</h3>
            <div class="listing-price">
              <strong>{{ formatPrice(item.price) }}</strong
              ><span>校园币</span
              ><span v-if="item.commodityInventory === 0" class="listing-sold"
                >已售罄</span
              >
            </div>
            <div class="listing-footer">
              <span class="listing-seller"
                ><i aria-hidden="true">{{
                  (item.adminName || "同学").slice(0, 1)
                }}</i
                >{{ item.adminName || "同学" }}</span
              ><span class="listing-saves"
                ><el-icon><Star /></el-icon>{{ item.favourNum || 0
                }}<span class="sr-only">人收藏</span></span
              >
            </div>
          </div>
        </router-link>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, PropType } from "vue";
import { Picture, Star } from "@element-plus/icons-vue";
defineProps({
  commodityList: { type: Array as PropType<API.CommodityVO[]>, required: true }
});
const failedCovers = ref<Record<string, boolean>>({});
const formatPrice = (value?: number) =>
  new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(
    Number(value) || 0
  );
</script>

<style scoped lang="scss">
.listing-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 28px 22px;
}
.listing-item {
  min-width: 0;
}
.listing-link {
  display: block;
  height: 100%;
  border-radius: 14px;
  &:hover .listing-cover > img {
    transform: scale(1.035);
  }
  &:hover h3 {
    color: var(--market-primary);
  }
}
.listing-cover {
  position: relative;
  aspect-ratio: 1.15;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 14px;
  background: var(--market-surface-soft);
  > img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
    transition: transform 240ms ease;
  }
}
.listing-condition {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 3px 9px;
  border-radius: 5px;
  background: var(--market-surface);
  color: var(--market-ink);
  font-size: 10px;
  font-weight: 600;
}
.listing-placeholder {
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--market-muted);
  font-size: 11px;
  .el-icon {
    font-size: 32px;
    opacity: 0.7;
  }
}
.listing-copy {
  padding: 12px 1px 0;
}
.listing-category {
  display: block;
  color: var(--market-muted);
  font-size: 10px;
  margin-bottom: 4px;
}
h3 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--market-ink);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  transition: color 180ms;
}
.listing-price {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 6px;
  strong {
    color: var(--market-ink);
    font-size: 23px;
    font-weight: 750;
    line-height: 1.3;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.6px;
  }
  > span {
    color: var(--market-muted);
    font-size: 10px;
  }
  .listing-sold {
    margin-left: auto;
  }
}
.listing-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding-top: 11px;
  margin-top: 10px;
  border-top: 1px solid var(--market-line);
  color: var(--market-muted);
  font-size: 11px;
}
.listing-seller {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  i {
    display: grid;
    place-items: center;
    flex-shrink: 0;
    width: 21px;
    height: 21px;
    background: var(--market-primary-soft);
    color: var(--market-primary);
    border-radius: 50%;
    font-size: 9px;
    font-style: normal;
  }
}
.listing-saves {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  flex-shrink: 0;
  .el-icon {
    font-size: 13px;
  }
}
.listing-empty {
  display: grid;
  justify-items: center;
  gap: 10px;
  min-height: 280px;
  padding: 32px 16px;
  text-align: center;
  background: var(--market-surface-soft);
  border-radius: 16px;
  img {
    object-fit: contain;
  }
  h3 {
    font-size: 18px;
  }
  p {
    font-size: 13px;
    color: var(--market-muted);
  }
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@media (max-width: 1100px) {
  .listing-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 22px 18px;
  }
}
@media (max-width: 760px) {
  .listing-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 14px;
  }
  .listing-cover {
    aspect-ratio: 1;
    border-radius: 11px;
  }
  .listing-condition {
    top: 8px;
    left: 8px;
    font-size: 9px;
    padding: 2px 6px;
  }
  h3 {
    font-size: 13px;
  }
  .listing-price strong {
    font-size: 21px;
  }
  .listing-footer {
    font-size: 10px;
  }
}
</style>
