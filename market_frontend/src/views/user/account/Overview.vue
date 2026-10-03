<template>
  <section class="account-overview">
    <div class="overview-opening">
      <div class="overview-person">
        <AsyncState
          :loading="profile.loading.value"
          :error="profile.error.value"
          @retry="profile.load"
        >
          <header class="profile-intro">
            <div class="profile-portrait">
              <el-avatar :size="88" :src="profile.data.value.userAvatar">{{
                (profile.data.value.userName || "同学").slice(0, 1)
              }}</el-avatar
              ><span class="portrait-note" aria-hidden="true">好物循环</span>
            </div>
            <div class="profile-copy">
              <p class="profile-caption">我的校园生活</p>
              <h1>{{ profile.data.value.userName || "同学" }}</h1>
              <p class="profile-bio">
                {{
                  profile.data.value.userProfile ||
                  "把喜欢留下，让闲置继续出发。"
                }}
              </p>
            </div>
          </header>
        </AsyncState>
        <nav class="personal-actions" aria-label="个人常用操作">
          <router-link to="/user/account/trade?view=favorites"
            ><el-icon><Star /></el-icon>收藏好物</router-link
          >
          <button type="button" @click="chat.openContact()">
            <el-icon><ChatDotRound /></el-icon>校园私信<span
              v-if="chat.hasUnread"
              class="unread-dot"
              aria-label="有新消息"
            ></span>
          </button>
        </nav>
      </div>
      <aside class="overview-wallet" aria-label="校园币摘要">
        <AsyncState
          :loading="wallet.loading.value"
          :error="wallet.error.value"
          @retry="wallet.load"
        >
          <span class="wallet-label">我的校园币</span>
          <p class="wallet-value">
            {{ formatCampusCoin(wallet.data.value.balance, true)
            }}<small>校园币</small>
          </p>
          <router-link class="wallet-link" to="/user/account/wallet"
            >查看收支<el-icon><ArrowRight /></el-icon
          ></router-link>
        </AsyncState>
      </aside>
    </div>
    <section class="overview-orders" aria-labelledby="recent-orders-title">
      <header class="section-heading">
        <div>
          <h2 id="recent-orders-title">最近的交易</h2>
          <p>好物的新去处，都记在这里。</p>
        </div>
        <router-link to="/user/account/trade?view=orders"
          >全部订单<el-icon><ArrowRight /></el-icon
        ></router-link>
      </header>
      <AsyncState
        :loading="orders.loading.value"
        :error="orders.error.value"
        @retry="orders.load"
      >
        <div v-if="!orders.data.value.length" class="editorial-empty">
          <h3>下一件心动好物，还在路上。</h3>
          <p>有了订单，就会在这里留下记录。</p>
        </div>
        <div v-else class="recent-orders">
          <article
            v-for="order in orders.data.value"
            :key="order.id"
            class="recent-order"
          >
            <div class="recent-order-copy">
              <div class="order-context">
                <span
                  class="order-status"
                  :class="{ pending: order.payStatus === 0 }"
                  >{{
                    order.payStatus === 1
                      ? "已成交"
                      : order.payStatus === 0
                      ? "待支付"
                      : "已过期"
                  }}</span
                ><time :datetime="order.createTime">{{
                  orderDate(order.createTime)
                }}</time>
              </div>
              <h3>{{ order.commodityName || "校园好物" }}</h3>
              <p>数量 {{ order.buyNumber || 1 }}</p>
            </div>
            <p class="recent-order-price">
              {{ formatCampusCoin(order.paymentAmount) }}<small>校园币</small>
            </p>
          </article>
        </div>
      </AsyncState>
    </section>
  </section>
</template>
<script setup lang="ts">
import dayjs from "dayjs";
import { onMounted } from "vue";
import { Star, ChatDotRound, ArrowRight } from "@element-plus/icons-vue";
import AsyncState from "@/components/AsyncState/index.vue";
import { useRemote, responseData } from "@/composables/useRemote";
import { getUserVoByIdUsingGet } from "@/api/userController";
import { getMyCampusCoinWallet } from "@/api/campusCoinController";
import { listMyCommodityOrderVoByPageUsingPost } from "@/api/commodityOrderController";
import { GET_ID } from "@/utils/token";
import { formatCampusCoin } from "@/utils/marketNavigation";
import usePrivateMessageStore from "@/store/modules/privateMessage";
const chat = usePrivateMessageStore();

const profile = useRemote<API.UserVO>({}, async () =>
  responseData(await getUserVoByIdUsingGet({ id: GET_ID() }))
);
const wallet = useRemote({ balance: 0 }, async () =>
  responseData(await getMyCampusCoinWallet())
);
const orders = useRemote<API.CommodityOrderVO[]>(
  [],
  async () =>
    responseData(
      await listMyCommodityOrderVoByPageUsingPost({
        current: 1,
        pageSize: 3,
        sortField: "createTime",
        sortOrder: "desc"
      })
    ).records || []
);
onMounted(() => {
  void profile.load();
  void wallet.load();
  void orders.load();
});

const orderDate = (value?: string) =>
  value ? dayjs(value).format("MM月DD日") : "";
</script>
<style scoped lang="scss">
.overview-opening {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 32px;
  align-items: center;
  padding: 20px 0 48px;
}
.overview-person {
  min-width: 0;
}
.profile-intro {
  display: flex;
  align-items: center;
  gap: 24px;
}
.profile-portrait {
  position: relative;
  flex-shrink: 0;
  padding-bottom: 10px;
  .el-avatar {
    background: var(--market-primary-soft);
    color: var(--market-primary);
    font-size: 32px;
  }
}
.portrait-note {
  position: absolute;
  white-space: nowrap;
  bottom: 0;
  left: 4px;
  padding: 3px 9px;
  background: var(--market-yellow-soft);
  color: var(--market-ink);
  font-size: 12px;
  transform: rotate(-5deg);
}
.profile-copy {
  min-width: 0;
  h1 {
    font-size: clamp(32px, 4vw, 48px);
    font-weight: 750;
    line-height: 1.2;
    letter-spacing: -1.5px;
    margin: 8px 0 12px;
    overflow-wrap: anywhere;
  }
}
.profile-caption {
  margin: 0;
  font-size: 13px;
  color: var(--market-muted);
}
.profile-bio {
  font-size: 15px;
  margin: 0;
  line-height: 1.8;
  max-width: 38ch;
  color: var(--market-muted);
  overflow-wrap: anywhere;
}
.personal-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 26px;
  margin: 24px 0 0 112px;
  a,
  button {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 44px;
    padding: 0;
    color: var(--market-ink);
    background: none;
    border: 0;
    font: inherit;
    font-size: 15px;
    cursor: pointer;
    &:hover {
      color: var(--market-primary);
    }
  }
  .el-icon {
    font-size: 19px;
  }
}
.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--market-primary);
}
.overview-wallet {
  border-left: 1px solid var(--market-line);
  padding: 14px 0 14px 32px;
}
.wallet-label {
  color: var(--market-muted);
  font-size: 13px;
}
.wallet-value {
  font-size: 32px;
  letter-spacing: -1px;
  margin: 12px 0 14px;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
  small {
    display: block;
    font-size: 12px;
    letter-spacing: 0;
    color: var(--market-muted);
    margin-top: 5px;
  }
}
.wallet-link {
  display: inline-flex;
  align-items: center;
  gap: 20px;
  font-size: 13px;
  min-height: 36px;
  color: var(--market-primary);
}
.overview-orders {
  padding-top: 32px;
  border-top: 1px solid var(--market-line);
}
.recent-orders {
  display: grid;
  gap: 12px;
  margin-top: 28px;
}
.recent-order {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  padding: 24px 28px;
  background: var(--market-surface);
  border-radius: 12px;
}
.recent-order-copy {
  min-width: 0;
  h3 {
    font-size: 22px;
    font-weight: 650;
    margin: 9px 0 8px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
  > p {
    font-size: 13px;
    color: var(--market-muted);
    margin: 0;
  }
}
.order-context {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--market-muted);
}
.order-status {
  color: var(--market-muted);
  &.pending {
    color: var(--market-primary);
  }
}
.recent-order-price {
  margin: 0;
  flex-shrink: 0;
  max-width: 45%;
  font-size: 24px;
  font-weight: 600;
  text-align: right;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
  small {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    font-weight: 400;
    color: var(--market-muted);
  }
}
@media (max-width: 1000px) {
  .overview-opening {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
  }
  .overview-wallet {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    column-gap: 18px;
    border-left: 0;
    padding: 20px 24px;
    border-radius: 12px;
    background: var(--market-primary-soft);
  }
  .wallet-value {
    grid-column: 1;
    display: inline-flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 28px;
    margin: 8px 0;
    small {
      margin: 0;
    }
  }
  .wallet-label {
    display: block;
    grid-column: 1;
  }
  .wallet-link {
    grid-column: 2;
    grid-row: 1 / 3;
    align-self: center;
    display: flex;
    width: fit-content;
    gap: 8px;
  }
  .overview-wallet :deep(.quiet-state) {
    grid-column: 1 / -1;
  }
}
@media (max-width: 760px) {
  .overview-opening {
    padding: 12px 0 28px;
  }
  .profile-intro {
    align-items: flex-start;
    gap: 18px;
  }
  .profile-portrait .el-avatar {
    width: 72px;
    height: 72px;
    font-size: 27px;
  }
  .portrait-note {
    left: 0;
    font-size: 11px;
  }
  .profile-copy h1 {
    font-size: 32px;
    margin: 5px 0 8px;
  }
  .personal-actions {
    margin-left: 0;
    gap: 28px;
    margin-top: 20px;
  }
  .overview-wallet {
    padding: 18px 22px;
  }
  .overview-orders {
    padding-top: 26px;
  }
  .recent-orders {
    margin-top: 20px;
  }
  .recent-order {
    padding: 20px;
    gap: 18px;
  }
  .recent-order-copy h3 {
    font-size: 19px;
  }
  .recent-order-price {
    font-size: 22px;
  }
}
</style>
