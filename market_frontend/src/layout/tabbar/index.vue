<template>
  <div class="tabbar">
    <div class="tabbar_left">
      <Breadcrumb />
    </div>
    <div class="tabbar_right">
      <Setting />
    </div>
  </div>
</template>

<script setup lang="ts">
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

@media (max-width: 768px) {
  .tabbar {
    padding: 0 14px 0 86px;
  }
}
</style>
