<template>
  <div
    class="layout_container"
    :style="{ '--market-viewport-height': `${viewportHeight}px` }"
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
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import Logo from "./logo/index.vue";
import Menu from "./menu/index.vue";
import Main from "./main/index.vue";
import userUserStore from "@/store/modules/user";
import useLayOutSettingStore from "@/store/modules/setting";

const userStore = userUserStore();
const LayOutSettingStore = useLayOutSettingStore();
const $route = useRoute();
const viewportHeight = ref(window.innerHeight);

let viewportResizeFrame: number | null = null;

const getUsableViewportHeight = () => {
  const innerHeight = window.innerHeight;
  const visualHeight = window.visualViewport?.height || innerHeight;
  let usableHeight = Math.min(innerHeight, visualHeight);

  const availableScreenHeight =
    window.screen.availHeight - Math.max(0, window.screenY);
  const screenHeightLooksReliable =
    availableScreenHeight > 0 &&
    availableScreenHeight < usableHeight &&
    availableScreenHeight >= usableHeight * 0.8;

  if (screenHeightLooksReliable) {
    usableHeight = availableScreenHeight;
  }

  return Math.max(1, Math.floor(usableHeight));
};

const updateViewportHeight = () => {
  viewportHeight.value = getUsableViewportHeight();
};

const scheduleViewportHeightUpdate = () => {
  if (viewportResizeFrame != null) return;
  viewportResizeFrame = window.requestAnimationFrame(() => {
    viewportResizeFrame = null;
    updateViewportHeight();
  });
};

onMounted(() => {
  updateViewportHeight();
  window.addEventListener("resize", scheduleViewportHeightUpdate);
  window.visualViewport?.addEventListener(
    "resize",
    scheduleViewportHeightUpdate
  );
  document.addEventListener("fullscreenchange", scheduleViewportHeightUpdate);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", scheduleViewportHeightUpdate);
  window.visualViewport?.removeEventListener(
    "resize",
    scheduleViewportHeightUpdate
  );
  document.removeEventListener(
    "fullscreenchange",
    scheduleViewportHeightUpdate
  );
  if (viewportResizeFrame != null) {
    window.cancelAnimationFrame(viewportResizeFrame);
    viewportResizeFrame = null;
  }
});
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
  height: var(--market-viewport-height, 100dvh);
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
  box-shadow: 8px 0 28px rgba(62, 45, 24, 0.08);
  transition: flex-basis 0.24s ease, width 0.24s ease;
  z-index: 20;
  @include paper-grain(0.3);

  &::before {
    height: 6px;
    @include awning-strip(6px);
    content: "";
    display: block;
    position: relative;
    z-index: 1;
  }

  &.fold {
    flex-basis: $base-menu-min-width;
    width: $base-menu-min-width;
  }

  .scrollbar {
    height: calc(100dvh - $base-menu-logo-height - 6px);
    position: relative;
    z-index: 1;
  }

  :deep(.el-menu) {
    border-right: none;
    padding: 10px;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    position: relative;
    height: 46px;
    margin: 4px 0;
    border-radius: 8px;
    font-weight: 700;
    transition: transform var(--market-dur-fast) ease;
  }

  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    background: var(--market-menu-hover-bg);
    transform: translateY(-2px);
  }

  // 激活项：左侧橙色邮票齿孔条 + 浅绿底
  :deep(.el-menu-item.is-active) {
    color: var(--market-green);
    background: var(--market-menu-active-bg);

    &::before {
      position: absolute;
      top: 8px;
      bottom: 8px;
      left: 0;
      width: 4px;
      border-radius: 0 4px 4px 0;
      background-image: radial-gradient(
        circle 1.6px at 2px 3px,
        var(--market-orange) 1.6px,
        transparent 1.6px
      );
      background-size: 4px 7px;
      background-color: rgba(224, 101, 31, 0.35);
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
  backdrop-filter: blur(18px);
  z-index: 18;
}

.layout_main {
  min-width: 0;
  min-height: 0;
  padding: 22px;
  overflow: auto;

  &.workspace-mode {
    display: grid;
    grid-template-rows: minmax(0, 1fr);
    height: 100%;
    max-height: 100%;
    padding: 12px;
    overflow: hidden;
  }
}

.layout_container.focus-mode {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  height: var(--market-viewport-height, 100dvh);

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
    transition: transform 0.24s ease;
  }

  .layout_slider.fold {
    transform: translateX(0);
  }

  .layout_main {
    padding: 14px;

    &.workspace-mode {
      padding: 0;
    }
  }
}
</style>
