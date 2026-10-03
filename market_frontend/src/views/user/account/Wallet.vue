<template>
  <section>
    <header class="quiet-heading">
      <h1>校园币</h1>
      <p>站内模拟币，用于校园集市交易。</p>
    </header>
    <AsyncState
      :loading="wallet.loading.value"
      :error="wallet.error.value"
      @retry="wallet.load"
      ><div class="wallet-balance">
        <span>可用余额</span
        ><strong
          >{{ formatCampusCoin(wallet.data.value.balance, true) }}
          <small>校园币</small></strong
        >
      </div></AsyncState
    >
    <h2 class="ledger-title">收支记录</h2>
    <AsyncState
      :loading="ledger.loading.value"
      :error="ledger.error.value"
      @retry="ledger.load"
    >
      <el-table
        class="ledger-table"
        :data="ledger.data.value.records"
        empty-text="暂无收支记录"
      >
        <el-table-column label="时间" prop="createTime" min-width="170" />
        <el-table-column label="类型" min-width="120"
          ><template #default="{ row }">{{
            typeLabel(row.transactionType)
          }}</template></el-table-column
        >
        <el-table-column label="变动（校园币）" min-width="150"
          ><template #default="{ row }"
            ><span :class="{ income: row.amount >= 0 }">{{
              signed(row.amount)
            }}</span></template
          ></el-table-column
        >
        <el-table-column label="余额（校园币）" min-width="150"
          ><template #default="{ row }">{{
            formatCampusCoin(row.balanceAfter, true)
          }}</template></el-table-column
        >
        <el-table-column label="说明" prop="remark" min-width="180" />
      </el-table>
      <div class="ledger-mobile">
        <p v-if="!groups.length" class="quiet-empty">暂无收支记录</p>
        <section v-for="group in groups" :key="group.date">
          <h3>{{ group.date }}</h3>
          <article v-for="row in group.items" :key="row.id">
            <div>
              <strong>{{ typeLabel(row.transactionType) }}</strong>
              <p>{{ row.remark || "校园集市" }}</p>
              <small>{{ row.createTime.slice(11, 16) }}</small>
            </div>
            <div class="ledger-amount">
              <strong :class="{ income: row.amount >= 0 }"
                >{{ signed(row.amount) }} 校园币</strong
              ><small
                >余额
                {{ formatCampusCoin(row.balanceAfter, true) }} 校园币</small
              >
            </div>
          </article>
        </section>
      </div>
      <div v-if="ledger.data.value.total" class="market-pagination">
        <el-pagination
          :current-page="page"
          :page-size="10"
          :total="ledger.data.value.total"
          :pager-count="5"
          layout="prev, pager, next"
          @current-change="changePage"
        />
      </div>
    </AsyncState>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AsyncState from "@/components/AsyncState/index.vue";
import { useRemote, responseData } from "@/composables/useRemote";
import { formatCampusCoin, queryPage } from "@/utils/marketNavigation";
import {
  getMyCampusCoinWallet,
  listMyCampusCoinTransactions,
  CampusCoinTransactionVO
} from "@/api/campusCoinController";
const route = useRoute(),
  router = useRouter();
const page = computed(() => queryPage(route.query.page));
const wallet = useRemote({ balance: 0 }, async () =>
  responseData(await getMyCampusCoinWallet())
);
const ledger = useRemote<{ records: CampusCoinTransactionVO[]; total: number }>(
  { records: [], total: 0 },
  async () =>
    responseData(
      await listMyCampusCoinTransactions({ current: page.value, pageSize: 10 })
    )
);
const groups = computed(() => {
  const result: { date: string; items: CampusCoinTransactionVO[] }[] = [];
  for (const item of ledger.data.value.records) {
    const date = item.createTime.slice(0, 10);
    let group = result.find((group) => group.date === date);
    if (!group) {
      group = { date, items: [] };
      result.push(group);
    }
    group.items.push(item);
  }
  return result;
});
const typeLabel = (value: string) =>
  ({
    REGISTER_GRANT: "注册赠送",
    OPENING_BALANCE: "期初余额",
    ADMIN_GRANT: "管理员发放",
    PURCHASE: "购买商品"
  }[value] || value);
const signed = (value: number) =>
  `${value >= 0 ? "+" : ""}${formatCampusCoin(value, true)}`;
const changePage = (page: number) =>
  router.push({ query: { ...route.query, page } });
onMounted(() => void wallet.load());
watch(page, () => void ledger.load(), { immediate: true });
</script>
<style scoped lang="scss">
.wallet-balance {
  padding: 28px 32px;
  border-radius: 16px;
  background: var(--market-primary-soft);
  strong {
    overflow-wrap: anywhere;
  }
  span {
    color: var(--market-muted);
  }
  strong {
    display: block;
    font-size: clamp(30px, 5vw, 44px);
    margin-top: 14px;
    font-variant-numeric: tabular-nums;
  }
  small {
    font-size: 14px;
    font-weight: 400;
  }
}
.ledger-title {
  font-size: 24px;
  margin: 42px 0 24px;
  font-weight: 700;
}
.income {
  color: var(--el-color-success);
}
.ledger-mobile {
  display: none;
  section + section {
    margin-top: 28px;
  }
  h3 {
    font-size: 13px;
    color: var(--market-muted);
    margin-top: 24px;
  }
  article {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 20px 0;
    font-size: 14px;
    > div:first-child {
      min-width: 0;
    }
    p {
      margin: 6px 0;
      overflow-wrap: anywhere;
    }
    small {
      display: block;
      margin-top: 8px;
      color: var(--market-muted);
    }
  }
}
.ledger-amount {
  text-align: right;
  flex-shrink: 0;
  max-width: 55%;
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .wallet-balance {
    padding: 24px;
  }
  .ledger-table {
    display: none;
  }
  .ledger-mobile {
    display: block;
  }
}
</style>
