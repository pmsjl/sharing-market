<template>
  <div class="market-page my-orders-page" ref="pageRef">
    <div class="market-page-header">
      <div>
        <span class="market-eyebrow">ORDER BOARD</span>
        <h1 class="market-title">我的订单</h1>
        <p class="market-subtitle">
          查看待支付、已支付和过期订单，及时完成校园交易确认。
        </p>
      </div>
    </div>

    <section class="market-panel orders-panel">
      <CommodityOrderList
        :commodity-order-list="commodityOrderList"
        @pay="handlePay"
      />

      <div class="market-pagination">
        <el-pagination
          v-model:current-page="queryParams.current"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="fetchCommodityOrders"
          @size-change="fetchCommodityOrders"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import CommodityOrderList from "@/components/CommodityOrderList/index.vue";
import { payCommodityOrderUsingPost } from "@/api/commodityController";
import { listMyCommodityOrderVoByPageUsingPost } from "@/api/commodityOrderController";
import { animateIn } from "@/utils/motion";

const pageRef = ref<HTMLElement | null>(null);
const total = ref(0);
const commodityOrderList = ref<API.CommodityOrderVO[]>([]);
const queryParams = ref({
  current: 1,
  pageSize: 10
});

const fetchCommodityOrders = async () => {
  try {
    const response = await listMyCommodityOrderVoByPageUsingPost(
      queryParams.value
    );
    if (response.data?.records) {
      commodityOrderList.value = response.data.records;
      total.value = Number(response.data.total || 0);
      return;
    }
    commodityOrderList.value = [];
    total.value = 0;
  } catch (error) {
    ElMessage.error("获取订单数据失败");
  }
};

const handlePay = async (orderId: number) => {
  try {
    const response = await payCommodityOrderUsingPost({
      commodityOrderId: orderId
    });

    if (response.code === 200 && response.data === true) {
      ElMessage.success("支付成功");
      await fetchCommodityOrders();
      return;
    }

    if (response.code === 200 && response.data === false) {
      ElMessage.warning("订单已过期，请重新购买");
      await fetchCommodityOrders();
      return;
    }

    ElMessage.error(`支付失败：${response.message || "请稍后重试"}`);
  } catch (error) {
    ElMessage.error("支付失败");
  }
};

onMounted(() => {
  fetchCommodityOrders();
  animateIn(
    pageRef.value?.querySelectorAll(".market-page-header, .orders-panel") || []
  );
});
</script>

<style scoped lang="scss">
.my-orders-page {
  display: grid;
  gap: 18px;
}

.orders-panel {
  padding: 22px;
}
</style>
