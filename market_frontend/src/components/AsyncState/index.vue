<template>
  <div v-if="loading" class="quiet-state" role="status">
    <el-skeleton :rows="3" animated /><span>正在加载…</span>
  </div>
  <div v-else-if="error" class="quiet-state" role="alert">
    <p>{{ error }}</p>
    <el-button @click="$emit('retry')">重新加载</el-button>
  </div>
  <slot v-else />
</template>
<script setup lang="ts">
defineProps<{ loading: boolean; error: string }>();
defineEmits<{ (event: "retry"): void }>();
</script>
