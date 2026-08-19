<template>
  <div class="top-actions">
    <el-button size="small" @click="updateRefsh" icon="Refresh" circle />
    <el-button size="small" @click="fullScren" icon="FullScreen" circle />

    <el-popover
      placement="bottom"
      :width="320"
      trigger="click"
      popper-class="campus-theme-popper"
    >
      <div class="theme-panel">
        <div class="theme-heading">
          <span class="theme-kicker">MARKET PASS</span>
          <strong>市集外观</strong>
          <p>选择校牌颜色，并切换白天或夜间逛摊。</p>
        </div>
        <div class="theme-section">
          <span class="theme-label">校牌颜色</span>
          <div class="accent-options" role="radiogroup" aria-label="校牌颜色">
            <button
              v-for="option in accentOptions"
              :key="option.value"
              type="button"
              class="accent-option"
              :class="{ 'is-active': accentPreset === option.value }"
              role="radio"
              :aria-checked="accentPreset === option.value"
              @click="setAccent(option.value)"
            >
              <i :style="{ background: option.color }"></i>
              <span>{{ option.label }}</span>
            </button>
          </div>
        </div>
        <div class="mode-row">
          <span>
            <b>夜间校园</b>
            <small>降低眩光，保留摊位暖灯</small>
          </span>
          <el-switch
            @change="changeThemeMode"
            v-model="dark"
            inline-prompt
            active-icon="MoonNight"
            inactive-icon="Sunny"
          />
        </div>
      </div>
      <template #reference>
        <el-button size="small" icon="Setting" circle />
      </template>
    </el-popover>

    <img class="user-avatar" :src="userStore.avatar" alt="用户头像" />
    <el-dropdown>
      <button class="user-trigger" type="button">
        <span>{{ userStore.userAccount || "同学" }}</span>
        <el-icon class="el-icon--right"><arrow-down /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item icon="UserFilled" @click="goPersonalHomePage"
            >个人主页</el-dropdown-item
          >
          <el-dropdown-item @click="logout" icon="CircleClose"
            >退出登录</el-dropdown-item
          >
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
  applyAccentPreset,
  applyThemeMode,
  getStoredAccentPreset,
  getStoredThemeMode,
  ThemeAccentPreset
} from "@/utils/theme";

const $router = useRouter();
const layOutSettingStore = useLayOutSettingStore();
const userStore = userUserStore();
const dark = ref<boolean>(getStoredThemeMode() === "night");
const accentPreset = ref<ThemeAccentPreset>(getStoredAccentPreset());
const accentOptions: Array<{
  value: ThemeAccentPreset;
  label: string;
  color: string;
}> = [
  { value: "campus-blue", label: "校园蓝", color: "#2563eb" },
  { value: "indigo", label: "学院靛青", color: "#4f46e5" },
  { value: "lake-blue", label: "湖面蓝", color: "#0284c7" }
];
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
  applyThemeMode(dark.value ? "night" : "light");
  applyAccentPreset(accentPreset.value);
  getUserInformationById();
});

const getUserInformationById = async () => {
  const id = GET_ID();
  if (id == null) return ElMessage.info("获取用户信息失败");
  const result: any = await getUserVoByIdUsingGet({
    id: BigInt(id as string) as any
  });
  if (result.code == 200) user.value = result.data;
  userStore.avatar = user.value.userAvatar;
  userStore.userAccount = user.value.userAccount;
};
const updateRefsh = () => {
  layOutSettingStore.refsh = !layOutSettingStore.refsh;
};
const fullScren = () =>
  document.fullscreenElement
    ? document.exitFullscreen()
    : document.documentElement.requestFullscreen();
const goPersonalHomePage = () => $router.push("/user/account");
const logout = async () => {
  await userStore.userLogout();
  $router.push({ path: "/login" });
};
const changeThemeMode = () => applyThemeMode(dark.value ? "night" : "light");
const setAccent = (preset: ThemeAccentPreset) => {
  accentPreset.value = preset;
  applyAccentPreset(preset);
};
</script>
<script lang="ts">
export default { name: "Setting" };
</script>

<style scoped lang="scss">
.top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  width: 36px;
  height: 36px;
  margin-left: 4px;
  padding: 2px;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  background: var(--market-surface);
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
  font-weight: 750;
  background: transparent;
  cursor: pointer;
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.theme-panel {
  color: var(--market-ink);
}
.theme-heading {
  padding: 4px 2px 14px;
  border-bottom: 1px solid var(--market-line);
  strong {
    display: block;
    margin-top: 4px;
    font-family: var(--market-font-display);
    font-size: 20px;
  }
  p {
    margin: 5px 0 0;
    color: var(--market-muted);
    font-size: 13px;
    line-height: 1.55;
  }
}
.theme-kicker {
  color: var(--market-orange);
  font-family: var(--market-font-mono);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.5px;
}
.theme-section {
  padding: 15px 0;
}
.theme-label {
  display: block;
  margin-bottom: 9px;
  color: var(--market-muted);
  font-size: 12px;
  font-weight: 700;
}
.accent-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.accent-option {
  display: grid;
  gap: 5px;
  min-height: 58px;
  padding: 8px 6px;
  place-items: center;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  color: var(--market-muted);
  font-size: 11px;
  background: var(--market-surface);
  cursor: pointer;
  transition: transform var(--market-dur-fast),
    border-color var(--market-dur-fast), background var(--market-dur-fast);
  i {
    width: 22px;
    height: 22px;
    border: 3px solid rgba(255, 255, 255, 0.82);
    border-radius: 50%;
    box-shadow: 0 0 0 1px var(--market-line);
  }
  &:hover {
    transform: translateY(-2px);
    border-color: var(--market-primary);
  }
  &.is-active {
    border-color: var(--market-primary);
    color: var(--market-primary);
    font-weight: 800;
    background: var(--market-primary-soft);
  }
}
.mode-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px;
  border-radius: 12px;
  background: var(--market-surface-soft);
  span {
    display: grid;
    gap: 3px;
  }
  b {
    font-size: 13px;
  }
  small {
    color: var(--market-muted);
    font-size: 11px;
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
