<template>
  <div class="order-list-container">
    <el-empty v-if="!props.commodityOrderList.length" description="暂无订单" />

    <article
      v-for="order in props.commodityOrderList"
      :key="order.id"
      class="order-item"
      :class="`status-${order.payStatus}`"
    >
      <i class="ticket-punch" aria-hidden="true"></i>
      <i class="order-stamp" :class="`stamp-${order.payStatus}`">{{
        getPayStatusText(order.payStatus)
      }}</i>
      <div class="order-header">
        <div>
          <span class="order-kicker">ORDER #{{ order.id }}</span>
          <h3>{{ order.commodityName || "未命名商品" }}</h3>
        </div>
      </div>

      <div class="order-body">
        <div class="order-field">
          <span class="field-label">购买数量</span>
          <span class="field-value">{{ order.buyNumber }}</span>
        </div>
        <div class="order-field">
          <span class="field-label">支付金额</span>
          <span class="field-value price">￥{{ order.paymentAmount }}</span>
        </div>
        <div class="order-field">
          <span class="field-label">联系人</span>
          <span class="field-value">{{ order.userName || "-" }}</span>
        </div>
        <div class="order-field">
          <span class="field-label">联系电话</span>
          <span class="field-value">{{ order.userPhone || "-" }}</span>
        </div>
        <div class="order-field">
          <span class="field-label">创建时间</span>
          <span class="field-value">{{ formatTime(order.createTime) }}</span>
        </div>
        <div class="order-field" v-if="order.payStatus === 0">
          <span class="field-label">剩余支付</span>
          <span class="field-value countdown">
            {{ remainingTimes[order.id] || "计算中..." }}
          </span>
        </div>
      </div>

      <div v-if="order.payStatus === 0" class="order-footer">
        <el-button type="warning" @click="showPayDialog(order)">
          <el-icon style="margin-right: 4px"><Scissor /></el-icon>
          撕下副券 · 立即支付
        </el-button>
      </div>
    </article>

    <el-dialog v-model="dialogVisible" title="支付订单" width="420px">
      <div class="dialog-content">
        <p><span>订单号</span>{{ currentOrder?.id }}</p>
        <p><span>商品</span>{{ currentOrder?.commodityName }}</p>
        <p><span>金额</span>￥{{ currentOrder?.paymentAmount }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPay">确定支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from "vue";
import { Scissor } from "@element-plus/icons-vue";
import dayjs from "dayjs";

type CommodityOrderItem = API.CommodityOrderVO & {
  id?: string;
  payStatus?: number;
  createTime?: string;
};

const props = defineProps<{
  commodityOrderList: CommodityOrderItem[];
}>();

const emit = defineEmits<{
  (event: "pay", orderId: string): void;
}>();

const dialogVisible = ref(false);
const currentOrder = ref<CommodityOrderItem | null>(null);
const remainingTimes = ref<Record<string, string>>({});

const showPayDialog = (order: CommodityOrderItem) => {
  currentOrder.value = order;
  dialogVisible.value = true;
};

const confirmPay = () => {
  if (currentOrder.value?.id) {
    emit("pay", currentOrder.value.id);
    dialogVisible.value = false;
  }
};

const formatTime = (time?: string) => {
  return time ? dayjs(time).format("YYYY-MM-DD HH:mm") : "未知时间";
};

const getPayStatusText = (payStatus?: number) => {
  switch (payStatus) {
    case 1:
      return "已成交";
    case 0:
      return "待支付";
    case 2:
      return "已过期";
    default:
      return "未知状态";
  }
};

const getRemainingTime = (createTime?: string) => {
  if (!createTime) {
    return "未知时间";
  }
  const expireTime = dayjs(createTime).add(15, "minute");
  const diff = expireTime.diff(dayjs(), "second");

  if (diff <= 0) {
    return "订单已过期";
  }

  const minutes = Math.floor(diff / 60);
  const seconds = diff % 60;
  return `${minutes} 分 ${seconds} 秒`;
};

const updateRemainingTimes = () => {
  props.commodityOrderList.forEach((order) => {
    if (order.payStatus === 0 && order.id) {
      remainingTimes.value[order.id] = getRemainingTime(order.createTime);
    }
  });
};

let timer: number | null = null;

onMounted(() => {
  timer = window.setInterval(updateRemainingTimes, 1000);
});

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});

watch(
  () => props.commodityOrderList,
  () => {
    updateRemainingTimes();
  },
  { immediate: true, deep: true }
);
</script>

<style scoped lang="scss">
.order-list-container {
  display: grid;
  gap: 16px;
}

// 票根订单卡：左右半圆缺口 + 打孔
.order-item {
  position: relative;
  padding: 18px 26px;
  border: 1px solid var(--market-line);
  border-radius: var(--market-radius-ticket);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  @include ticket-notch(var(--market-paper));
}

// 左侧打孔
.ticket-punch {
  position: absolute;
  top: 50%;
  left: 9px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--market-line);
  border-radius: 50%;
  background: var(--market-body-bg);
  transform: translateY(-50%);
}

// 支付状态印章
.order-stamp {
  position: absolute;
  top: 16px;
  right: 20px;
  font-size: 14px;
  font-style: normal;

  &.stamp-1 {
    @include stamp-text(var(--market-green));
  }

  &.stamp-0 {
    @include stamp-text(var(--market-orange));
  }

  &.stamp-2 {
    @include stamp-text(var(--market-muted));
    opacity: 0.6;
  }
}

.order-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-right: 96px;
  padding-bottom: 14px;
  border-bottom: 1px dashed rgba(35, 49, 63, 0.14);

  h3 {
    margin-top: 5px;
    font-family: var(--market-font-display);
    font-size: 20px;
    font-weight: 900;
  }
}

.order-kicker {
  color: var(--market-orange);
  font-family: var(--market-font-mono);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 1px;
}

.order-body {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding-top: 16px;
}

.order-field {
  display: grid;
  gap: 5px;
}

.field-label {
  color: var(--market-muted);
  font-size: 12px;
  font-weight: 900;
}

.field-value {
  color: var(--market-ink);
  font-weight: 800;
  line-height: 1.45;
}

.price,
.countdown {
  color: var(--market-orange);
  font-family: var(--market-font-mono);
}

// 副券撕线 + 支付按钮
.order-footer {
  display: flex;
  justify-content: flex-end;
  margin: 16px -26px -18px;
  padding: 14px 26px 16px;
  border-top: 2px dashed rgba(224, 101, 31, 0.35);
}

.dialog-content {
  display: grid;
  gap: 12px;

  p {
    display: grid;
    grid-template-columns: 86px minmax(0, 1fr);
    gap: 12px;
    color: var(--market-ink);
    font-weight: 800;
  }

  span {
    color: var(--market-muted);
  }
}

@media (max-width: 760px) {
  .order-body {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .order-header {
    flex-direction: column;
    padding-right: 0;
  }

  .order-stamp {
    position: static;
    margin-top: 8px;
    width: fit-content;
  }

  .order-body {
    grid-template-columns: 1fr;
  }

  .order-footer .el-button {
    width: 100%;
  }
}
</style>
