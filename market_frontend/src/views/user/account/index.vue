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
          <span>用户 ID：{{ user.id }}</span>
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

      <el-tab-pane label="聊天室" name="seventh">
        <section class="tab-panel market-panel"><PrivateMessage /></section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { GET_ID } from "@/utils/token";
import useUserStore from "@/store/modules/user";
import PrivateMessage from "@/components/PrivateMessage/index.vue";
import Post from "@/components/Post/index.vue";
import MyComment from "@/components/MyComment/index.vue";
import CommodityList from "@/components/CommodityList/index.vue";
import CommodityOrderList from "@/components/CommodityOrderList/index.vue";
import HeatmapChart from "@/components/CalendarChart/index.vue";
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

const user = ref({
  id: 0,
  userAvatar: "",
  userName: "",
  userProfile: "",
  userRole: ""
});

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

const handlePay = async (orderId: number) => {
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
      id: result.data.id || 0,
      userAvatar: result.data.userAvatar || "",
      userName: result.data.userName || "",
      userProfile: result.data.userProfile || "",
      userRole: result.data.userRole || ""
    };
    newUserAvatar.value = user.value.userAvatar;
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

onMounted(() => {
  getUserInformationById();
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
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 20px;
  align-items: center;
  padding: 28px;
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
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 900;
  background: var(--market-blue);
}

.profile-copy {
  h1 {
    margin: 8px 0;
    font-size: clamp(28px, 4vw, 44px);
    font-weight: 900;
  }

  p {
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
    background: #fff7e8;
  }
}

.account-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
    padding: 8px;
    border: 1px solid var(--market-line);
    border-radius: 8px;
    background: var(--market-surface);
    box-shadow: var(--market-shadow-soft);
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }
}

.profile-form,
.tab-panel {
  padding: 24px;
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
  background: #fff7e8;
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
}
</style>
