<template>
  <div class="post-browse">
    <!-- 搜索区域 -->
    <div class="search-bar">
      <div class="post-toolbar" v-if="!addPost">
        <el-input
          v-model="searchText"
          class="post-search-input"
          placeholder="搜索帖子"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
        <div class="post-toolbar-actions">
          <el-button
            type="primary"
            class="toolbar-button"
            @click="showAddPost()"
            :icon="Promotion"
          >
            分享攻略
          </el-button>
        </div>
      </div>
      <div class="post-toolbar post-toolbar--back" v-else>
        <el-button
          type="success"
          class="toolbar-button"
          @click="addPost = false"
          :icon="Back"
        >
          返回攻略列表
        </el-button>
      </div>
    </div>
    <AddPost v-if="addPost"></AddPost>
    <!-- 帖子列表 -->
    <div class="post-list" v-if="!addPost">
      <div
        v-for="post in postList"
        :key="post.id"
        class="post-item"
        @click="goToPostDetail(post.id)"
      >
        <!-- 用户信息 -->
        <div class="user-info">
          <el-avatar
            :src="post.user?.userAvatar"
            class="user-avatar"
          ></el-avatar>
          <div class="user-details">
            <span class="user-name">{{ post.user?.userName }}</span>
            <span class="post-time">{{ post.createTime }}</span>
          </div>
        </div>
        <!-- 帖子标题 -->
        <div class="post-header">
          <span class="post-title">{{ post.title }}</span>
        </div>
        <!-- 帖子内容 -->
        <div class="post-content">
          {{ truncateContent(post.content, 30) }}
        </div>
        <!-- 标签 -->
        <div class="post-footer">
          <div class="post-tags">
            <el-tag
              v-for="tag in post.tagList"
              :key="tag"
              type="info"
              size="small"
              class="tag"
            >
              {{ tag }}
            </el-tag>
          </div>
          <!-- 点赞和收藏 -->
          <div class="post-actions">
            <span class="action-item">
              <img src="@/assets/icons/dianzan.svg" width="14" height="14" />
              {{ post.thumbNum }}
            </span>
            <span class="action-item">
              <el-icon><Star /></el-icon>
              {{ post.favourNum }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="!addPost">
      <el-pagination
        small
        background
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[5, 10, 15, 20]"
        :current-page="paginationConfig.current"
        :total="paginationConfig.total"
        :page-size="paginationConfig.pageSize"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { Promotion, Search, Star, Back } from "@element-plus/icons-vue";
import { ElButton, ElMessage, ElPagination } from "element-plus";
import { listPostVoByPageUsingPost } from "@/api/postController";
import AddPost from "@/components/AddPost/index.vue";
import "@/assets/icons/dianzan.svg";
import { useRouter } from "vue-router";
import eventBus from "@/utils/eventBus";

// 搜索文本
const searchText = ref("");

// 帖子列表
const postList = ref([]);
const addPost = ref(false);
const router = useRouter(); // 获取路由实例
// 分页配置
const paginationConfig = ref({
  current: 1,
  pageSize: 10,
  total: 0
});

// 获取帖子列表
const getPostList = async () => {
  try {
    const res = await listPostVoByPageUsingPost({
      searchText: searchText.value,
      current: paginationConfig.value.current,
      pageSize: paginationConfig.value.pageSize
    });
    if (res.code === 200) {
      postList.value = res.data.records;
      paginationConfig.value.total = parseInt(res.data.total);
    } else {
      ElMessage.error("获取帖子列表失败");
    }
  } catch (error) {
    ElMessage.error("获取帖子列表失败");
  }
};
// 跳转到帖子详情页
const goToPostDetail = (postId?: string) => {
  if (!postId) return;
  router.push({ name: "PostDetail", params: { id: postId } });
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

const handleSizeChange = (val: number) => {
  paginationConfig.value.pageSize = val;
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

// 工具函数：截断内容
const truncateContent = (text: string, length: number) => {
  if (text.length > length) {
    return text.slice(0, length) + "...";
  }
  return text;
};
</script>

<style scoped lang="scss">
.post-browse {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;

  .search-bar {
    margin-bottom: 20px;
    padding: 12px 14px;

    .el-input {
      width: 100%;
    }
  }

  .post-toolbar {
    display: grid;
    grid-template-columns: minmax(240px, 420px) auto;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  .post-toolbar--back {
    display: flex;
    justify-content: flex-end;
  }

  .post-search-input {
    max-width: 420px;
  }

  .post-toolbar-actions {
    display: flex;
    justify-content: flex-end;
  }

  .toolbar-button {
    min-height: 36px;
    padding: 8px 18px;
  }

  .post-list {
    .post-item {
      background-color: #fff;
      border-radius: 8px;
      padding: 20px;
      margin-bottom: 20px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      cursor: pointer;

      .user-info {
        display: flex;
        align-items: center;
        margin-bottom: 16px;

        .user-avatar {
          margin-right: 12px;
        }

        .user-details {
          display: flex;
          flex-direction: column;

          .user-name {
            font-size: 16px;
            font-weight: bold;
            color: #333;
          }

          .post-time {
            font-size: 14px;
            margin-top: 10px;
            color: #999;
          }
        }
      }

      .post-header {
        margin-bottom: 16px;

        .post-title {
          font-size: 18px;
          font-weight: bold;
          color: #333;
        }
      }

      .post-content {
        font-size: 14px;
        color: #666;
        line-height: 1.6;
        margin-bottom: 16px;
        display: -webkit-box;
        -webkit-line-clamp: 2; /* 限制显示两行 */
        -webkit-box-orient: vertical;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .post-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .post-tags {
          .tag {
            margin-right: 8px;
          }
        }

        .post-actions {
          display: flex;
          align-items: center;

          .action-item {
            display: flex;
            align-items: center;
            margin-left: 16px;
            font-size: 14px;
            color: #666;

            .el-icon {
              margin-right: 4px;
            }
          }
        }
      }
    }
  }

  .pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}

@media (max-width: 760px) {
  .post-browse {
    padding: 12px;

    .post-toolbar {
      grid-template-columns: 1fr;
    }

    .post-toolbar-actions,
    .post-toolbar--back,
    .pagination {
      justify-content: flex-start;
    }

    .toolbar-button {
      width: 100%;
    }
  }
}
</style>
