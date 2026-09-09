<template>
  <div class="market-app">
    <router-view />
    <MessageCenter v-if="userStore.token" :key="userStore.token" />
  </div>
</template>

<script setup lang="ts">
import MessageCenter from "@/components/MessageCenter/index.vue";
import useUserStore from "@/store/modules/user";
const userStore = useUserStore();
</script>

<style lang="scss">
.market-app {
  min-height: 100vh;
}
</style>
