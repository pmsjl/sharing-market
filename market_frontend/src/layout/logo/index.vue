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
  gap: 10px;
  width: 100%;
  height: $base-menu-logo-height;
  padding: 14px;
  color: var(--market-ink);
}

.fold-pin {
  position: absolute;
  right: 9px;
  bottom: -8px;
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: var(--market-orange);
  box-shadow: 0 5px 12px rgba(62, 45, 24, 0.24);
  cursor: pointer;
  transform: rotate(-9deg);
  transition: transform var(--market-dur-fast) var(--market-ease-spring),
    box-shadow var(--market-dur-fast) ease;
  z-index: 4;

  &::before {
    width: 14px;
    height: 10px;
    border-radius: 50% 50% 42% 42%;
    background: currentColor;
    content: "";
  }

  span {
    position: absolute;
    top: 16px;
    width: 2px;
    height: 9px;
    border-radius: 999px;
    background: currentColor;
  }

  &:hover {
    box-shadow: 0 7px 15px rgba(62, 45, 24, 0.3);
    transform: translateY(-2px) rotate(0);
  }
}

.logo-mark {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid rgba(217, 108, 44, 0.22);
  border-radius: 8px;
  background: #fffdf8;
  box-shadow: var(--market-shadow-soft);

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
    line-height: 1.15;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    display: block;
    margin-top: 3px;
    color: var(--market-orange);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 2px;
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
  right: 2px;
  transform: rotate(9deg);
}
</style>
