<template>
  <nav v-if="mobile" class="market-bottom-nav" aria-label="手机主导航">
    <router-link
      v-for="item in mobileLinks"
      :key="item.path"
      :to="item.path"
      :class="{ selected: isActive(item.path) }"
      :aria-current="isActive(item.path) ? 'page' : undefined"
    >
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </router-link>
  </nav>
  <div v-else class="market-navigation">
    <a href="#market-main" class="skip-content" @click.prevent="focusMain"
      >跳到主要内容</a
    >
    <router-link to="/user/home" class="market-brand" aria-label="校园集市首页">
      <img :src="setting.logo" alt="" width="48" height="48" />
      <span
        ><strong>校园集市<span class="brand-dot">.</span></strong
        ><small>SHARING MARKET</small></span
      >
    </router-link>
    <nav class="market-desktop-nav" aria-label="主导航">
      <router-link
        v-for="item in desktopLinks"
        :key="item.path"
        :to="item.path"
        :class="{ selected: isActive(item.path) }"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        >{{ item.label
        }}<span v-if="item.path === '/user/agentGuide'" class="ai-label"
          >AI</span
        ></router-link
      >
    </nav>
    <div class="market-account-actions">
      <router-link class="publish-link" to="/user/commodity?publish=1"
        ><el-icon><Plus /></el-icon>发布闲置</router-link
      >
      <Setting consumer />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";
import {
  House,
  ShoppingBag,
  MagicStick,
  User,
  Plus,
  ChatDotRound
} from "@element-plus/icons-vue";
import Setting from "./tabbar/setting/index.vue";
import setting from "@/setting";

defineProps({ mobile: Boolean });
const route = useRoute();
const desktopLinks = [
  { label: "逛逛", path: "/user/home" },
  { label: "发现好物", path: "/user/commodity" },
  { label: "同学攻略", path: "/user/post" },
  { label: "帮我选", path: "/user/agentGuide" }
];
const mobileLinks = [
  { label: "逛逛", path: "/user/home", icon: House },
  { label: "好物", path: "/user/commodity", icon: ShoppingBag },
  { label: "帮我选", path: "/user/agentGuide", icon: MagicStick },
  { label: "攻略", path: "/user/post", icon: ChatDotRound },
  { label: "我的", path: "/user/account", icon: User }
];
const isActive = (path: string) =>
  route.path === path || route.path.startsWith(path + "/");
const focusMain = () => document.getElementById("market-main")?.focus();
</script>

<style scoped lang="scss">
.market-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  height: 100%;
  max-width: 1408px;
  padding: 0 40px;
  margin: 0 auto;
}
.market-brand {
  display: flex;
  flex-shrink: 0;
  gap: var(--market-logo-gap);
  align-items: center;
  img {
    width: var(--market-logo-size);
    height: var(--market-logo-size);
    object-fit: contain;
  }
  strong {
    display: block;
    font-size: 21px;
    font-weight: 800;
    letter-spacing: -1px;
    line-height: 1.3;
  }
  small {
    display: block;
    font-size: 8px;
    font-weight: 700;
    letter-spacing: 1.6px;
    margin-top: 2px;
  }
}
.brand-dot {
  color: var(--market-primary);
}
.market-desktop-nav {
  display: flex;
  align-items: stretch;
  gap: 30px;
  height: 100%;
}
.market-desktop-nav a {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--market-muted);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  transition: color 180ms;
  &:hover,
  &.selected {
    color: var(--market-ink);
  }
  &.selected::after {
    content: "";
    position: absolute;
    height: 3px;
    border-radius: 3px;
    background: var(--market-primary);
    bottom: 17px;
    left: 0;
    right: 0;
  }
}
.ai-label {
  padding: 1px 4px;
  border-radius: 4px;
  color: var(--market-primary);
  background: var(--market-primary-soft);
  font-size: 9px;
  font-weight: 800;
}
.market-account-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.publish-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 42px;
  padding: 0 17px;
  border-radius: var(--market-action-radius);
  color: var(--market-on-primary);
  background: var(--market-primary);
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  transition: background 180ms;
  &:hover {
    background: var(--market-primary-hover);
  }
}
.skip-content {
  position: fixed;
  top: -100px;
  left: 16px;
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--market-surface);
  z-index: 100;
  &:focus {
    top: 8px;
  }
}
.market-bottom-nav {
  display: none;
}
@media (max-width: 1100px) {
  .market-navigation {
    padding-inline: 24px;
    gap: 18px;
  }
  .market-desktop-nav {
    gap: 20px;
  }
  .publish-link {
    display: none;
  }
}
@media (max-width: 760px) {
  .market-navigation {
    padding-inline: 18px;
    gap: 8px;
  }
  .market-brand {
    strong {
      font-size: 18px;
    }
    small {
      font-size: 7px;
      letter-spacing: 1px;
    }
  }
  .market-desktop-nav {
    display: none;
  }
  .market-bottom-nav {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    height: var(--market-mobile-nav-height);
    box-sizing: border-box;
    border-top: 1px solid var(--market-line);
    background: var(--market-surface);
    padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
    z-index: 19;
    a {
      display: flex;
      min-height: 48px;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 3px;
      color: var(--market-muted);
      font-size: 10px;
    }
    .el-icon {
      font-size: 21px;
    }
    a.selected {
      color: var(--market-primary);
      font-weight: 700;
    }
  }
}
</style>
