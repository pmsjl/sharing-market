<template>
  <section>
    <header class="quiet-heading content-heading">
      <div>
        <h1>我的内容</h1>
        <p>写过的经验、收藏的攻略，还有你留下的讨论。</p>
      </div>
      <router-link
        v-if="!isAdmin && view === 'posts'"
        class="editorial-action"
        :to="{ path: '/user/post/new', query: { returnTo: route.fullPath } }"
        >＋ 写一篇攻略</router-link
      >
      <router-link
        v-else-if="isAdmin"
        class="quiet-link"
        to="/admin/postManagement"
        >前往攻略管理</router-link
      >
    </header>
    <nav class="quiet-tabs" aria-label="个人内容分类">
      <router-link
        v-for="item in tabs"
        :key="item.value"
        :class="{ active: view === item.value }"
        :to="{ path: route.path, query: { view: item.value } }"
        >{{ item.label }}</router-link
      >
    </nav>
    <MyPost v-if="view === 'posts'" /><Post
      v-else-if="view === 'favorites'"
    /><MyComment v-else />
  </section>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import MyPost from "@/components/MyPost/index.vue";
import Post from "@/components/Post/index.vue";
import MyComment from "@/components/MyComment/index.vue";
import { GET_ROLE } from "@/utils/token";
const isAdmin = GET_ROLE() === "admin";
const route = useRoute();
const tabs = [
  { value: "posts", label: "我的攻略" },
  { value: "favorites", label: "收藏攻略" },
  { value: "comments", label: "我的评论" }
];
const view = computed(() =>
  tabs.some((t) => t.value === route.query.view) ? route.query.view : "posts"
);
</script>

<style scoped lang="scss">
.content-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
@media (max-width: 600px) {
  .content-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }
}
</style>
