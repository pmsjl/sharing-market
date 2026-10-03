<template>
  <div
    v-if="userStore.token"
    class="layout_container"
    :class="{
      'market-consumer': isConsumer,
      'market-admin': !isConsumer,
      'focus-mode': $route.meta.workspace && LayOutSettingStore.focusMode
    }"
  >
    <aside
      v-if="!isConsumer"
      class="layout_slider"
      :class="{ fold: LayOutSettingStore.fold ? true : false }"
    >
      <Logo />
      <el-scrollbar class="scrollbar">
        <el-menu
          :collapse="LayOutSettingStore.fold ? true : false"
          :default-active="$route.path"
          background-color="transparent"
          text-color="var(--market-ink)"
          active-text-color="var(--market-green)"
        >
          <Menu :menuList="userStore.menuRoutes"></Menu>
        </el-menu>
      </el-scrollbar>
    </aside>

    <el-drawer
      v-if="!isConsumer"
      v-model="adminNavigationOpen"
      title="校园集市 · 管理导航"
      direction="ltr"
      size="min(300px, 88vw)"
      append-to-body
    >
      <el-menu
        :default-active="$route.path"
        :default-openeds="['/admin']"
        @select="adminNavigationOpen = false"
        ><Menu :menuList="userStore.menuRoutes"
      /></el-menu>
    </el-drawer>

    <section
      class="layout_content"
      :class="{ fold: LayOutSettingStore.fold ? true : false }"
    >
      <header class="layout_tabbar">
        <MarketNavigation v-if="isConsumer" />
        <Tabbar
          v-else
          :navigation-open="adminNavigationOpen"
          @open-navigation="adminNavigationOpen = true"
        />
      </header>
      <main
        id="market-main"
        tabindex="-1"
        class="layout_main"
        :class="{ 'workspace-mode': $route.meta.workspace }"
      >
        <Main />
      </main>
      <MarketNavigation v-if="isConsumer" mobile />
    </section>
  </div>
</template>

<script setup lang="ts">
import Tabbar from "./tabbar/index.vue";
import MarketNavigation from "./MarketNavigation.vue";
import { computed, ref, watch, nextTick } from "vue";
import { GET_ROLE } from "@/utils/token";
import { useRoute } from "vue-router";
import Logo from "./logo/index.vue";
import Menu from "./menu/index.vue";
import Main from "./main/index.vue";
import userUserStore from "@/store/modules/user";
import useLayOutSettingStore from "@/store/modules/setting";

const userStore = userUserStore();
const LayOutSettingStore = useLayOutSettingStore();
const $route = useRoute();
const adminNavigationOpen = ref(false);
const isConsumer = computed(() => {
  // Read the token as a dependency so a role switch updates the shell.
  return Boolean(userStore.token) && GET_ROLE() === "user";
});
watch(
  () => $route.path,
  async () => {
    adminNavigationOpen.value = false;
    await nextTick();
    document.getElementById("market-main")?.scrollTo({ top: 0 });
  }
);
</script>
<script lang="ts">
export default {
  name: "Layout"
};
</script>
<style scoped lang="scss">
.layout_container {
  display: flex;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  min-height: 0;
  overflow: hidden;
  background: var(--market-canvas);
}
.layout_slider {
  position: sticky;
  top: 0;
  flex: 0 0 $base-menu-width;
  width: $base-menu-width;
  height: 100dvh;
  border-right: 1px solid var(--market-line);
  background: var(--market-sidebar-bg);
  box-shadow: none;
  transition: flex-basis 0.24s var(--market-ease-standard),
    width 0.24s var(--market-ease-standard);
  z-index: 20;

  &.fold {
    flex-basis: $base-menu-min-width;
    width: $base-menu-min-width;
  }
  .scrollbar {
    position: relative;
    height: calc(100dvh - $base-menu-logo-height - 7px);
  }
  :deep(.el-menu) {
    border-right: none;
    padding: 36px 11px 18px;
  }
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    position: relative;
    height: 47px;
    margin: 5px 0;
    border: 1px solid transparent;
    border-radius: 10px;
    color: var(--market-muted);
    font-weight: 500;
    transition: transform var(--market-dur-fast), color var(--market-dur-fast),
      background var(--market-dur-fast), border-color var(--market-dur-fast);
  }
  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    border-color: transparent;
    color: var(--market-primary);
    background: var(--market-menu-hover-bg);
  }
  :deep(.el-menu-item.is-active) {
    border-color: transparent;
    color: var(--market-primary);
    background: var(--market-menu-active-bg);
    font-weight: 600;
    &::after {
      position: absolute;
      top: 9px;
      bottom: 9px;
      left: -1px;
      width: 4px;
      border-radius: 0 5px 5px 0;
      background: var(--market-primary);
      content: "";
    }
  }
}
.layout_content {
  display: grid;
  grid-template-rows: $base-tabbar-height minmax(0, 1fr);
  flex: 1;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.layout_tabbar {
  position: relative;
  min-width: 0;
  min-height: 0;
  border-bottom: 1px solid var(--market-line);
  background: var(--market-topbar-bg);
  backdrop-filter: blur(18px) saturate(1.2);
  z-index: 18;
}
.layout_main {
  min-width: 0;
  min-height: 0;
  padding: 24px;
  overflow: auto;
  scrollbar-color: var(--market-line-strong) transparent;
}
.layout_main.workspace-mode {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  height: 100%;
  max-height: 100%;
  padding: 12px;
  overflow: hidden;
}
.layout_container.focus-mode {
  position: fixed;
  inset: 0;
  width: 100%;
  height: auto;
  max-height: none;
  .layout_slider,
  .layout_tabbar {
    display: none;
  }
  .layout_content {
    grid-template-rows: minmax(0, 1fr);
  }
  .layout_main {
    padding: 0;
    overflow: hidden;
  }
}
@media (max-width: 768px) {
  .layout_container {
    display: block;
  }
  .layout_slider {
    display: none;
  }
  .layout_main {
    padding: 15px;
  }
  .layout_main.workspace-mode {
    padding: 0;
  }
}
</style>
