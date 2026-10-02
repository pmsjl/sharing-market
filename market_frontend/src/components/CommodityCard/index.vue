<template>
  <div class="market-page commodity-detail" ref="pageRef">
    <div v-if="isAgentEntry" class="agent-return-bar">
      <el-button :icon="ArrowLeft" plain @click="returnToAgent">
        返回智能导购
      </el-button>
      <span>继续查看刚才的咨询与推荐理由</span>
    </div>

    <div v-if="detailLoading" class="detail-loading" role="status">
      <el-skeleton :rows="7" animated />
    </div>
    <div v-else-if="detailFailed" class="detail-loading" role="status">
      <p>商品暂时没有加载出来。</p>
      <el-button @click="fetchCommodityDetail">重新加载</el-button>
    </div>
    <template v-else>
      <section class="item-feature">
        <div class="item-visual">
          <img
            v-if="commodity.commodityAvatar && !coverFailed"
            :src="commodity.commodityAvatar"
            :alt="commodity.commodityName"
            @error="coverFailed = true"
          />
          <div v-else class="item-placeholder">
            <el-icon><Picture /></el-icon><span>商品图片暂未就绪</span>
          </div>
          <span class="item-sticker" aria-hidden="true"
            >好物<br />继续发光</span
          >
        </div>
        <div class="item-story">
          <span class="item-category">{{
            commodity.commodityTypeName || "校园好物"
          }}</span>
          <h1>{{ commodity.commodityName || "商品详情" }}</h1>
          <div class="item-facts">
            <span>{{ commodity.degree || "成色待确认" }}</span
            ><span
              class="item-availability"
              :class="{
                unavailable:
                  commodity.isListed !== 1 || commodity.commodityInventory <= 0
              }"
              ><i aria-hidden="true"></i
              >{{
                commodity.isListed !== 1
                  ? "未上架"
                  : commodity.commodityInventory <= 0
                  ? "已售罄"
                  : "在售"
              }}</span
            ><span>剩余 {{ commodity.commodityInventory }} 件</span>
          </div>
          <div class="item-price">
            <strong>{{ commodity.price }}</strong
            ><span>校园币</span>
          </div>
          <p class="item-price-note">站内模拟币交易</p>
          <div class="item-seller">
            <span class="seller-initial" aria-hidden="true">{{
              (commodity.adminName || "同学").slice(0, 1)
            }}</span>
            <div>
              <small>这件好物的主人</small
              ><strong>{{ commodity.adminName || "同学" }}</strong>
            </div>
          </div>
          <div class="item-purchase">
            <el-button
              type="primary"
              :disabled="
                commodity.isListed !== 1 || commodity.commodityInventory <= 0
              "
              @click="handleBuy"
              :icon="Coin"
              >{{
                commodity.isListed !== 1
                  ? "暂不可购买"
                  : commodity.commodityInventory <= 0
                  ? "已售罄"
                  : "购买商品"
              }}</el-button
            ><el-button
              v-if="canContactSeller"
              class="seller-contact"
              text
              @click="handleContactSeller"
              >联系卖家 <el-icon><ArrowRight /></el-icon
            ></el-button>
          </div>
          <div class="item-social">
            <button
              type="button"
              :aria-pressed="initStatus === 1"
              :class="{ collected: initStatus === 1 }"
              @click="handleCollect"
            >
              <el-icon
                ><StarFilled v-if="initStatus === 1" /><Star v-else /></el-icon
              >{{ initStatus === 1 ? "已收藏" : "收藏" }}
              {{ favourCount }}</button
            ><button type="button" @click="handleShare">
              <el-icon><Share /></el-icon>分享</button
            ><span class="item-views"
              ><el-icon><View /></el-icon>{{ viewCount }} 次浏览</span
            >
          </div>
        </div>
      </section>
      <section class="item-details" aria-label="商品说明与评价">
        <el-tabs v-model="detailActiveName"
          ><el-tab-pane label="关于这件好物" name="first"
            ><p class="description-text">
              {{
                commodity.commodityDescription ||
                "卖家暂未填写商品详情，可以联系卖家了解。"
              }}
            </p></el-tab-pane
          ><el-tab-pane label="同学评价" name="second"
            ><div class="score-area">
              <CommodityScore /><CommodityScoreList /></div></el-tab-pane
        ></el-tabs>
      </section>
    </template>

    <el-dialog v-model="shareDialogVisible" title="分享此商品" width="460px">
      <div class="share-dialog-content">
        <div class="share-section">
          <p>复制链接发给同学</p>
          <div class="link-container">
            <span>{{ currentPageUrl }}</span>
            <el-button type="primary" @click="copyLink">复制</el-button>
          </div>
        </div>
        <div class="share-section qr-section">
          <p>或扫描二维码打开</p>
          <QRCodeVue3
            :value="currentPageUrl"
            :width="200"
            :height="200"
            :imageOptions="{
              hideBackgroundDots: false,
              imageSize: 0.4,
              margin: 0
            }"
          />
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="buyDialogVisible" title="购买商品" width="520px">
      <el-form :model="buyForm" label-width="110px">
        <el-form-item label="购买数量" prop="buyNumber">
          <el-input-number
            v-model="buyForm.buyNumber"
            :min="1"
            :max="commodity.commodityInventory"
            @change="updatePaymentAmount"
          />
        </el-form-item>
        <el-form-item label="支付校园币" prop="paymentAmount">
          <el-input-number
            v-model="buyForm.paymentAmount"
            :min="0"
            :precision="2"
            readonly
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="buyForm.remark"
            type="textarea"
            placeholder="可填写交易地点、取货时间等备注"
            :rows="4"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBuy">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import usePrivateMessageStore from "@/store/modules/privateMessage";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Picture,
  Coin,
  Share,
  Star,
  StarFilled,
  View
} from "@element-plus/icons-vue";
import QRCodeVue3 from "qrcode-vue3";
import {
  getCommodityVoByIdUsingGet,
  buyCommodityUsingPost
} from "@/api/commodityController";
import useClipboard from "vue-clipboard3";
import {
  addUserCommodityFavoritesUsingPost,
  editUserCommodityFavoritesUsingPost,
  listMyUserCommodityFavoritesVoByPageUsingPost
} from "@/api/userCommodityFavoritesController";
import CommodityScore from "@/components/CommodityScore/index.vue";
import CommodityScoreList from "@/components/CommodityScoreList/index.vue";

import { GET_ID } from "@/utils/token";

const route = useRoute();
const router = useRouter();
const commodityId = route.params.id as string;
const currentUserId = String(GET_ID() || "");
const pageRef = ref<HTMLElement | null>(null);
const detailLoading = ref(true);
const detailFailed = ref(false);
const coverFailed = ref(false);
const detailActiveName = ref("first");
const commodity = ref({
  commodityName: "",
  tags: [],
  commodityAvatar: "",
  price: 0,
  commodityInventory: 0,
  commodityDescription: "",
  viewNum: 0,
  favourNum: 0,
  commodityTypeName: "",
  adminId: "",
  adminName: "",
  isListed: 0
});

const viewCount = ref(0);
const favourCount = ref(0);
const initStatus = ref(0);
const alreadyRecord = ref(0);
const id = ref();
const shareDialogVisible = ref(false);
const buyDialogVisible = ref(false);
const buildShareUrl = () => {
  const url = new URL(window.location.href);
  url.searchParams.delete("from");
  url.searchParams.delete("conversationId");
  return url.toString();
};
const currentPageUrl = ref(buildShareUrl());
const routeValue = (value: unknown) =>
  Array.isArray(value) ? String(value[0] || "") : String(value || "");
const isAgentEntry = computed(() => routeValue(route.query.from) === "agent");
const sourceConversationId = computed(() =>
  routeValue(route.query.conversationId)
);
const sellerId = computed(() => String(commodity.value.adminId || ""));
const canContactSeller = computed(
  () => Boolean(sellerId.value) && sellerId.value !== currentUserId
);

const buyForm = ref({
  buyNumber: 1,
  paymentAmount: 0,
  remark: ""
});

watch(
  () => buyForm.value.buyNumber,
  (newVal) => {
    const total = (newVal * commodity.value.price).toFixed(2);
    buyForm.value.paymentAmount = parseFloat(total);

    if (newVal > commodity.value.commodityInventory) {
      ElMessage.warning("购买数量超过库存！");
      buyForm.value.buyNumber = commodity.value.commodityInventory;
    }
  }
);

const fetchCommodityDetail = async () => {
  detailLoading.value = true;
  detailFailed.value = false;
  coverFailed.value = false;
  try {
    const res = await getCommodityVoByIdUsingGet({ id: commodityId });
    if (res.code === 200) {
      commodity.value = res.data;
      viewCount.value = res.data.viewNum || 0;
      favourCount.value = res.data.favourNum || 0;
      buyForm.value.paymentAmount =
        buyForm.value.buyNumber * commodity.value.price;
    } else {
      detailFailed.value = true;
    }
  } catch (error) {
    detailFailed.value = true;
  } finally {
    detailLoading.value = false;
  }
};

const updatePaymentAmount = () => {
  buyForm.value.paymentAmount = buyForm.value.buyNumber * commodity.value.price;
};

const syncFavourCount = (delta: number) => {
  const nextFavourCount = Math.max((favourCount.value || 0) + delta, 0);
  favourCount.value = nextFavourCount;
  commodity.value.favourNum = nextFavourCount;
};

const fetchInitFavour = async () => {
  const res = await listMyUserCommodityFavoritesVoByPageUsingPost({
    current: 1,
    pageSize: 1,
    commodityId: commodityId
  });
  if (res.code !== 200) {
    return ElMessage.error({
      duration: 1000,
      message: "获取用户收藏关联表失败"
    });
  }
  if (res.data.records.length > 0) {
    alreadyRecord.value = 1;
    initStatus.value = res.data.records[0].status;
    id.value = res.data.records[0].id;
  } else {
    alreadyRecord.value = 0;
    initStatus.value = 0;
  }
};

const handleCollect = async () => {
  const hadRecord = alreadyRecord.value === 1;
  const previousStatus = initStatus.value;
  if (alreadyRecord.value === 0) {
    const res2 = await addUserCommodityFavoritesUsingPost({
      commodityId: commodityId
    });
    if (res2.code !== 200) {
      return ElMessage.error({
        duration: 1000,
        message: "添加收藏失败"
      });
    }
    ElMessage.success({
      duration: 1000,
      message: "添加收藏成功"
    });
  } else {
    const res3 = await editUserCommodityFavoritesUsingPost({
      id: id.value,
      status: initStatus.value === 1 ? 0 : 1
    });
    if (res3.code !== 200) {
      return ElMessage.error({
        duration: 1000,
        message: `${initStatus.value === 1 ? "取消" : "添加"}收藏失败`
      });
    }
    ElMessage.success({
      duration: 1000,
      message: `${initStatus.value === 1 ? "取消" : "添加"}收藏成功`
    });
  }
  syncFavourCount(!hadRecord || previousStatus !== 1 ? 1 : -1);
  await fetchInitFavour();
};

const handleShare = () => {
  shareDialogVisible.value = true;
};

const returnToAgent = () => {
  const previousPath = String(window.history.state?.back || "");
  if (previousPath.startsWith("/user/agentGuide")) {
    router.back();
    return;
  }
  void router.push({
    path: "/user/agentGuide",
    query: sourceConversationId.value
      ? { conversationId: sourceConversationId.value }
      : {}
  });
};

const privateChat = usePrivateMessageStore();
const handleContactSeller = () => {
  if (!canContactSeller.value) return;
  privateChat.openContact({
    id: sellerId.value,
    userName: commodity.value.adminName || "卖家",
    userAvatar: ""
  });
};

const handleBuy = () => {
  if (commodity.value.commodityInventory <= 0) {
    return ElMessage.error({
      message: "商品库存不够，无法完成购买",
      duration: 1500
    });
  }
  buyDialogVisible.value = true;
};

const submitBuy = async () => {
  try {
    const res = await buyCommodityUsingPost({
      ...buyForm.value,
      commodityId: commodityId
    });
    if (res.code === 200) {
      if (res.data.needPay) {
        ElMessage.info("订单已创建，校园币不足，请在获得发放后尽快完成支付");
      } else {
        ElMessage.success("购买成功");
      }
      buyDialogVisible.value = false;
      await fetchCommodityDetail();
    } else {
      ElMessage.error("购买失败");
    }
  } catch (error) {
    ElMessage.error("购买失败");
  }
};

const { toClipboard } = useClipboard();
const copyLink = async () => {
  try {
    await toClipboard(currentPageUrl.value);
    ElMessage.success({
      message: "链接已复制到剪贴板",
      duration: 1000
    });
  } catch (e) {
    ElMessage.error("复制失败");
  }
};

onMounted(async () => {
  await fetchCommodityDetail();
  await fetchInitFavour();
});
</script>

<style scoped lang="scss">
.commodity-detail {
  display: grid;
  gap: 36px;
}
.agent-return-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--market-muted);
  font-size: 12px;
  .el-button {
    padding-left: 0;
    border: 0;
    background: transparent;
    box-shadow: none;
  }
}
.item-feature {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: clamp(32px, 6vw, 88px);
  align-items: center;
}
.item-visual {
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  height: clamp(340px, 38vw, 490px);
  padding: 24px;
  border-radius: 16px;
  background: var(--market-surface-soft);
  img {
    display: block;
    width: 100%;
    height: 100%;
    min-height: 0;
    object-fit: contain;
  }
}
.item-placeholder {
  display: grid;
  justify-items: center;
  gap: 12px;
  color: var(--market-muted);
  font-size: 13px;
  .el-icon {
    font-size: 40px;
  }
}
.item-sticker {
  position: absolute;
  bottom: 24px;
  right: -12px;
  padding: 13px 20px;
  color: var(--market-sticker-ink, #253348);
  background: var(--market-sticker-yellow, #ffe58b);
  font-family: var(--market-playful-font);
  font-size: 21px;
  line-height: 1.2;
  transform: rotate(7deg);
}
.item-story {
  padding: 12px 0;
  min-width: 0;
}
.item-category {
  color: var(--market-muted);
  font-size: 12px;
  letter-spacing: 2px;
}
h1 {
  margin: 12px 0 18px;
  font-size: clamp(30px, 3.5vw, 46px);
  line-height: 1.25;
  font-weight: 750;
  letter-spacing: -1.5px;
  overflow-wrap: anywhere;
}
.item-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  color: var(--market-muted);
  font-size: 13px;
}
.item-availability {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--market-success);
  }
  &.unavailable i {
    background: var(--market-muted);
  }
}
.item-price {
  display: flex;
  align-items: baseline;
  gap: 9px;
  margin: 28px 0 4px;
  strong {
    color: var(--market-ink);
    font-size: 46px;
    font-weight: 750;
    letter-spacing: -2px;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }
  span {
    font-size: 12px;
    color: var(--market-muted);
  }
}
.item-price-note {
  color: var(--market-muted);
  font-size: 11px;
}
.item-seller {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 22px 0;
  margin: 22px 0;
  border-block: 1px solid var(--market-line);
  small {
    display: block;
    color: var(--market-muted);
    font-size: 11px;
    margin-bottom: 3px;
  }
  strong {
    font-size: 14px;
    font-weight: 600;
  }
}
.seller-initial {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  color: var(--market-primary);
  background: var(--market-primary-soft);
}
.item-purchase {
  display: flex;
  align-items: center;
  gap: 18px;
  .el-button--primary {
    min-width: 170px;
    min-height: 46px;
    border-radius: 10px;
    box-shadow: none;
  }
  .seller-contact {
    background: transparent !important;
    border: 0;
    padding-inline: 0;
    .el-icon {
      margin-left: 7px;
    }
  }
}
.item-social {
  display: flex;
  gap: 24px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 14px;
  color: var(--market-muted);
  font-size: 12px;
  button {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 0;
    min-height: 40px;
    border: 0;
    background: transparent;
    color: inherit;
    cursor: pointer;
    &:hover,
    &.collected {
      color: var(--market-primary);
    }
  }
  .el-icon {
    font-size: 16px;
  }
}
.item-views {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-left: auto;
  font-size: 11px;
}
.item-details {
  padding-top: 4px;
  border-top: 1px solid var(--market-line);
  :deep(.el-tabs__item) {
    height: 60px;
    font-size: 15px;
    font-weight: 600;
  }
  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
  }
}
.description-text {
  max-width: 820px;
  margin: 16px 0 28px;
  font-size: 16px;
  color: var(--market-ink);
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.score-area,
.share-dialog-content,
.share-section {
  display: grid;
  gap: 18px;
}
.link-container {
  display: flex;
  align-items: center;
  gap: 12px;
  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.qr-section {
  justify-items: center;
}
.detail-loading {
  display: grid;
  justify-items: center;
  gap: 20px;
  padding: 50px 20px;
}
@media (max-width: 760px) {
  .commodity-detail {
    gap: 24px;
  }
  .item-feature {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .item-visual {
    height: 300px;
    padding: 20px;
  }
  .item-sticker {
    right: 10px;
    bottom: 12px;
    font-size: 18px;
  }
  .item-story {
    padding: 0;
  }
  h1 {
    font-size: 32px;
    margin: 9px 0 14px;
  }
  .item-price {
    margin-top: 22px;
    strong {
      font-size: 38px;
    }
  }
  .item-seller {
    margin: 20px 0;
    padding: 16px 0;
  }
  .item-purchase {
    gap: 14px;
    .el-button--primary {
      flex: 1;
      min-width: 0;
    }
  }
  .agent-return-bar {
    flex-wrap: wrap;
    gap: 2px;
  }
  .item-social {
    gap: 18px;
  }
}
</style>
