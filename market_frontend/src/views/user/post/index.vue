<template>
  <div class="journal-browse">
    <header class="journal-heading">
      <h1>
        同学攻略<span aria-hidden="true" class="journal-mark">一起分享</span>
      </h1>
      <p>买过、用过、踩过的坑，都值得说给同学听。</p>
    </header>
    <div class="journal-toolbar" v-if="!addPost">
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
            @click="handleSearch" /></template></el-input
      ><el-button type="primary" :icon="Promotion" @click="showAddPost"
        >分享经验</el-button
      >
    </div>
    <el-button v-else text @click="addPost = false">返回同学攻略</el-button>
    <PostTagFilter
      v-if="!addPost"
      v-model="filterTags"
      v-model:mode="tagMatchMode"
      input-id="browse-post-tags"
      @change="handleSearch"
    />
    <AddPost v-if="addPost" /><template v-else
      ><div class="journal-list" v-loading="loading">
        <PostPreview
          v-for="post in postList"
          :key="post.id"
          :post="post"
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
import { onMounted, onUnmounted, ref } from "vue";
import { Promotion, Search } from "@element-plus/icons-vue";
import { ElButton, ElMessage, ElPagination } from "element-plus";
import { listPostVoByPageUsingPost } from "@/api/postController";
import AddPost from "@/components/AddPost/index.vue";

import PostTagFilter from "@/components/PostTagFilter/index.vue";
import PostPreview from "@/components/PostPreview/index.vue";
import eventBus from "@/utils/eventBus";

// 搜索文本
const searchText = ref("");
const filterTags = ref<string[]>([]);
const tagMatchMode = ref<"all" | "any">("all");
const loading = ref(false);
let querySequence = 0;

// 帖子列表
const postList = ref<API.PostVO[]>([]);
const addPost = ref(false);

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
  try {
    const res = await listPostVoByPageUsingPost({
      searchText: searchText.value,
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
      ElMessage.error("获取帖子列表失败");
    }
  } catch (error) {
    if (sequence === querySequence) ElMessage.error("获取帖子列表失败");
  } finally {
    if (sequence === querySequence) loading.value = false;
  }
};

const showAddPost = () => {
  addPost.value = true;
};

// 处理搜索
const handleSearch = () => {
  paginationConfig.value.current = 1;
  getPostList();
};

// 处理分页
const handlePageChange = (page: number) => {
  paginationConfig.value.current = page;
  getPostList();
};

// 初始化加载帖子列表
onMounted(() => {
  getPostList();
  eventBus.on("refresh-post-list", getPostList); // 监听事件
});
onUnmounted(() => {
  eventBus.off("refresh-post-list", getPostList); // 组件卸载时移除监听
});
</script>
<style scoped lang="scss" src="@/styles/journal.scss"></style>
