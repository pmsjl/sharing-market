<template>
  <!-- 顶部左侧面包屑 -->
  <el-breadcrumb class="chalk-breadcrumb" separator="">
    <!-- 面包动态展示路由名字与标题 -->
    <el-breadcrumb-item
      v-for="(item, index) in $route.matched"
      :key="index"
      v-show="item.meta.title"
      :to="item.path"
    >
      <!-- 图标 -->
      <el-icon>
        <component :is="item.meta.icon"></component>
      </el-icon>
      <!-- 面包屑展示匹配路由的标题 -->
      <span>{{ item.meta.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";

let $route = useRoute();
</script>
<script lang="ts">
export default {
  name: "Breadcrumb"
};
</script>
<style scoped lang="scss">
.chalk-breadcrumb {
  min-width: 0;

  :deep(.el-breadcrumb__inner) {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--market-muted);
    font-weight: 700;
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--market-ink);
  }

  :deep(.el-breadcrumb__separator) {
    width: 15px;
    height: 20px;
    margin: 0 8px;
    overflow: hidden;
    color: transparent;
    background: center / 13px 18px no-repeat
      url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 14 20'%3E%3Cpath d='M10.8 1.7C8.3 6.5 6.5 10.6 3.1 18.3' fill='none' stroke='%23e0651f' stroke-width='2.1' stroke-linecap='round'/%3E%3Cpath d='M11.8 2.4C9.4 7 7.5 11.2 4.2 17.7' fill='none' stroke='%23e0651f' stroke-opacity='.34' stroke-width='.9' stroke-linecap='round'/%3E%3C/svg%3E");
  }
}

@media (max-width: 600px) {
  .chalk-breadcrumb :deep(.el-breadcrumb__item:not(:last-child)) {
    display: none;
  }
}
</style>
