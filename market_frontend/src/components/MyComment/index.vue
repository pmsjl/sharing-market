<template>
  <div class="my-comments-container">
    <el-card
      title="我的评论"
      class="my-comments-card"
      :body-style="{ padding: '20px' }"
    >
      <div :data="myComments" class="comment-list">
        <div v-for="item in myComments" :key="item.id" class="my-comments-item">
          <el-card class="comment-card" :body-style="{ padding: '20px' }">
            <div class="comment-heading">
              <slot name="header">
                <el-text strong>
                  <router-link
                    :to="`/user/post/${item.postId}`"
                    class="comment-link"
                  >
                    攻略标题：{{ item.postTitle }}
                  </router-link>
                </el-text>
                <el-text class="comment-update-time">
                  更新时间：{{ formatDate(item.updateTime) }}
                </el-text>
              </slot>
            </div>

            <div class="comment-content">
              <strong>评论内容：</strong> {{ item.content }}
            </div>
          </el-card>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { listMyCommentsUsingPost } from "@/api/commentController"; // API 请求函数
import { ElMessage } from "element-plus";
import dayjs from "dayjs";

// 定义评论类型
interface MyComment {
  id: string;
  postId: string;
  postTitle: string;
  updateTime: string;
  content: string;
}

// 定义 myComments 的状态
const myComments = ref<MyComment[]>([]);

// 获取评论的函数
const getComments = async () => {
  try {
    const res = await listMyCommentsUsingPost();
    myComments.value = res.data || [];
  } catch (e) {
    ElMessage.error(`获取我的评论失败，${e.message}`);
  }
};

// 格式化时间的函数
const formatDate = (date: string) => {
  return dayjs(date).format("YYYY-MM-DD HH:mm:ss");
};

// 在组件挂载时获取评论数据
onMounted(() => {
  getComments();
});
</script>

<style scoped lang="scss">
.my-comments-container {
  margin: 0;
}

.my-comments-card {
  border: 0;
  background: transparent;
  box-shadow: none;
}

.comment-list {
  margin-top: 20px;
}

.my-comments-item {
  margin-bottom: 16px;
}

.comment-card {
  border: 0;
  border-bottom: 1px solid var(--market-line);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  :deep(.el-card__body) {
    padding: 20px 0;
  }
}

.comment-heading {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.comment-link {
  color: var(--market-primary);
  font-family: var(--market-font-body);
  text-decoration: none;
}

.comment-update-time {
  color: var(--market-muted);
  font-family: var(--market-font-body);
}

.comment-content {
  margin-top: 10px;
  color: var(--market-ink);
  line-height: 28px;
}

@media (max-width: 600px) {
  .comment-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
