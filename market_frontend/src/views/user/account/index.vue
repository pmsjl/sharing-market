<template>
  <div class="market-page personal-home-page" ref="pageRef">
    <section class="profile-hero market-board">
      <img
        v-if="user.userAvatar"
        :src="user.userAvatar"
        class="avatar hero-avatar"
        alt="用户头像"
      />
      <div v-else class="avatar hero-avatar avatar-fallback">同学</div>
      <div class="profile-copy">
        <span class="market-eyebrow">PERSONAL BOARD</span>
        <h1>{{ user.userName || "我的校园摊位" }}</h1>
        <p>
          {{
            user.userProfile || "完善简介后，同学们会更容易了解你的交易偏好。"
          }}
        </p>
        <div class="profile-meta">
          <span>ID：{{ user.id }}</span>
          <span>身份：{{ user.userRole || "-" }}</span>
        </div>
      </div>
      <el-button type="primary" @click="updateUserInfo">保存资料</el-button>
    </section>

    <el-tabs v-model="activeName" class="account-tabs">
      <el-tab-pane label="个人信息" name="first">
        <section class="profile-form market-panel">
          <div class="section-title">
            <span class="market-eyebrow">PROFILE</span>
            <h2>个人信息设置</h2>
          </div>

          <div class="profile-editor">
            <div class="avatar-editor">
              <img
                v-if="user.userAvatar"
                :src="user.userAvatar"
                class="avatar"
                alt="用户头像"
              />
              <div v-else class="avatar avatar-fallback">头像</div>
              <el-upload
                :http-request="handleAvatarUpload"
                :show-file-list="false"
                accept="image/*"
              >
                <el-button type="primary">上传头像</el-button>
              </el-upload>
            </div>

            <div class="field-grid">
              <label class="field-card">
                <span>昵称</span>
                <div class="inline-edit">
                  <el-text v-if="!isEditing">{{
                    user.userName || "未设置"
                  }}</el-text>
                  <el-input v-else v-model="newUserName" @blur="saveEdit" />
                  <el-button
                    v-if="!isEditing"
                    size="small"
                    type="primary"
                    icon="Edit"
                    @click="startEditing"
                    circle
                    aria-label="编辑昵称"
                  />
                </div>
              </label>
              <label class="field-card">
                <span>用户身份</span>
                <el-input disabled v-model="user.userRole" />
              </label>
              <label class="field-card full">
                <span>用户简介</span>
                <el-input
                  type="textarea"
                  v-model="user.userProfile"
                  :rows="5"
                  placeholder="写点关于你的校园交易偏好"
                />
              </label>
            </div>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="我的攻略" name="myPosts">
        <section class="tab-panel market-panel"><MyPost /></section>
      </el-tab-pane>

      <el-tab-pane label="收藏攻略" name="second">
        <section class="tab-panel market-panel"><Post /></section>
      </el-tab-pane>

      <el-tab-pane label="我的评论" name="third">
        <section class="tab-panel market-panel"><MyComment /></section>
      </el-tab-pane>

      <el-tab-pane label="个人订单" name="fourth">
        <section class="tab-panel market-panel">
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
      </el-tab-pane>

      <el-tab-pane label="购物日历" name="fifth">
        <section class="tab-panel market-panel">
          <HeatmapChart :data="chartData" :year="selectedYear" />
        </section>
      </el-tab-pane>

      <el-tab-pane label="收藏商品" name="sixth">
        <section class="tab-panel market-panel">
          <CommodityList :commodity-list="commodityList" />
          <div class="market-pagination">
            <el-pagination
              v-model:current-page="favoritesQueryParams.current"
              v-model:page-size="favoritesQueryParams.pageSize"
              :total="favoritesTotal"
              layout="total, prev, pager, next, jumper"
              @current-change="loadCommodityFavoritesList"
              @size-change="loadCommodityFavoritesList"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="已归档对话" name="archivedAi" lazy>
        <section class="tab-panel market-panel">
          <ArchivedAiConversations />
        </section>
      </el-tab-pane>

      <el-tab-pane
        v-if="showPrivateMessageTab"
        :label="chatTabLabel"
        name="seventh"
      >
        <section class="tab-panel market-panel">
          <PrivateMessage
            :initial-contact="routeChatContact"
            :allow-directory-contacts="isAdmin"
          />
        </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRoute } from "vue-router";
import { GET_ID } from "@/utils/token";
import useUserStore from "@/store/modules/user";
import PrivateMessage from "@/components/PrivateMessage/index.vue";
import Post from "@/components/Post/index.vue";
import MyPost from "@/components/MyPost/index.vue";
import MyComment from "@/components/MyComment/index.vue";
import CommodityList from "@/components/CommodityList/index.vue";
import CommodityOrderList from "@/components/CommodityOrderList/index.vue";
import HeatmapChart from "@/components/CalendarChart/index.vue";
import ArchivedAiConversations from "@/components/ArchivedAiConversations/index.vue";
import {
  getUserVoByIdUsingGet,
  updateMyUserUsingPost
} from "@/api/userController";
import {
  getCommodityOrderHeatmapDataUsingGet,
  listMyCommodityOrderVoByPageUsingPost
} from "@/api/commodityOrderController";
import { payCommodityOrderUsingPost } from "@/api/commodityController";
import { uploadFileUsingPost } from "@/api/fileController";
import { listMyUserCommodityFavoritesVoByPageUsingPost } from "@/api/userCommodityFavoritesController";
import { animateIn } from "@/utils/motion";

const pageRef = ref<HTMLElement | null>(null);
const activeName = ref("first");
const route = useRoute();
const userStore = useUserStore();
const newUserAvatar = ref("");
const newUserName = ref("");
const isEditing = ref(false);
const chartData = ref<{ date: string; value: number }[]>([]);
const selectedYear = ref(String(new Date().getFullYear()));
const total = ref(0);
const commodityOrderList = ref<API.CommodityOrderVO[]>([]);
const commodityList = ref<any[]>([]);
const favoritesTotal = ref(0);
const routeChatContact = ref<API.UserVO | undefined>();
const currentUserId = String(GET_ID() || "");

const user = ref({
  id: "",
  userAvatar: "",
  userName: "",
  userProfile: "",
  userRole: ""
});

const isAdmin = computed(() => user.value.userRole === "admin");
const showPrivateMessageTab = computed(
  () => isAdmin.value || Boolean(routeChatContact.value?.id)
);
const chatTabLabel = computed(() => (isAdmin.value ? "聊天室" : "私聊"));

const queryParams = ref({
  current: 1,
  pageSize: 10
});

const favoritesQueryParams = ref({
  current: 1,
  pageSize: 10,
  status: 1
});

const fetchTravelData = async (payStatus = 1) => {
  try {
    const res = await getCommodityOrderHeatmapDataUsingGet({ payStatus });
    if (res.code === 200) {
      chartData.value = (res.data || []) as { date: string; value: number }[];
      return;
    }
    ElMessage.error("获取购物日历数据失败");
  } catch (error) {
    ElMessage.error("获取购物日历数据失败");
  }
};

const loadCommodityFavoritesList = async () => {
  try {
    const res = await listMyUserCommodityFavoritesVoByPageUsingPost(
      favoritesQueryParams.value
    );

    if (res.code === 200 && res.data?.records) {
      commodityList.value = res.data.records.map((item: any) => ({
        ...item,
        id: item.commodityId
      }));
      favoritesTotal.value = Number(res.data.total || 0);
      return;
    }
    commodityList.value = [];
    favoritesTotal.value = 0;
  } catch (error) {
    ElMessage.error("获取收藏商品失败");
  }
};

const refreshOrderViews = async () => {
  await fetchCommodityOrders();
  await fetchTravelData(1);
};

const handlePay = async (orderId: string) => {
  try {
    const response = await payCommodityOrderUsingPost({
      commodityOrderId: orderId
    });

    if (response.code === 200 && response.data === true) {
      ElMessage.success("支付成功");
      await refreshOrderViews();
      return;
    }

    if (response.code === 200 && response.data === false) {
      ElMessage.warning("订单已过期，请重新购买");
      await refreshOrderViews();
      return;
    }

    ElMessage.error(`支付失败：${response.message || "请稍后重试"}`);
  } catch (error) {
    ElMessage.error("支付失败");
  }
};

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

const updateUserInfo = async () => {
  const nextUserAvatar = newUserAvatar.value || user.value.userAvatar;
  const nextUserName = user.value.userName;
  const res = await updateMyUserUsingPost({
    userAvatar: nextUserAvatar,
    userName: nextUserName,
    userProfile: user.value.userProfile
  });
  if (res.code !== 200) {
    return ElMessage.error("更新用户信息失败");
  }
  await userStore.updateAvatar(nextUserAvatar);
  await userStore.updateUserName(nextUserName);
  ElMessage.success("更新用户信息成功");
  await getUserInformationById();
};

const handleAvatarUpload = async (options: any) => {
  try {
    const res = await uploadFileUsingPost(
      { biz: "user_avatar" },
      {},
      options.file
    );
    if (res.code !== 200) {
      return ElMessage.error("上传头像失败");
    }
    const uploadedUrl = res.data || "";
    newUserAvatar.value = uploadedUrl;
    user.value.userAvatar = uploadedUrl;
    ElMessage.success("上传头像成功");
  } catch (error) {
    ElMessage.error("上传头像失败");
  }
};

const getUserInformationById = async () => {
  const result = await getUserVoByIdUsingGet({
    id: GET_ID()
  });
  if (result.code === 200 && result.data) {
    user.value = {
      id: result.data.id || "",
      userAvatar: result.data.userAvatar || "",
      userName: result.data.userName || "",
      userProfile: result.data.userProfile || "",
      userRole: result.data.userRole || ""
    };
    newUserAvatar.value = user.value.userAvatar;
  }
};

const getQueryValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return String(value[0] || "");
  }
  return String(value || "");
};

const resolveChatContactFromRoute = async () => {
  const contactUserId = getQueryValue(route.query.contactUserId);

  if (!contactUserId || contactUserId === currentUserId) {
    routeChatContact.value = undefined;
    if (activeName.value === "seventh" && !isAdmin.value) {
      activeName.value = "first";
    }
    return;
  }

  const contact: API.UserVO = {
    id: contactUserId,
    userName: getQueryValue(route.query.contactName) || "对方用户",
    userAvatar: getQueryValue(route.query.contactAvatar)
  };

  if (!getQueryValue(route.query.contactName)) {
    try {
      const result = await getUserVoByIdUsingGet({ id: contactUserId });
      if (result.code === 200 && result.data) {
        contact.userName = result.data.userName || contact.userName;
        contact.userAvatar = result.data.userAvatar || contact.userAvatar;
      }
    } catch (error) {
      // route query 已经有用户 id，补充资料失败时仍允许进入私聊。
    }
  }

  routeChatContact.value = contact;
  if (route.query.tab === "chat") {
    activeName.value = "seventh";
  }
};

const startEditing = () => {
  isEditing.value = true;
  newUserName.value = user.value.userName;
};

const saveEdit = () => {
  isEditing.value = false;
  user.value.userName = newUserName.value;
};

watch(
  () => route.query,
  () => {
    resolveChatContactFromRoute();
  }
);

onMounted(async () => {
  await getUserInformationById();
  await resolveChatContactFromRoute();
  fetchCommodityOrders();
  fetchTravelData(1);
  loadCommodityFavoritesList();
  animateIn(
    pageRef.value?.querySelectorAll(".profile-hero, .account-tabs") || []
  );
});
</script>

<style scoped lang="scss">
.personal-home-page {
  display: grid;
  gap: 20px;
}

.profile-hero {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 20px;
  align-items: center;
  padding: 28px;
  overflow: hidden;
  background: linear-gradient(90deg, rgba(43, 110, 80, 0.08), transparent 38%),
    repeating-linear-gradient(
      0deg,
      transparent 0 31px,
      rgba(94, 160, 181, 0.07) 31px 32px
    ),
    var(--market-card-bg);

  &::after {
    position: absolute;
    top: 18px;
    right: 22px;
    color: var(--market-green);
    font-family: var(--market-font-mono);
    font-size: 9px;
    font-weight: 800;
    letter-spacing: 1.4px;
    content: "CAMPUS VENDOR FILE";
    opacity: 0.58;
  }
}

.avatar {
  width: 84px;
  height: 84px;
  border: 3px solid #fff7e8;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: var(--market-shadow-soft);
}

.hero-avatar {
  width: 104px;
  height: 104px;
  padding: 7px 7px 24px;
  border: 1px solid rgba(35, 49, 63, 0.14);
  border-radius: 3px;
  background: var(--market-surface);
  box-shadow: 0 14px 24px rgba(62, 45, 24, 0.18);
  transform: rotate(-2deg);
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 900;
  background: var(--market-blue);
}

.profile-copy {
  min-width: 0;

  h1 {
    margin: 8px 0;
    color: var(--market-ink);
    font-family: var(--market-font-display);
    font-size: clamp(28px, 4vw, 44px);
    font-weight: 900;
  }

  p {
    max-width: 680px;
    margin: 0;
    color: var(--market-muted);
    line-height: 1.7;
  }
}

.profile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;

  span {
    padding: 6px 10px;
    border-radius: 999px;
    color: var(--market-ink);
    font-size: 13px;
    font-weight: 800;
    font-family: var(--market-font-mono);
    background: var(--market-paper-deep);
  }
}

.account-tabs {
  min-width: 0;

  :deep(.el-tabs__header) {
    margin-bottom: 0;
    padding: 12px 12px 0;
    border: 0;
    border-radius: 10px 10px 0 0;
    background: transparent;
    box-shadow: none;
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__nav) {
    display: flex;
    gap: 5px;
    border: 0;
  }

  :deep(.el-tabs__active-bar) {
    display: none;
  }

  :deep(.el-tabs__item) {
    height: 42px;
    padding: 0 17px;
    border: 1px solid var(--market-line);
    border-bottom: 0;
    border-radius: 9px 9px 0 0;
    color: var(--market-muted);
    font-weight: 800;
    background: var(--market-paper-deep);
    transform: translateY(4px);
    transition: color var(--market-dur-fast) ease,
      transform var(--market-dur-fast) var(--market-ease-spring),
      background var(--market-dur-fast) ease;
  }

  // Element Plus 会清空首个和末个页签的边缘 padding；索引卡必须显式恢复。
  :deep(.el-tabs__item:nth-child(2)) {
    padding-left: 17px;
  }

  :deep(.el-tabs__item:last-child) {
    padding-right: 17px;
  }

  :deep(.el-tabs__item:hover) {
    color: var(--market-green);
    background: var(--market-note-green-bg);
    transform: translateY(2px);
  }

  :deep(.el-tabs__item:focus-visible) {
    box-shadow: inset 0 0 0 2px rgba(43, 110, 80, 0.32);
    outline: none;
  }

  :deep(.el-tabs__item.is-active) {
    color: var(--market-green);
    background: var(--market-surface);
    transform: translateY(0);
    z-index: 2;

    &::after {
      position: absolute;
      right: 10px;
      bottom: 4px;
      left: 10px;
      height: 2px;
      border-radius: 999px;
      background: var(--market-orange);
      content: "";
    }
  }

  :deep(.el-tabs__content) {
    position: relative;
    min-width: 0;
    border: 1px solid var(--market-line);
    border-radius: 10px;
    background: var(--market-surface);
    box-shadow: var(--market-shadow-soft);
  }
}

.profile-form,
.tab-panel {
  min-width: 0;
  padding: 24px;
  border: 0;
  box-shadow: none;
}

.section-title {
  margin-bottom: 20px;

  h2 {
    margin-top: 8px;
    font-size: 24px;
    font-weight: 900;
  }
}

.profile-editor {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 24px;
}

.avatar-editor {
  display: grid;
  gap: 14px;
  align-content: start;
  justify-items: center;
  padding: 18px;
  border: 1px dashed var(--market-line);
  border-radius: 8px;
  background: var(--market-paper-deep);
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field-card {
  display: grid;
  gap: 10px;

  > span {
    color: var(--market-muted);
    font-size: 13px;
    font-weight: 900;
  }

  &.full {
    grid-column: 1 / -1;
  }
}

.inline-edit {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 40px;
}

@media (max-width: 860px) {
  .profile-hero {
    grid-template-columns: auto minmax(0, 1fr);

    .el-button {
      grid-column: 1 / -1;
      width: 100%;
    }
  }

  .profile-editor {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .profile-hero {
    grid-template-columns: 1fr;
    justify-items: start;
    padding: 22px;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .account-tabs :deep(.el-tabs__header) {
    min-width: 0;
  }

  .account-tabs :deep(.el-tabs__nav-wrap) {
    min-width: 0;
  }

  .account-tabs :deep(.el-tabs__nav-scroll) {
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .account-tabs :deep(.el-tabs__nav) {
    width: max-content;
  }
}
</style>
