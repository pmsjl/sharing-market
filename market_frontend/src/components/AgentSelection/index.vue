<template>
  <section class="selection-sheet" aria-labelledby="selection-title">
    <header class="selection-heading">
      <div>
        <h2 id="selection-title">
          本轮好物 <small>{{ recommendations.length }}</small>
        </h2>
      </div>
      <button type="button" aria-label="关闭选物清单" @click="$emit('close')">
        ×
      </button>
    </header>
    <div class="selection-context">
      <span>来自 {{ timeLabel }} 的回答</span>
      <p>{{ prompt || "这轮咨询的商品建议" }}</p>
      <button type="button" @click="$emit('return-to-answer')">
        回到这条回答 ↗
      </button>
    </div>
    <div class="selection-items">
      <article
        v-for="item in recommendations"
        :key="item.commodity.id"
        class="selection-item"
      >
        <button
          type="button"
          class="selection-product"
          @click="$emit('open-commodity', item.commodity.id)"
        >
          <img
            v-if="
              item.commodity.commodityAvatar &&
              !failedImages[item.commodity.commodityAvatar]
            "
            :src="item.commodity.commodityAvatar"
            :alt="item.commodity.commodityName"
            loading="lazy"
            @error="failedImages[item.commodity.commodityAvatar] = true"
          />
          <span v-else class="selection-placeholder">好物</span>
          <span class="selection-product-copy"
            ><small>{{ item.commodity.degree || "成色待确认" }}</small
            ><strong>{{ item.commodity.commodityName }}</strong
            ><span
              ><b>{{ formatCampusCoin(item.commodity.price) }}</b> 校园币</span
            ></span
          >
          <span class="selection-arrow" aria-hidden="true">↗</span>
        </button>
        <p v-if="item.reason" class="selection-reason">{{ item.reason }}</p>
        <p v-if="item.riskTip" class="selection-risk">
          <span>验货提醒</span>{{ item.riskTip }}
        </p>
      </article>
    </div>
    <p class="selection-note">商品信息可能变化，以商品详情页为准。</p>
  </section>
</template>

<script setup lang="ts">
import { formatCampusCoin } from "@/utils/marketNavigation";
import { computed, ref } from "vue";
import type { AiMessageVO } from "@/api/aiController";
const props = defineProps<{ message: AiMessageVO; prompt: string }>();
defineEmits<{
  (event: "close"): void;
  (event: "return-to-answer"): void;
  (event: "open-commodity", id: string): void;
}>();
const failedImages = ref<Record<string, boolean>>({});
const recommendations = computed(
  () => props.message.structuredContent?.recommendations || []
);
const timeLabel = computed(
  () => props.message.createTime?.replace("T", " ").slice(0, 16) || "当前咨询"
);
</script>

<style scoped lang="scss">
.selection-sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  color: var(--market-ink);
}
.selection-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20px;
  > div > span {
    display: block;
    font-size: var(--market-meta-size, 13px);
    letter-spacing: 1px;
    color: var(--market-muted);
  }
  h2 {
    margin-top: 6px;
    font-size: 23px;
    font-weight: 700;
  }
  small {
    margin-left: 8px;
    color: var(--market-muted);
    font-size: 13px;
    font-weight: 400;
  }
  > button {
    width: 40px;
    height: 40px;
    border: 0;
    background: transparent;
    color: var(--market-muted);
    font-size: 26px;
    cursor: pointer;
  }
}
.selection-context {
  padding: 0 0 18px;
  border-bottom: 1px solid var(--market-line);
  font-size: var(--market-meta-size, 13px);
  > span {
    font-size: var(--market-meta-size, 13px);
    color: var(--market-muted);
  }
  p {
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    margin: 8px 0 3px;
    line-height: 1.65;
    overflow-wrap: anywhere;
  }
  button {
    min-height: 36px;
    border: 0;
    padding: 0;
    color: var(--market-primary);
    background: transparent;
    font-size: var(--market-meta-size, 13px);
    cursor: pointer;
  }
}
.selection-items {
  overflow-y: auto;
  min-height: 0;
  flex: 1;
  scrollbar-width: thin;
}
.selection-item {
  padding: 22px 0;
  border-bottom: 1px solid var(--market-line);
}
.selection-product {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 13px;
  padding: 0;
  border: 0;
  text-align: left;
  color: var(--market-ink);
  background: transparent;
  cursor: pointer;
  img,
  .selection-placeholder {
    width: 80px;
    height: 88px;
    object-fit: contain;
    background: var(--market-surface-soft);
    border-radius: 6px;
    flex-shrink: 0;
  }
  &:hover strong {
    color: var(--market-primary);
  }
}
.selection-placeholder {
  display: grid;
  place-items: center;
  color: var(--market-muted);
  font-size: 13px;
}
.selection-product-copy {
  display: grid;
  min-width: 0;
  gap: 5px;
  flex: 1;
  small {
    font-size: var(--market-meta-size, 13px);
    color: var(--market-muted);
  }
  strong {
    font-size: 14px;
    font-weight: 600;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
  > span {
    font-size: var(--market-meta-size, 13px);
    color: var(--market-muted);
  }
  b {
    color: var(--market-ink);
    font-size: 19px;
    font-weight: 700;
  }
}
.selection-arrow {
  font-size: 17px;
  color: var(--market-muted);
}
.selection-reason {
  margin-top: 14px;
  font-size: var(--market-meta-size, 13px);
  line-height: 1.85;
  color: var(--market-muted);
}
.selection-risk {
  display: grid;
  gap: 3px;
  margin-top: 10px;
  padding-left: 10px;
  border-left: 2px solid var(--market-warning);
  color: var(--market-muted);
  font-size: var(--market-meta-size, 13px);
  line-height: 1.75;
  > span {
    color: var(--market-warning);
    font-weight: 600;
  }
}
.selection-note {
  padding-top: 16px;
  color: var(--market-muted);
  font-size: var(--market-meta-size, 13px);
}
</style>
