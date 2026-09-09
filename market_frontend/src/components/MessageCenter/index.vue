<template>
  <el-drawer
    v-model="chat.opened"
    title="校园私信"
    direction="rtl"
    size="min(860px, 100vw)"
    class="campus-message-drawer"
    append-to-body
    destroy-on-close
  >
    <template #header
      ><div class="message-drawer-heading">
        <span class="market-eyebrow">CAMPUS MAIL</span>
        <h2>校园私信 <span>同学之间，随时聊聊</span></h2>
      </div></template
    >
    <PrivateMessage />
  </el-drawer>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount } from "vue";
import PrivateMessage from "@/components/PrivateMessage/index.vue";
import usePrivateMessageStore from "@/store/modules/privateMessage";
import { GET_ID } from "@/utils/token";
const chat = usePrivateMessageStore();
let timer: ReturnType<typeof setInterval>;
const poll = () => {
  if (document.visibilityState === "visible") void chat.refresh();
};
const syncStorage = (event: StorageEvent) => {
  if (event.key === `market:private-read:${chat.owner}`) chat.syncReadStorage();
};
onMounted(() => {
  chat.reset(GET_ID() || "");
  poll();
  timer = setInterval(poll, 8000);
  document.addEventListener("visibilitychange", poll);
  window.addEventListener("online", poll);
  window.addEventListener("storage", syncStorage);
});
onBeforeUnmount(() => {
  clearInterval(timer);
  document.removeEventListener("visibilitychange", poll);
  window.removeEventListener("online", poll);
  window.removeEventListener("storage", syncStorage);
  chat.reset();
});
</script>
<style lang="scss">
.campus-message-drawer {
  color: var(--market-ink);
  background: var(--market-paper);
  .el-drawer__header {
    margin: 0;
    padding: 20px 22px;
    border-bottom: 1px dashed var(--market-line-strong);
    color: var(--market-ink);
    background: var(--market-surface);
  }
  .el-drawer__body {
    padding: 0;
    overflow: hidden;
  }
  .el-drawer__close-btn {
    min-width: 44px;
    min-height: 44px;
  }
}
.message-drawer-heading h2 {
  margin: 5px 0 0;
  font-family: var(--market-font-display);
  font-size: 25px;
  span {
    margin-left: 12px;
    font-family: var(--market-font-body);
    font-size: 12px;
    color: var(--market-muted);
  }
}
.campus-message-notification {
  cursor: pointer;
  border: 1px solid var(--market-line);
  border-left: 4px solid var(--market-orange);
  background: var(--market-surface);
  .el-notification__title,
  .el-notification__content {
    color: var(--market-ink);
  }
}
.campus-message-notification-action {
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  &:focus-visible {
    outline: 2px solid var(--market-primary);
    outline-offset: 3px;
  }
}
@media (max-width: 560px) {
  .message-drawer-heading h2 span {
    display: none;
  }
}
</style>
