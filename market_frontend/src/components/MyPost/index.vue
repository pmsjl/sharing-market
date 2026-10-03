<template>
  <div class="my-posts">
    <div class="my-posts-toolbar">
      <el-input
        v-model="searchText"
        clearable
        placeholder="搜索我的攻略"
        aria-label="搜索我的攻略"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" aria-label="搜索" @click="handleSearch" />
        </template>
      </el-input>
      <el-button class="toolbar-button" @click="loadMyPosts">刷新</el-button>
    </div>

    <PostTagFilter
      v-model="filterTags"
      v-model:mode="tagMatchMode"
      input-id="my-post-tags"
      @change="handleSearch"
    />

    <div v-if="loadError" class="quiet-state" role="alert">
      {{ loadError }}<el-button @click="loadMyPosts">重试</el-button>
    </div>
    <el-empty
      v-else-if="!loading && postList.length === 0"
      :description="
        searchText || filterTags.length
          ? '没有符合筛选条件的攻略'
          : '还没有发布攻略'
      "
    />

    <div v-else class="post-list" v-loading="loading">
      <PostPreview
        v-for="post in postList"
        :key="post.id"
        :post="post"
        :linkable="!isAdmin"
        ><template #actions>
          <el-button
            v-if="!isAdmin"
            class="action-button"
            link
            size="small"
            type="primary"
            @click="openEditDialog(post)"
          >
            编辑
          </el-button>
          <el-popconfirm
            title="确定删除这篇攻略吗？"
            @confirm="deleteMyPost(post.id)"
          >
            <template #reference>
              <el-button class="action-button" link size="small" type="danger">
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </PostPreview>
    </div>

    <div class="market-pagination">
      <el-pagination
        small
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        :pager-count="5"
        @current-change="syncQuery"
        @size-change="handleSearch"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import PostPreview from "@/components/PostPreview/index.vue";
import PostTagFilter from "@/components/PostTagFilter/index.vue";
import { Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  deletePostUsingPost,
  listMyPostVoByPageUsingPost
} from "@/api/postController";

import { useRoute, useRouter } from "vue-router";
import { queryPage, queryText } from "@/utils/marketNavigation";
import { GET_ROLE } from "@/utils/token";
const isAdmin = GET_ROLE() === "admin";
const route = useRoute(),
  router = useRouter();
const loading = ref(false);
const loadError = ref("");
const postList = ref<API.PostVO[]>([]);
const total = ref(0);
const searchText = ref("");
const filterTags = ref<string[]>([]);
const tagMatchMode = ref<"all" | "any">("all");
let querySequence = 0;

const queryParams = ref({
  current: 1,
  pageSize: 10
});

const loadMyPosts = async () => {
  const sequence = ++querySequence;
  loading.value = true;
  loadError.value = "";
  try {
    const res = await listMyPostVoByPageUsingPost({
      searchText: searchText.value,
      tags: tagMatchMode.value === "all" ? filterTags.value : [],
      orTags: tagMatchMode.value === "any" ? filterTags.value : [],
      current: queryParams.value.current,
      pageSize: queryParams.value.pageSize
    });
    if (sequence !== querySequence) return;
    if (res.code === 200 && res.data) {
      postList.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
      return;
    }
    postList.value = [];
    total.value = 0;
    loadError.value = "获取我的攻略失败";
  } catch (error) {
    if (sequence === querySequence) loadError.value = "获取我的攻略失败";
  } finally {
    if (sequence === querySequence) loading.value = false;
  }
};

const syncQuery = () =>
  router.push({
    query: {
      ...route.query,
      q: searchText.value || undefined,
      tags: filterTags.value,
      mode: tagMatchMode.value,
      page: queryParams.value.current
    }
  });
const handleSearch = () => {
  queryParams.value.current = 1;
  void syncQuery();
};
const openEditDialog = (post: API.PostVO) =>
  router.push({
    path: isAdmin ? "/admin/postManagement" : `/user/post/${post.id}/edit`,
    query: { returnTo: route.fullPath }
  });
const deleteMyPost = async (postId?: string) => {
  if (!postId) return;
  try {
    const res = await deletePostUsingPost({ id: postId });
    if (res.code !== 200) {
      ElMessage.error("删除攻略失败");
      return;
    }
    ElMessage.success("删除攻略成功");
    if (postList.value.length === 1 && queryParams.value.current > 1) {
      queryParams.value.current -= 1;
      await syncQuery();
    } else {
      await loadMyPosts();
    }
  } catch (error) {
    ElMessage.error("删除攻略失败");
  }
};

watch(
  () => route.query,
  (query) => {
    searchText.value = queryText(query.q);
    filterTags.value = (
      Array.isArray(query.tags) ? query.tags : query.tags ? [query.tags] : []
    ).filter((item): item is string => typeof item === "string");
    tagMatchMode.value = query.mode === "any" ? "any" : "all";
    queryParams.value.current = queryPage(query.page);
    void loadMyPosts();
  },
  { immediate: true }
);
</script>

<style scoped lang="scss">
.my-posts {
  display: grid;
  gap: 16px;
}

.my-posts-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 420px) auto;
  gap: 12px;
  align-items: center;
  justify-content: start;
}

.toolbar-button,
.action-button {
  min-height: 34px;
  padding: 7px 14px;
}

.post-list {
  display: grid;
  gap: 0;
  min-height: 120px;
}

@media (max-width: 760px) {
  .my-posts-toolbar {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
