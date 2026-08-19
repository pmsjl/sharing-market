<template>
  <div
    class="layout_container"
    :class="{
      'focus-mode': $route.meta.workspace && LayOutSettingStore.focusMode
    }"
  >
    <aside
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

    <section
      class="layout_content"
      :class="{ fold: LayOutSettingStore.fold ? true : false }"
    >
      <header class="layout_tabbar">
        <Tabbar />
      </header>
      <main
        class="layout_main"
        :class="{ 'workspace-mode': $route.meta.workspace }"
      >
        <Main />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import Tabbar from "./tabbar/index.vue";
import { useRoute } from "vue-router";
import Logo from "./logo/index.vue";
import Menu from "./menu/index.vue";
import Main from "./main/index.vue";
import userUserStore from "@/store/modules/user";
import useLayOutSettingStore from "@/store/modules/setting";

const userStore = userUserStore();
const LayOutSettingStore = useLayOutSettingStore();
const $route = useRoute();
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
  background: var(--market-body-bg);
}
.layout_slider {
  position: sticky;
  top: 0;
  flex: 0 0 $base-menu-width;
  width: $base-menu-width;
  height: 100dvh;
  border-right: 1px solid var(--market-line);
  background: var(--market-sidebar-bg);
  box-shadow: 8px 0 30px rgba(30, 64, 109, 0.08);
  transition: flex-basis 0.24s var(--market-ease-standard),
    width 0.24s var(--market-ease-standard);
  z-index: 20;
  &::before {
    display: block;
    width: 100%;
    height: 7px;
    background: linear-gradient(
      90deg,
      var(--market-primary) 0 60%,
      var(--market-orange) 60% 76%,
      var(--market-yellow) 76% 100%
    );
    content: "";
  }
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
    padding: 10px 11px 18px;
  }
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    position: relative;
    height: 47px;
    margin: 5px 0;
    border: 1px solid transparent;
    border-radius: 9px 13px 9px 13px;
    color: var(--market-muted);
    font-weight: 720;
    transition: transform var(--market-dur-fast), color var(--market-dur-fast),
      background var(--market-dur-fast), border-color var(--market-dur-fast);
  }
  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    border-color: rgba(37, 99, 235, 0.09);
    color: var(--market-primary);
    background: var(--market-menu-hover-bg);
    transform: translateX(3px);
  }
  :deep(.el-menu-item.is-active) {
    border-color: rgba(37, 99, 235, 0.18);
    color: var(--market-primary);
    background: var(--market-menu-active-bg);
    box-shadow: 0 7px 18px rgba(37, 99, 235, 0.08);
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
    &::before {
      position: absolute;
      right: 10px;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--market-orange);
      box-shadow: 0 0 0 4px var(--market-orange-soft);
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
    position: fixed;
    left: 0;
    transform: translateX(-100%);
    transition: transform 0.24s var(--market-ease-standard);
  }
  .layout_slider.fold {
    transform: translateX(0);
  }
  .layout_main {
    padding: 15px;
  }
  .layout_main.workspace-mode {
    padding: 0;
  }
}
</style>
