<template>
  <div class="logo" v-if="setting.logoHidden">
    <div class="logo-mark">
      <img :src="setting.logo" alt="校园二手平台" />
    </div>
    <div class="logo-copy">
      <p>{{ setting.title }}</p>
      <span>管理工作台</span>
    </div>
    <button
      class="fold-pin"
      type="button"
      :aria-label="layoutSetting.fold ? '展开侧边栏' : '折叠侧边栏'"
      :title="layoutSetting.fold ? '展开侧边栏' : '折叠侧边栏'"
      @click="layoutSetting.fold = !layoutSetting.fold"
    >
      <el-icon><Expand v-if="layoutSetting.fold" /><Fold v-else /></el-icon>
    </button>
  </div>
</template>

<script setup lang="ts">
import setting from "@/setting";
import { Expand, Fold } from "@element-plus/icons-vue";
import useLayOutSettingStore from "@/store/modules/setting";

const layoutSetting = useLayOutSettingStore();
</script>
<script lang="ts">
export default {
  name: "Logo"
};
</script>
<style scoped lang="scss">
.logo {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  height: $base-menu-logo-height;
  padding: 13px 14px;
  color: var(--market-ink);
}
.logo-mark {
  position: relative;
  display: grid;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  background: var(--market-surface);
  img {
    width: 32px;
    height: 32px;
    object-fit: contain;
  }
}
.logo-copy {
  min-width: 0;
  p {
    overflow: hidden;
    margin: 0;
    font-family: var(--market-font-display);
    font-size: $base-logo-title-fontSize;
    font-weight: 700;
    line-height: 1.12;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  span {
    display: block;
    margin-top: 4px;
    color: var(--market-muted);
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 1px;
  }
}
.fold-pin {
  position: absolute;
  right: 8px;
  bottom: -28px;
  display: grid;
  width: 44px;
  height: 44px;
  padding: 0;
  place-items: center;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  color: var(--market-muted);
  background: var(--market-surface);
  cursor: pointer;
  transition: color var(--market-dur-fast), background var(--market-dur-fast);
  z-index: 4;
  .el-icon {
    font-size: 18px;
  }
  &:hover {
    color: var(--market-primary);
    background: var(--market-primary-soft);
  }
}
:global(.layout_slider.fold) .logo {
  justify-content: center;
  padding-inline: 10px;
}
:global(.layout_slider.fold) .logo-copy {
  display: none;
}
:global(.layout_slider.fold) .fold-pin {
  right: 14px;
}
</style>
