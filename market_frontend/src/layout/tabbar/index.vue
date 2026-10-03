<template>
  <div class="tabbar">
    <div class="tabbar_left">
      <button
        type="button"
        class="admin-navigation-toggle"
        aria-label="打开管理导航"
        :aria-expanded="navigationOpen"
        @click="$emit('open-navigation')"
      >
        <el-icon><MenuIcon /></el-icon>
      </button>
      <Breadcrumb />
    </div>
    <div class="tabbar_right">
      <Setting />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Menu as MenuIcon } from "@element-plus/icons-vue";
defineProps<{ navigationOpen: boolean }>();
defineEmits<{ (event: "open-navigation"): void }>();
import Breadcrumb from "./breadcrumb/index.vue";
import Setting from "./setting/index.vue";
</script>
<script lang="ts">
export default {
  name: "Tabbar"
};
</script>
<style scoped lang="scss">
.tabbar {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 0 22px;

  .tabbar_left {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .tabbar_right {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.admin-navigation-toggle {
  display: none;
}
@media (max-width: 768px) {
  .admin-navigation-toggle {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    width: 40px;
    height: 44px;
    border: 0;
    background: transparent;
    color: var(--market-ink);
    font-size: 20px;
    cursor: pointer;
  }
  .tabbar .tabbar_left {
    gap: 6px;
  }
  .tabbar {
    padding: 0 12px;
    gap: 8px;
  }
}
</style>
