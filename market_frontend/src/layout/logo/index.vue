<template>
  <div class="logo" v-if="setting.logoHidden">
    <div class="logo-mark">
      <img :src="setting.logo" alt="校园二手平台" />
    </div>
    <div class="logo-copy">
      <p>{{ setting.title }}</p>
      <span>Campus Market</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import setting from "@/setting";
</script>
<script lang="ts">
export default {
  name: "Logo"
};
</script>
<style scoped lang="scss">
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: $base-menu-logo-height;
  padding: 14px;
  color: var(--market-ink);
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
    letter-spacing: 0;
  }
}

:global(.layout_slider.fold) .logo {
  justify-content: center;
  padding-inline: 10px;
}

:global(.layout_slider.fold) .logo-copy {
  display: none;
}
</style>
