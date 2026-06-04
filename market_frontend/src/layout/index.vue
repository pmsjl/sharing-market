<template>
  <div class="layout_container">
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
      <main class="layout_main">
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
  min-height: 100dvh;
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
  box-shadow: 8px 0 28px rgba(62, 45, 24, 0.08);
  transition: flex-basis 0.24s ease, width 0.24s ease;
  z-index: 20;

  &.fold {
    flex-basis: $base-menu-min-width;
    width: $base-menu-min-width;
  }

  .scrollbar {
    height: calc(100dvh - $base-menu-logo-height);
  }

  :deep(.el-menu) {
    border-right: none;
    padding: 10px;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 46px;
    margin: 4px 0;
    border-radius: 8px;
    font-weight: 700;
  }

  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    background: var(--market-menu-hover-bg);
  }

  :deep(.el-menu-item.is-active) {
    color: var(--market-green);
    background: var(--market-menu-active-bg);
  }
}

.layout_content {
  flex: 1;
  min-width: 0;
  transition: all 0.24s ease;
}

.layout_tabbar {
  position: sticky;
  top: 0;
  height: $base-tabbar-height;
  border-bottom: 1px solid var(--market-line);
  background: var(--market-topbar-bg);
  backdrop-filter: blur(18px);
  z-index: 18;
}

.layout_main {
  height: calc(100dvh - $base-tabbar-height);
  padding: 22px;
  overflow: auto;
}

@media (max-width: 768px) {
  .layout_container {
    display: block;
  }

  .layout_slider {
    position: fixed;
    left: 0;
    transform: translateX(-100%);
  }

  .layout_slider.fold {
    transform: translateX(0);
  }

  .layout_main {
    height: calc(100dvh - $base-tabbar-height);
    padding: 14px;
  }
}
</style>
