<template>
  <div class="journal-browse">
    <div class="journal-toolbar">
      <el-input
        v-model="searchText"
        placeholder="搜索同学的经验"
        clearable
        aria-label="搜索攻略"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
        ><template #append
          ><el-button
            :icon="Search"
            aria-label="搜索"
            @click="handleSearch" /></template
      ></el-input>
    </div>
    <PostTagFilter
      v-model="filterTags"
      v-model:mode="tagMatchMode"
      input-id="favourite-post-tags"
      @change="handleSearch"
    />
    <div v-if="loadError" class="quiet-state" role="alert">
      {{ loadError }}<el-button @click="getPostList">重试</el-button>
    </div>
    <template v-else
      ><div class="journal-list" v-loading="loading">
        <PostPreview
          v-for="post in postList"
          :key="post.id"
          :post="post"
          :linkable="!isAdmin"
        /><el-empty
          v-if="!loading && !postList.length"
          description="还没有符合条件的攻略，换个关键词试试"
        />
      </div>
      <div class="journal-pagination">
        <el-pagination
          small
          layout="total, prev, pager, next"
          :pager-count="5"
          :current-page="paginationConfig.current"
          :total="paginationConfig.total"
          :page-size="paginationConfig.pageSize"
          @current-change="handlePageChange"
        /></div
    ></template>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { Search } from "@element-plus/icons-vue";
import { ElButton, ElPagination } from "element-plus";

import PostTagFilter from "@/components/PostTagFilter/index.vue";
import PostPreview from "@/components/PostPreview/index.vue";
import { listMyFavourPostByPageUsingPost } from "@/api/postFavourController";

// 搜索文本
import { useRoute, useRouter } from "vue-router";
import { queryPage, queryText } from "@/utils/marketNavigation";
import { GET_ROLE } from "@/utils/token";
const isAdmin = GET_ROLE() === "admin";
const route = useRoute(),
  router = useRouter();
const loadError = ref("");
const searchText = ref("");
const filterTags = ref<string[]>([]);
const tagMatchMode = ref<"all" | "any">("all");
const loading = ref(false);
let querySequence = 0;

// 帖子列表
const postList = ref<API.PostVO[]>([]);

// 分页配置
const paginationConfig = ref({
  current: 1,
  pageSize: 10,
  total: 0
});

// 获取帖子列表
const getPostList = async () => {
  const sequence = ++querySequence;
  loading.value = true;
  loadError.value = "";
  try {
    const res = await listMyFavourPostByPageUsingPost({
      title: searchText.value,
      tags: tagMatchMode.value === "all" ? filterTags.value : [],
      orTags: tagMatchMode.value === "any" ? filterTags.value : [],
      current: paginationConfig.value.current,
      pageSize: paginationConfig.value.pageSize
    });
    if (sequence !== querySequence) return;
    if (res.code === 200) {
      postList.value = res.data.records || [];
      paginationConfig.value.total = parseInt(res.data.total);
    } else {
      loadError.value = "获取攻略失败，请重试";
    }
  } catch (error) {
    if (sequence === querySequence) loadError.value = "获取攻略失败，请重试";
  } finally {
    if (sequence === querySequence) loading.value = false;
  }
};

// 处理搜索
const handleSearch = () => {
  paginationConfig.value.current = 1;
  void syncQuery();
};

// 处理分页
const handlePageChange = (page: number) => {
  paginationConfig.value.current = page;
  void syncQuery();
};

const syncQuery = () =>
  router.push({
    query: {
      ...route.query,
      q: searchText.value || undefined,
      tags: filterTags.value,
      mode: tagMatchMode.value,
      page: paginationConfig.value.current
    }
  });
watch(
  () => route.query,
  (query) => {
    searchText.value = queryText(query.q);
    filterTags.value = (
      Array.isArray(query.tags) ? query.tags : query.tags ? [query.tags] : []
    ).filter((item): item is string => typeof item === "string");
    tagMatchMode.value = query.mode === "any" ? "any" : "all";
    paginationConfig.value.current = queryPage(query.page);
    void getPostList();
  },
  { immediate: true }
);
</script>
<style scoped lang="scss" src="@/styles/journal.scss"></style>
