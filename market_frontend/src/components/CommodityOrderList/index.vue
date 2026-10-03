<template>
  <div class="order-list-container">
    <div v-if="!props.commodityOrderList.length" class="editorial-empty">
      <h3>还没有交易记录</h3>
      <p>遇见喜欢的好物后，订单会保存在这里。</p>
    </div>
    <article
      v-for="order in props.commodityOrderList"
      :key="order.id"
      class="order-item"
      :class="`status-${order.payStatus}`"
    >
      <div class="order-main">
        <div class="order-copy">
          <div class="order-context">
            <span
              class="order-state"
              :class="{ pending: order.payStatus === 0 }"
              >{{ getPayStatusText(order.payStatus) }}</span
            ><time>{{ formatTime(order.createTime) }}</time>
          </div>
          <h3>{{ order.commodityName || "未命名商品" }}</h3>
          <p class="order-quantity">购买数量 {{ order.buyNumber }}</p>
        </div>
        <p class="order-price">
          {{ formatCampusCoin(order.paymentAmount) }}<small>校园币</small>
        </p>
      </div>
      <div class="order-bottom">
        <details class="order-details">
          <summary>订单信息</summary>
          <dl>
            <div>
              <dt>订单号</dt>
              <dd>{{ order.id }}</dd>
            </div>
            <div>
              <dt>联系人</dt>
              <dd>{{ order.userName || "未填写" }}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd>{{ order.userPhone || "未填写" }}</dd>
            </div>
            <div v-if="order.remark">
              <dt>备注</dt>
              <dd>{{ order.remark }}</dd>
            </div>
          </dl>
        </details>
        <div v-if="order.payStatus === 0" class="order-payment">
          <span>{{ remainingTimes[order.id] || "计算中…" }}</span
          ><el-button type="primary" @click="showPayDialog(order)"
            >立即支付</el-button
          >
        </div>
      </div>
    </article>
    <el-dialog
      append-to-body
      v-model="dialogVisible"
      title="支付订单"
      width="420px"
    >
      <div class="dialog-content">
        <p><span>订单号</span>{{ currentOrder?.id }}</p>
        <p><span>商品</span>{{ currentOrder?.commodityName }}</p>
        <p>
          <span>金额</span
          >{{ formatCampusCoin(currentOrder?.paymentAmount) }} 校园币
        </p>
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
import dayjs from "dayjs";
import { formatCampusCoin } from "@/utils/marketNavigation";

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
  gap: 20px;
}
.order-item {
  min-width: 0;
  padding: 28px;
  background: var(--market-surface);
  border-radius: 14px;
}
.order-main {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
}
.order-copy {
  min-width: 0;
  h3 {
    font-size: 24px;
    line-height: 1.5;
    margin: 12px 0 8px;
    font-weight: 650;
    overflow-wrap: anywhere;
  }
}
.order-context {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--market-muted);
}
.order-state.pending {
  color: var(--market-primary);
}
.order-quantity {
  font-size: 13px;
  color: var(--market-muted);
  margin: 0;
}
.order-price {
  font-size: 28px;
  margin: 0;
  text-align: right;
  flex-shrink: 0;
  max-width: 45%;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  small {
    display: block;
    font-size: 12px;
    font-weight: 400;
    color: var(--market-muted);
    margin-top: 5px;
  }
}
.order-bottom {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-top: 22px;
}
.order-details {
  min-width: 0;
  summary {
    cursor: pointer;
    color: var(--market-muted);
    font-size: 13px;
    padding: 10px 0;
    min-height: 44px;
  }
  dl {
    margin: 8px 0 0;
    display: grid;
    gap: 12px;
    font-size: 13px;
  }
  dl > div {
    display: grid;
    grid-template-columns: 70px minmax(0, 1fr);
    gap: 10px;
  }
  dt {
    color: var(--market-muted);
  }
  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }
}
.order-payment {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  > span {
    color: var(--market-muted);
    font-size: 12px;
  }
}
.dialog-content p {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin: 18px 0;
  overflow-wrap: anywhere;
  span {
    color: var(--market-muted);
    flex-shrink: 0;
  }
}
@media (max-width: 760px) {
  .order-item {
    padding: 22px 20px;
  }
  .order-copy h3 {
    font-size: 20px;
  }
  .order-price {
    font-size: 24px;
  }
  .order-context {
    gap: 6px 12px;
  }
  .order-bottom {
    flex-wrap: wrap;
    gap: 8px;
  }
  .order-payment {
    justify-content: space-between;
    flex: 1 0 100%;
  }
}
</style>
