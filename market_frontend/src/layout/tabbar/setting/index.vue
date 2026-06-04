<template>
  <div class="top-actions">
    <el-button size="small" @click="updateRefsh" icon="Refresh" circle />
    <el-button size="small" @click="fullScren" icon="FullScreen" circle />

    <el-popover
      placement="bottom"
      title="主题设置"
      :width="300"
      trigger="click"
    >
      <el-form>
        <el-form-item label="主题颜色">
          <el-color-picker
            @change="setColor"
            v-model="color"
            :teleported="false"
            size="small"
            show-alpha
            :predefine="predefineColors"
          />
        </el-form-item>
        <el-form-item label="夜间自习">
          <el-switch
            @change="changeThemeMode"
            v-model="dark"
            inline-prompt
            active-icon="MoonNight"
            inactive-icon="Sunny"
          />
        </el-form-item>
      </el-form>
      <template #reference>
        <el-button size="small" icon="Setting" circle />
      </template>
    </el-popover>

    <img class="user-avatar" :src="userStore.avatar" alt="用户头像" />
    <el-dropdown>
      <button class="user-trigger" type="button">
        <span>{{ userStore.userAccount || "同学" }}</span>
        <el-icon class="el-icon--right">
          <arrow-down />
        </el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item icon="UserFilled" @click="goPersonalHomePage">
            个人主页
          </el-dropdown-item>
          <el-dropdown-item @click="logout" icon="CircleClose">
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import { onMounted, ref } from "vue";
import userUserStore from "@/store/modules/user";
import useLayOutSettingStore from "@/store/modules/setting";
import { GET_ID } from "@/utils/token";
import { UserData } from "@/api/user/type";
import { getUserVoByIdUsingGet } from "@/api/userController";
import { ElMessage } from "element-plus";
import {
  applyAccentColor,
  applyThemeMode,
  getStoredAccentColor,
  getStoredThemeMode
} from "@/utils/theme";

const $router = useRouter();
const layOutSettingStore = useLayOutSettingStore();
const userStore = userUserStore();
const dark = ref<boolean>(getStoredThemeMode() === "night");
const user = ref<UserData>({
  id: 0,
  userName: "",
  userAccount: "",
  userAvatar: "",
  gender: 0,
  userRole: "",
  userPassword: "",
  accessKey: "",
  secretKey: "",
  invitationCode: "",
  email: "",
  balance: 0,
  createTime: "",
  updateTime: "",
  isDelete: 0,
  tokenValue: ""
});

onMounted(() => {
  color.value = getStoredAccentColor();
  applyThemeMode(dark.value ? "night" : "light");
  applyAccentColor(color.value);
  getUserInformationById();
});

const getUserInformationById = async () => {
  const id = GET_ID();
  if (id == null) {
    return ElMessage.info("获取用户信息失败");
  }
  const stringId = BigInt(id as string) as any;
  const result: any = await getUserVoByIdUsingGet({
    id: stringId
  });
  if (result.code == 200) {
    user.value = result.data;
  }
  getUserAvatar();
  getUserAccount();
};

const getUserAvatar = () => {
  userStore.avatar = user.value.userAvatar;
};

const getUserAccount = () => {
  userStore.userAccount = user.value.userAccount;
};

const updateRefsh = () => {
  layOutSettingStore.refsh = !layOutSettingStore.refsh;
};

const fullScren = () => {
  const full = document.fullscreenElement;
  if (!full) {
    document.documentElement.requestFullscreen();
  } else {
    document.exitFullscreen();
  }
};

const goPersonalHomePage = () => {
  $router.push("/user/account");
};

const logout = async () => {
  await userStore.userLogout();
  $router.push({ path: "/login" });
};

const color = ref("rgba(47, 125, 92, 1)");
const predefineColors = ref([
  "#2f7d5c",
  "#26547c",
  "#d96c2c",
  "#f4c95d",
  "#c64545",
  "#8f5d33",
  "rgba(217, 108, 44, 0.82)",
  "rgba(47, 125, 92, 0.85)"
]);

const changeThemeMode = () => {
  applyThemeMode(dark.value ? "night" : "light");
};

const setColor = () => {
  applyAccentColor(color.value);
};
</script>
<script lang="ts">
export default {
  name: "Setting"
};
</script>
<style scoped lang="scss">
.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 34px;
  height: 34px;
  margin-left: 4px;
  border: 2px solid #fff7e8;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: var(--market-shadow-soft);
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  max-width: 160px;
  border: 0;
  color: var(--market-ink);
  font-weight: 800;
  background: transparent;
  cursor: pointer;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 560px) {
  .top-actions .el-button:nth-child(2),
  .top-actions .el-button:nth-child(3) {
    display: none;
  }

  .user-trigger {
    max-width: 88px;
  }
}
</style>
