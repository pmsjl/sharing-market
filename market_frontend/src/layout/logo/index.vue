<template>
  <div class="logo" v-if="setting.logoHidden">
    <div class="logo-mark">
      <img :src="setting.logo" alt="校园二手平台" />
    </div>
    <div class="logo-copy">
      <p>{{ setting.title }}</p>
      <span>Campus Market</span>
    </div>
    <button
      class="fold-pin"
      type="button"
      :aria-label="layoutSetting.fold ? '展开侧边栏' : '折叠侧边栏'"
      :title="layoutSetting.fold ? '展开侧边栏' : '折叠侧边栏'"
      @click="layoutSetting.fold = !layoutSetting.fold"
    >
      <span aria-hidden="true"></span>
    </button>
  </div>
</template>

<script setup lang="ts">
import setting from "@/setting";
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
  border-radius: 10px 14px 10px 14px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  transform: rotate(-2deg);
  &::after {
    position: absolute;
    right: -3px;
    bottom: -3px;
    width: 11px;
    height: 11px;
    border: 2px solid var(--market-surface);
    border-radius: 50%;
    background: var(--market-yellow);
    content: "";
  }
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
    font-weight: 900;
    line-height: 1.12;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  span {
    display: block;
    margin-top: 4px;
    color: var(--market-primary);
    font-family: var(--market-font-mono);
    font-size: 9px;
    font-weight: 800;
    letter-spacing: 1.7px;
  }
}
.fold-pin {
  position: absolute;
  right: 8px;
  bottom: -8px;
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  place-items: center;
  border: 2px solid var(--market-surface);
  border-radius: 50%;
  color: #fff;
  background: var(--market-orange);
  box-shadow: 0 6px 14px rgba(249, 115, 22, 0.25);
  cursor: pointer;
  transform: rotate(-8deg);
  transition: transform var(--market-dur-fast) var(--market-ease-spring),
    box-shadow var(--market-dur-fast);
  z-index: 4;
  &::before {
    width: 13px;
    height: 9px;
    border-radius: 50% 50% 42% 42%;
    background: currentColor;
    content: "";
  }
  span {
    position: absolute;
    top: 16px;
    width: 2px;
    height: 8px;
    border-radius: 999px;
    background: currentColor;
  }
  &:hover {
    box-shadow: 0 9px 18px rgba(249, 115, 22, 0.3);
    transform: translateY(-2px) rotate(0);
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
  right: 1px;
  transform: rotate(8deg);
}
</style>
