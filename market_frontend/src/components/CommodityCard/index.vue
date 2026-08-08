<template>
  <div class="market-page commodity-detail" ref="pageRef">
    <div v-if="isAgentEntry" class="agent-return-bar">
      <el-button :icon="ArrowLeft" plain @click="returnToAgent">
        返回智能导购
      </el-button>
      <span>继续查看刚才的咨询与推荐理由</span>
    </div>

    <section class="detail-hero market-board">
      <div class="detail-media">
        <img
          v-if="commodity.commodityAvatar"
          :src="commodity.commodityAvatar"
          :alt="commodity.commodityName"
        />
        <div v-else class="detail-placeholder">校园好物</div>
      </div>

      <div class="detail-summary">
        <span class="market-eyebrow">ITEM NOTE</span>
        <h1>{{ commodity.commodityName || "商品详情" }}</h1>
        <div class="status-info">
          <el-tag type="info">成色 {{ commodity.degree || "未知" }}</el-tag>
          <el-tag type="success">{{
            commodity.commodityTypeName || "未分类"
          }}</el-tag>
          <el-tag type="primary"
            >发布者 {{ commodity.adminName || "-" }}</el-tag
          >
          <el-tag v-if="commodity.isListed === 0" type="danger">未上架</el-tag>
          <el-tag v-if="commodity.isListed === 1" type="success">已上架</el-tag>
        </div>

        <div class="price-board">
          <div class="price-cell">
            <span>价格</span>
            <strong><em>￥</em>{{ commodity.price }}</strong>
          </div>
          <div class="stock-cell">
            <span>库存</span>
            <i class="stock-stamp">余量 {{ commodity.commodityInventory }}</i>
          </div>
        </div>

        <div class="action-buttons">
          <el-button type="primary" @click="handleBuy" :icon="Coin">
            购买商品
          </el-button>
          <el-button
            v-if="canContactSeller"
            type="success"
            plain
            @click="handleContactSeller"
          >
            联系卖家
          </el-button>
          <el-button @click="handleShare" :icon="Share">分享</el-button>
        </div>

        <div class="metric-strip">
          <button type="button" class="metric-item">
            <el-icon><View /></el-icon>
            <span>{{ viewCount }} 浏览</span>
          </button>
          <button
            type="button"
            class="metric-item metric-favour"
            :class="{ stamped: initStatus === 1 }"
            @click="handleCollect"
          >
            <el-icon>
              <Star v-if="initStatus === 0" />
              <StarFilled v-if="initStatus === 1" color="#e0651f" />
            </el-icon>
            <span>{{ favourCount }} 收藏</span>
            <i v-if="initStatus === 1" ref="favourStamp" class="favour-stamp"
              >已收藏</i
            >
          </button>
        </div>
      </div>
    </section>

    <section class="detail-tabs market-panel">
      <el-tabs v-model="detailActiveName">
        <el-tab-pane label="商品详情" name="first">
          <p class="description-text">
            {{ commodity.commodityDescription || "卖家暂未填写商品详情。" }}
          </p>
        </el-tab-pane>
        <el-tab-pane label="商品评分" name="second">
          <div class="score-area">
            <CommodityScore />
            <CommodityScoreList />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

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
        <el-form-item label="支付金额" prop="paymentAmount">
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
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  ArrowLeft,
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
import { animateIn, stampIn } from "@/utils/motion";
import { GET_ID } from "@/utils/token";

const route = useRoute();
const router = useRouter();
const commodityId = route.params.id as string;
const currentUserId = String(GET_ID() || "");
const pageRef = ref<HTMLElement | null>(null);
const favourStamp = ref<HTMLElement | null>(null);
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
  try {
    const res = await getCommodityVoByIdUsingGet({ id: commodityId });
    if (res.code === 200) {
      commodity.value = res.data;
      viewCount.value = res.data.viewNum || 0;
      favourCount.value = res.data.favourNum || 0;
      buyForm.value.paymentAmount =
        buyForm.value.buyNumber * commodity.value.price;
    } else {
      ElMessage.error("获取商品详情失败");
    }
  } catch (error) {
    ElMessage.error("获取商品详情失败");
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
  const willStamp = !hadRecord || previousStatus !== 1;
  await fetchInitFavour();
  if (willStamp) {
    await nextTick();
    stampIn(favourStamp.value);
  }
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

const handleContactSeller = () => {
  if (!canContactSeller.value) return;
  router.push({
    path: "/user/account",
    query: {
      tab: "chat",
      contactUserId: sellerId.value,
      contactName: commodity.value.adminName || "卖家"
    }
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
        ElMessage.info("订单已创建，余额不足，请尽快完成订单支付");
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
  animateIn(
    pageRef.value?.querySelectorAll(".detail-hero, .detail-tabs") || []
  );
});
</script>

<style scoped lang="scss">
.commodity-detail {
  display: grid;
  gap: 20px;
}

.agent-return-bar {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border: 1px dashed var(--market-line);
  border-radius: 8px;
  color: var(--market-muted);
  background: var(--market-surface);
  font-size: 13px;
}

.agent-return-bar .el-button {
  min-height: 40px;
}

.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 28px;
  padding: 28px;
}

.detail-media {
  position: relative;
  aspect-ratio: 4 / 3;
  padding: 14px;
  overflow: hidden;
  border: 1px solid rgba(143, 93, 51, 0.2);
  border-radius: 8px;
  background: var(--market-paper-deep);
  box-shadow: inset 0 2px 10px rgba(62, 45, 24, 0.08);

  img,
  .detail-placeholder {
    width: 100%;
    height: 100%;
    border-radius: 4px;
    object-fit: cover;
  }
}

.detail-placeholder {
  display: grid;
  place-items: center;
  color: var(--market-muted);
  font-family: var(--market-font-display);
  font-size: 24px;
  font-weight: 900;
}

.detail-summary {
  align-self: center;

  h1 {
    margin: 14px 0;
    color: var(--market-ink);
    font-family: var(--market-font-display);
    font-size: clamp(30px, 4vw, 48px);
    font-weight: 900;
    line-height: 1.12;
  }
}

.status-info,
.action-buttons,
.metric-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.price-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 12px;
  max-width: 460px;
  margin: 22px 0;

  > div {
    padding: 16px;
    border: 1px dashed rgba(35, 49, 63, 0.18);
    border-radius: 8px;
    background: var(--market-paper-deep);
  }

  span {
    display: block;
    color: var(--market-muted);
    font-size: 13px;
    font-weight: 800;
  }
}

.price-cell strong {
  display: block;
  margin-top: 6px;
  color: var(--market-orange);
  font-family: var(--market-font-mono);
  font-size: 40px;
  font-weight: 900;
  line-height: 1;

  em {
    margin-right: 2px;
    font-size: 22px;
    font-style: normal;
    vertical-align: 8px;
  }
}

// 余量章
.stock-cell .stock-stamp {
  margin-top: 10px;
  font-size: 15px;
  font-style: normal;
  @include stamp-text(var(--market-green));
}

.metric-strip {
  margin-top: 20px;
}

.metric-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--market-line);
  border-radius: 999px;
  color: var(--market-ink);
  font-weight: 800;
  background: var(--market-surface);
  cursor: pointer;
  transition: border-color var(--market-dur-fast) ease,
    box-shadow var(--market-dur-fast) ease;

  &:hover {
    border-color: rgba(224, 101, 31, 0.4);
    box-shadow: var(--market-shadow-soft);
  }
}

// 已收藏态：橙色描边 + 盖章
.metric-favour.stamped {
  border-color: rgba(224, 101, 31, 0.55);
  background: rgba(224, 101, 31, 0.08);
}

.favour-stamp {
  position: absolute;
  top: -14px;
  right: -10px;
  font-size: 11px;
  font-style: normal;
  letter-spacing: 1px;
  @include stamp-text(var(--market-stamp-red));
  background: var(--market-surface);
}

.detail-tabs {
  padding: 24px;
}

.description-text {
  color: var(--market-muted);
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.score-area {
  display: grid;
  gap: 16px;
}

.share-dialog-content {
  display: grid;
  gap: 20px;
}

.share-section {
  display: grid;
  gap: 10px;

  p {
    color: var(--market-ink);
    font-weight: 900;
  }
}

.link-container {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px dashed var(--market-line);
  border-radius: 8px;
  background: var(--market-paper-deep);

  span {
    overflow: hidden;
    color: var(--market-muted);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.qr-section {
  justify-items: center;
}

// 深色模式下的价格/库存纸底与按钮底
html.dark .price-board > div,
html.dark .metric-item,
html.dark .link-container {
  background: var(--market-paper-deep);
}

@media (max-width: 860px) {
  .detail-hero {
    grid-template-columns: 1fr;
    padding: 20px;
  }
}

@media (max-width: 520px) {
  .agent-return-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .price-board,
  .link-container {
    grid-template-columns: 1fr;
  }
}
</style>
