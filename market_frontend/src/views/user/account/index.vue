<template>
  <div class="account-space editorial-surface">
    <aside class="account-navigation" aria-label="个人中心导航">
      <p>我的校园生活</p>
      <nav>
        <router-link
          v-for="item in ACCOUNT_SECTIONS"
          :key="item.path"
          :to="item.path"
          :class="{ active: route.path === item.path }"
          :aria-current="route.path === item.path ? 'page' : undefined"
          >{{ item.label }}</router-link
        >
      </nav>
    </aside>
    <main class="account-body">
      <div class="account-context" :class="{ 'on-overview': !section }">
        <router-link v-if="section" class="account-back" to="/user/account"
          >‹ 返回概览</router-link
        >
        <el-dropdown
          class="account-mobile-switch"
          trigger="click"
          @command="selectSection"
          @visible-change="menuOpen = $event"
        >
          <button
            type="button"
            class="account-switch-button"
            aria-label="切换个人中心分组"
            aria-haspopup="menu"
            :aria-expanded="menuOpen"
          >
            {{ activeSection.label }}<el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown
            ><el-dropdown-menu class="account-section-menu"
              ><el-dropdown-item
                v-for="item in ACCOUNT_SECTIONS"
                :key="item.path"
                :command="item.path"
                :aria-current="route.path === item.path ? 'page' : undefined"
                :class="{ 'is-current': route.path === item.path }"
                >{{ item.label
                }}<el-icon v-if="route.path === item.path"
                  ><Check /></el-icon></el-dropdown-item></el-dropdown-menu
          ></template>
        </el-dropdown>
      </div>
      <component :is="panel" :key="section" />
    </main>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, watch, ref, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import usePrivateMessageStore from "@/store/modules/privateMessage";
import { GET_ID } from "@/utils/token";
import { getUserVoByIdUsingGet } from "@/api/userController";
import { ArrowDown, Check } from "@element-plus/icons-vue";
import {
  queryText,
  ACCOUNT_SECTIONS,
  accountSectionPath
} from "@/utils/marketNavigation";
const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);
const activeSection = computed(
  () =>
    ACCOUNT_SECTIONS.find((item) => item.path === route.path) ||
    ACCOUNT_SECTIONS[0]
);
async function selectSection(path: unknown) {
  await router.push(accountSectionPath(path));
  await nextTick();
  document.querySelector<HTMLButtonElement>(".account-switch-button")?.focus();
}
const chat = usePrivateMessageStore();
const sections = {
  trade: defineAsyncComponent(() => import("./Trade.vue")),
  content: defineAsyncComponent(() => import("./Content.vue")),
  wallet: defineAsyncComponent(() => import("./Wallet.vue")),
  settings: defineAsyncComponent(() => import("./Settings.vue"))
};
const overview = defineAsyncComponent(() => import("./Overview.vue"));
const section = computed(() => route.path.split("/")[3] || "");
const panel = computed(
  () => sections[section.value as keyof typeof sections] || overview
);
let contactRevision = 0;
watch(
  () => [route.query.tab, route.query.contactUserId],
  async () => {
    const revision = ++contactRevision;
    if (route.query.tab !== "chat") return;
    const id = queryText(route.query.contactUserId);
    if (!id) {
      chat.openContact();
      return;
    }
    if (id === String(GET_ID())) return;
    const contact = {
      id,
      userName: queryText(route.query.contactName) || "对方用户",
      userAvatar: queryText(route.query.contactAvatar)
    };
    if (!route.query.contactName) {
      try {
        const res = await getUserVoByIdUsingGet({ id });
        if (res.code === 200 && res.data) {
          contact.userName = res.data.userName || contact.userName;
          contact.userAvatar = res.data.userAvatar || "";
        }
      } catch {
        /* Contact ID remains usable when profile lookup fails. */
      }
    }
    if (revision === contactRevision) chat.openContact(contact);
  },
  { immediate: true }
);
</script>
<style scoped lang="scss">
.account-space {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: clamp(28px, 5vw, 72px);
  width: min(1240px, 100%);
  margin: 24px auto;
}
.account-navigation {
  padding-top: 10px;
  > p {
    margin: 0 0 26px;
    padding: 0 12px;
    color: var(--market-muted);
    font-size: var(--market-meta-size, 13px);
  }
  nav {
    display: grid;
    gap: 8px;
  }
  a {
    position: relative;
    min-height: 44px;
    padding: 11px 12px;
    display: flex;
    align-items: center;
    font-size: 15px;
    color: var(--market-muted);
    border-radius: 8px;
    transition: color 150ms, background 150ms;
    &:hover {
      color: var(--market-ink);
      background: var(--market-surface-soft);
    }
    &.active {
      color: var(--market-primary);
      font-weight: 650;
      &::before {
        position: absolute;
        left: 0;
        width: 3px;
        height: 16px;
        border-radius: 3px;
        background: currentColor;
        content: "";
      }
    }
  }
}
.account-body {
  min-width: 0;
}
.account-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
  &.on-overview {
    display: none;
  }
}
.account-back {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  color: var(--market-muted);
  font-size: 13px;
}
.account-mobile-switch {
  display: none;
}
.account-switch-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  border: 0;
  background: transparent;
  padding: 0;
  color: var(--market-ink);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
@media (max-width: 760px) {
  .account-space {
    display: block;
    margin: 0;
  }
  .account-navigation {
    display: none;
  }
  .account-mobile-switch {
    display: inline-flex;
    margin-left: auto;
  }
  .account-context {
    margin-bottom: 18px;
    &.on-overview {
      display: flex;
      margin-bottom: 12px;
      .account-mobile-switch {
        margin-left: 0;
      }
    }
  }
}
</style>
