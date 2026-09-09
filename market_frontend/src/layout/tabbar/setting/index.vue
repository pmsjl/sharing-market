<template>
  <div class="top-actions">
    <button
      class="paper-tool message-entry"
      type="button"
      :aria-label="chat.hasUnread ? '校园私信，有新消息' : '校园私信'"
      :aria-expanded="chat.opened"
      @click="chat.openContact()"
    >
      <el-icon><Message /></el-icon><span>消息</span
      ><i v-if="chat.hasUnread" class="message-dot" aria-hidden="true"></i>
    </button>
    <el-tooltip content="刷新页面" placement="bottom"
      ><button
        class="paper-tool utility-tool"
        type="button"
        aria-label="刷新页面"
        @click="updateRefsh"
      >
        <el-icon><Refresh /></el-icon></button
    ></el-tooltip>
    <el-tooltip content="切换全屏" placement="bottom"
      ><button
        class="paper-tool utility-tool fullscreen-tool"
        type="button"
        aria-label="切换全屏"
        @click="fullScren"
      >
        <el-icon><FullScreen /></el-icon></button
    ></el-tooltip>

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
        <button
          class="paper-tool"
          type="button"
          aria-label="市集外观设置"
          title="市集外观设置"
        >
          <el-icon><SettingIcon /></el-icon>
        </button>
      </template>
    </el-popover>

    <el-dropdown trigger="click" popper-class="campus-account-menu">
      <button class="user-trigger" type="button" aria-label="打开个人菜单">
        <el-avatar class="user-avatar" :src="userStore.avatar" shape="square">{{
          (userStore.userName || "同学").slice(0, 1)
        }}</el-avatar>
        <span class="user-trigger-copy"
          ><small>校园通行证</small
          ><strong>{{
            userStore.userName || userStore.userAccount || "同学"
          }}</strong></span
        >
        <el-icon class="el-icon--right"><arrow-down /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <li class="account-menu-identity" role="presentation">
            <el-avatar :size="38" :src="userStore.avatar" shape="square">{{
              (userStore.userName || "同学").slice(0, 1)
            }}</el-avatar>
            <span
              ><small>我的校园通行证</small
              ><strong>{{
                userStore.userName || userStore.userAccount || "同学"
              }}</strong></span
            >
          </li>
          <el-dropdown-item icon="User" @click="goPersonalHomePage"
            >个人主页</el-dropdown-item
          >
          <el-dropdown-item
            divided
            class="account-logout"
            @click="logout"
            icon="SwitchButton"
            >退出登录</el-dropdown-item
          >
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { Setting as SettingIcon } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { onMounted, ref } from "vue";
import userUserStore from "@/store/modules/user";
import useLayOutSettingStore from "@/store/modules/setting";
import usePrivateMessageStore from "@/store/modules/privateMessage";
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
const chat = usePrivateMessageStore();
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
  userStore.userName = user.value.userName;
  userStore.userAccount = user.value.userAccount;
};
const updateRefsh = () => {
  layOutSettingStore.refsh = !layOutSettingStore.refsh;
};
const fullScren = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch {
    ElMessage.info("当前浏览器暂不支持全屏");
  }
};
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
.paper-tool {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 44px;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--market-line);
  border-radius: 5px 11px 5px 5px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: 0 3px 0 var(--market-surface-soft);
  cursor: pointer;
  transition: background 160ms, border-color 160ms;
  .el-icon {
    font-size: 17px;
  }
  &:hover {
    border-color: var(--market-primary);
    color: var(--market-primary);
    background: var(--market-primary-soft);
  }
  &:focus-visible {
    outline: 2px solid var(--market-primary);
    outline-offset: 3px;
  }
}
.message-entry {
  border-color: var(--market-line-strong);
  font-weight: 700;
}
.message-dot {
  position: absolute;
  right: 5px;
  top: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--market-danger);
  box-shadow: 0 0 0 2px var(--market-surface);
}
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--market-primary-soft);
  color: var(--market-primary);
}
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 50px;
  max-width: 210px;
  padding: 6px 11px 6px 7px;
  margin-left: 4px;
  border: 1px solid var(--market-line);
  border-left: 3px solid var(--market-orange);
  border-radius: 5px 11px 5px 5px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  cursor: pointer;
  &:hover {
    border-color: var(--market-orange);
    background: var(--market-paper);
  }
  &:focus-visible {
    outline: 2px solid var(--market-primary);
    outline-offset: 3px;
  }
}
.user-trigger-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  text-align: left;
  small {
    color: var(--market-muted);
    font-size: 10px;
    letter-spacing: 1px;
  }
  strong {
    font-size: 13px;
    font-weight: 700;
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
@media (max-width: 760px) {
  .utility-tool {
    display: none;
  }
  .user-trigger-copy {
    display: none;
  }
  .user-trigger {
    margin-left: 0;
    min-height: 44px;
    padding: 4px;
    gap: 2px;
  }
  .user-trigger .el-icon--right {
    margin-left: 0;
  }
  .top-actions {
    gap: 6px;
  }
  .paper-tool {
    padding: 0 10px;
  }
}
@media (max-width: 420px) {
  .message-entry > span {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .paper-tool {
    transition: none;
  }
}
</style>
<style lang="scss">
.campus-account-menu.el-popper {
  min-width: 200px;
  border: 1px solid var(--market-line);
  border-top: 3px solid var(--market-orange);
  border-top-color: var(--market-orange) !important;
  border-radius: 6px 14px 6px 6px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow);
  .el-dropdown-menu {
    padding: 8px;
    background: transparent;
  }
  .account-menu-identity {
    display: flex;
    align-items: center;
    gap: 11px;
    padding: 12px 14px 16px;
    margin-bottom: 7px;
    border-bottom: 1px dashed var(--market-line-strong);
    .el-avatar {
      flex-shrink: 0;
      background: var(--market-primary-soft);
      color: var(--market-primary);
      border-radius: 4px;
    }
    span {
      display: grid;
      gap: 5px;
      max-width: 150px;
    }
    small {
      color: var(--market-muted);
      font-size: 11px;
    }
    strong {
      color: var(--market-ink);
      font-size: 14px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .el-dropdown-menu__item {
    min-height: 44px;
    padding: 10px 14px;
    gap: 9px;
    border-radius: 5px;
    color: var(--market-ink);
    font-size: 14px;
  }
  .el-dropdown-menu__item:not(.is-disabled):hover,
  .el-dropdown-menu__item:focus {
    color: var(--market-primary);
    background: var(--market-primary-soft);
  }
  .el-dropdown-menu__item--divided {
    border-top: 1px dashed var(--market-line-strong);
    margin-top: 7px;
  }
  .account-logout:hover,
  .account-logout:focus {
    color: var(--market-danger) !important;
    background: var(--market-danger-soft) !important;
  }
}
</style>
