<template>
  <div class="market-page commodity-detail" ref="pageRef">
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
          <div>
            <span>价格</span>
            <strong>￥{{ commodity.price }}</strong>
          </div>
          <div>
            <span>库存</span>
            <strong>{{ commodity.commodityInventory }}</strong>
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
          <button type="button" class="metric-item" @click="handleCollect">
            <el-icon>
              <Star v-if="initStatus === 0" />
              <StarFilled v-if="initStatus === 1" color="#d96c2c" />
            </el-icon>
            <span>{{ favourCount }} 收藏</span>
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
import { Coin } from "@element-plus/icons-vue";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Share, Star, StarFilled, View } from "@element-plus/icons-vue";
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
import { animateIn } from "@/utils/motion";
import { GET_ID } from "@/utils/token";

const route = useRoute();
const router = useRouter();
const commodityId = route.params.id as string;
const currentUserId = String(GET_ID() || "");
const pageRef = ref<HTMLElement | null>(null);
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
const currentPageUrl = ref(window.location.href);
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
  await fetchInitFavour();
};

const handleShare = () => {
  shareDialogVisible.value = true;
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

.detail-hero {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 28px;
  padding: 28px;
}

.detail-media {
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: #f1dec0;

  img,
  .detail-placeholder {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.detail-placeholder {
  display: grid;
  place-items: center;
  color: rgba(35, 49, 63, 0.58);
  font-size: 24px;
  font-weight: 900;
}

.detail-summary {
  align-self: center;

  h1 {
    margin: 14px 0;
    color: var(--market-ink);
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
  max-width: 420px;
  margin: 22px 0;

  div {
    padding: 16px;
    border: 1px dashed rgba(35, 49, 63, 0.18);
    border-radius: 8px;
    background: #fff7e8;
  }

  span {
    display: block;
    color: var(--market-muted);
    font-size: 13px;
    font-weight: 800;
  }

  strong {
    display: block;
    margin-top: 6px;
    color: var(--market-orange);
    font-size: 28px;
    font-weight: 900;
  }
}

.metric-strip {
  margin-top: 20px;
}

.metric-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--market-line);
  border-radius: 999px;
  color: var(--market-ink);
  font-weight: 800;
  background: #fffdf8;
  cursor: pointer;
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
  background: #fff7e8;

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

@media (max-width: 860px) {
  .detail-hero {
    grid-template-columns: 1fr;
    padding: 20px;
  }
}

@media (max-width: 520px) {
  .price-board,
  .link-container {
    grid-template-columns: 1fr;
  }
}
</style>
