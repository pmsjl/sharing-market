<template>
  <section>
    <header class="quiet-heading">
      <h1>我的交易</h1>
      <p>从心动收藏，到好物交接。</p>
    </header>
    <nav class="quiet-tabs" aria-label="交易分类">
      <router-link
        v-for="item in tabs"
        :key="item.value"
        :class="{ active: view === item.value }"
        :to="{ path: route.path, query: { view: item.value } }"
        >{{ item.label }}</router-link
      >
    </nav>
    <router-link
      v-if="isAdmin && view === 'favorites'"
      class="quiet-link"
      to="/admin/commodityManagement"
      >前往商品管理</router-link
    >
    <AsyncState
      :loading="remote.loading.value"
      :error="remote.error.value"
      @retry="remote.load"
    >
      <CommodityOrderList
        v-if="view === 'orders'"
        :commodity-order-list="remote.data.value.records"
        @pay="pay"
      />
      <CommodityList
        v-else-if="view === 'favorites'"
        :commodity-list="remote.data.value.records"
        :linkable="!isAdmin"
      />
      <HeatmapChart
        v-else
        :data="remote.data.value.records"
        :year="String(new Date().getFullYear())"
      />
      <div
        v-if="view !== 'calendar' && remote.data.value.total"
        class="market-pagination"
      >
        <el-pagination
          :current-page="page"
          :page-size="10"
          :total="remote.data.value.total"
          :pager-count="5"
          layout="prev, pager, next"
          @current-change="changePage"
        />
      </div>
    </AsyncState>
    <p v-if="paying" role="status">正在处理支付，请稍候…</p>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import AsyncState from "@/components/AsyncState/index.vue";
import CommodityOrderList from "@/components/CommodityOrderList/index.vue";
import CommodityList from "@/components/CommodityList/index.vue";
import HeatmapChart from "@/components/CalendarChart/index.vue";
import { useRemote, responseData } from "@/composables/useRemote";
import { queryPage } from "@/utils/marketNavigation";
import {
  listMyCommodityOrderVoByPageUsingPost,
  getCommodityOrderHeatmapDataUsingGet
} from "@/api/commodityOrderController";
import { listMyUserCommodityFavoritesVoByPageUsingPost } from "@/api/userCommodityFavoritesController";
import { payCommodityOrderUsingPost } from "@/api/commodityController";
import { GET_ROLE } from "@/utils/token";
const isAdmin = GET_ROLE() === "admin";
const route = useRoute(),
  router = useRouter();
const tabs = [
  { value: "orders", label: "订单" },
  { value: "favorites", label: "收藏商品" },
  { value: "calendar", label: "购物日历" }
];
const view = computed(() =>
  tabs.some((item) => item.value === route.query.view)
    ? String(route.query.view)
    : "orders"
);
const page = computed(() => queryPage(route.query.page));
const remote = useRemote<{ records: any[]; total: number }>(
  { records: [], total: 0 },
  async () => {
    if (view.value === "calendar")
      return {
        records: responseData(
          await getCommodityOrderHeatmapDataUsingGet({ payStatus: 1 })
        ) as any[],
        total: 0
      };
    const params = { current: page.value, pageSize: 10 };
    const result = responseData(
      view.value === "orders"
        ? await listMyCommodityOrderVoByPageUsingPost(params)
        : await listMyUserCommodityFavoritesVoByPageUsingPost({
            ...params,
            status: 1
          })
    );
    return {
      records: (result.records || []).map((item: any) =>
        view.value === "favorites" ? { ...item, id: item.commodityId } : item
      ),
      total: Number(result.total || 0)
    };
  }
);
const changePage = (page: number) =>
  router.push({ query: { ...route.query, page } });
const paying = ref(false);
async function pay(id: string) {
  if (paying.value) return;
  paying.value = true;
  try {
    const result = responseData(
      await payCommodityOrderUsingPost({ commodityOrderId: id })
    );
    if (result) ElMessage.success("支付成功");
    else ElMessage.warning("订单已过期，请重新购买");
    await remote.load();
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "支付失败，请重试"
    );
  } finally {
    paying.value = false;
  }
}
watch([view, page], () => void remote.load(), { immediate: true });
</script>
