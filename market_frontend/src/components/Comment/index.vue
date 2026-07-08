<template>
  <div class="comments-page">
    <el-card class="comments-card">
      <template #header>
        <div class="card-header">
          <span>回答讨论</span>
        </div>
      </template>

      <!-- 评论输入框 -->
      <el-card class="comment-input-card">
        <el-row style="display: flex; align-items: center">
          <!-- 头像 -->
          <el-avatar
            :src="loginUser.userAvatar || '/assets/logo.png'"
            size="default"
            class="comment-avatar"
          />
          <span style="margin-left: 5px; font-weight: 700">{{
            loginUser.userName
          }}</span>
        </el-row>

        <el-input
          v-model="commentText"
          type="textarea"
          :rows="4"
          placeholder="快来和大家讨论吧"
          class="comment-textarea"
        />

        <el-button type="success" @click="doComment" class="submit-button"
          >评论
        </el-button>
      </el-card>

      <!-- 评论列表 -->
      <div v-if="comments.length > 0" class="comment-list">
        <div v-for="comment in comments" :key="comment.id" class="comment-item">
          <CommentView
            :postId="postId"
            :comment="comment"
            :showCount="showCount"
            @getComment="getComments"
            @delete="handleDelete"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  addCommentUsingPost,
  deleteCommentUsingPost,
  getCommentByPostIdUsingGet
} from "@/api/commentController";
import CommentView from "@/components/CommentView/index.vue";
import { GET_AVATAR, GET_ID, GET_USER_NAME } from "@/utils/token";

const props = defineProps({
  postId: {
    type: String,
    required: true
  }
});
const loginUser = ref({
  id: GET_ID(), // 假设用户已登录
  userAvatar: GET_AVATAR(),
  userName: GET_USER_NAME()
});
const commentText = ref("");
const comments = ref([]);
const showCount = ref(new Map());

const initShowCount = (commentList) => {
  const map = new Map();
  const fillShowCount = (list = []) => {
    list.forEach((comment) => {
      map.set(comment.id, 3);
      if (comment.replies?.length) {
        fillShowCount(comment.replies);
      }
    });
  };
  fillShowCount(commentList);
  return map;
};

// 获取评论
const getComments = async () => {
  try {
    const res = await getCommentByPostIdUsingGet({
      postId: props.postId
    });
    const commentList = res?.data || [];
    showCount.value = initShowCount(commentList);
    comments.value = commentList;
  } catch (e) {
    ElMessage.error("获取评论失败: " + e.message);
  }
};

// 提交评论
const doComment = async () => {
  if (!commentText.value) {
    ElMessage.warning("评论内容不能为空");
    return;
  }
  try {
    const res = await addCommentUsingPost({
      postId: props.postId,
      content: commentText.value
    });
    if (res.code !== 200) {
      return ElMessage.error({
        duration: 1500,
        message: `回复失败，${res.message}`
      });
    }
    if (res.code === 200) {
      ElMessage.success({
        duration: 1000,
        message: "评论成功"
      });
      commentText.value = "";
      await getComments();
    }
  } catch (e) {
    ElMessage.error("评论失败: " + e.message);
  }
};

// 处理删除
const handleDelete = async (commentId) => {
  try {
    const res = await deleteCommentUsingPost({ id: commentId });
    if (res.code === 200) {
      ElMessage.success({
        duration: 1000,
        message: "删除评论成功"
      });
      await getComments();
    }
  } catch (e) {
    ElMessage.error("删除评论失败: " + e.message);
  }
};

onMounted(() => {
  getComments();
});
</script>

<style scoped lang="scss">
.comments-page {
  .comments-card {
    padding: 20px;
    position: relative; /* 用于定位头像 */
  }

  .card-header {
    font-size: 18px;
    font-weight: bold;
  }

  /* 评论输入框的卡片 */
  .comment-input-card {
    display: flex;
    flex-direction: column;
    padding: 20px;
    position: relative;
    background-color: #f9f9f9;

    /* 头像区域 */
    .avatar-container {
      margin-bottom: 12px; /* 头像和输入框之间的间距 */
    }

    /* 评论输入框填充满卡片 */
    .comment-textarea {
      width: 100%;
      height: 120px;
      padding: 10px;
      background-color: #fff;
      border-radius: 4px;
    }

    /* 提交按钮放在卡片右下角 */
    .submit-button {
      margin-top: 12px;
      align-self: flex-end; /* 按钮右对齐 */
    }
  }

  /* 评论列表 */
  .comment-list {
    margin-top: 20px;
  }

  .comment-item {
    margin-bottom: 15px;
  }
}
</style>
