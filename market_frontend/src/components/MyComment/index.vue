<template>
  <AsyncState
    :loading="comments.loading.value"
    :error="comments.error.value"
    @retry="comments.load"
  >
    <p v-if="!comments.data.value.length" class="quiet-empty">
      还没有留下评论，去和同学聊聊经验吧。
    </p>
    <article
      v-for="item in comments.data.value"
      :key="item.id"
      class="comment-activity"
    >
      <p>{{ item.content }}</p>
      <router-link v-if="!isAdmin" :to="`/user/post/${item.postId}`"
        >回应「{{ item.postTitle || "原攻略" }}」 ↗</router-link
      ><span v-else class="comment-reference"
        >回应「{{ item.postTitle || "原攻略" }}」</span
      ><time>{{ formatDate(item.updateTime) }}</time>
    </article>
  </AsyncState>
</template>
<script setup lang="ts">
import { GET_ROLE } from "@/utils/token";
const isAdmin = GET_ROLE() === "admin";
import { onMounted } from "vue";
import dayjs from "dayjs";
import AsyncState from "@/components/AsyncState/index.vue";
import { useRemote, responseData } from "@/composables/useRemote";
import { listMyCommentsUsingPost } from "@/api/commentController";
const comments = useRemote<any[]>([], async () =>
  responseData(await listMyCommentsUsingPost())
);
const formatDate = (value: string) => dayjs(value).format("YYYY-MM-DD HH:mm");
onMounted(() => void comments.load());
</script>
<style scoped lang="scss">
.comment-activity {
  padding: 24px 0;
  & + & {
    margin-top: 8px;
  }
  p {
    font-size: 16px;
    line-height: 1.9;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    margin: 0 0 12px;
  }
  a,
  .comment-reference {
    display: block;
    color: var(--market-muted);
    font-size: 13px;
    border-left: 2px solid var(--market-line);
    padding-left: 12px;
    line-height: 1.7;
  }
  time {
    display: block;
    color: var(--market-muted);
    font-size: 12px;
    margin-top: 14px;
  }
}
</style>
