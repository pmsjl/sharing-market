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
    gap: 6px;
    color: var(--market-muted);
    font-weight: 700;
  }
  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    padding: 5px 10px;
    border: 1px solid var(--market-line);
    border-radius: 7px 11px 7px 11px;
    color: var(--market-primary);
    background: var(--market-primary-soft);
  }
  :deep(.el-breadcrumb__separator) {
    width: 18px;
    margin: 0 6px;
    color: var(--market-orange);
    font-size: 0;
  }
  :deep(.el-breadcrumb__separator)::after {
    font-size: 15px;
    content: "→";
  }
}
@media (max-width: 600px) {
  .chalk-breadcrumb :deep(.el-breadcrumb__item:not(:last-child)) {
    display: none;
  }
}
</style>
