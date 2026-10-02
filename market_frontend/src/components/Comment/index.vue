<template>
  <section class="comments-page" aria-labelledby="comments-heading">
    <h2 id="comments-heading">回答讨论</h2>
    <div class="comment-composer">
      <div class="comment-author">
        <el-avatar :src="loginUser.userAvatar" :size="32">{{
          (loginUser.userName || "同学").slice(0, 1)
        }}</el-avatar>
        <span>{{ loginUser.userName || "同学" }}</span>
      </div>
      <el-input
        v-model="commentText"
        type="textarea"
        :rows="3"
        placeholder="分享你的经验，或留下想问的问题"
        aria-label="评论内容"
      />
      <div class="comment-submit">
        <el-button type="primary" @click="doComment">发表评论</el-button>
      </div>
    </div>
    <div v-if="comments.length" class="comment-list">
      <CommentView
        v-for="comment in comments"
        :key="comment.id"
        :postId="postId"
        :comment="comment"
        :showCount="showCount"
        @getComment="getComments"
        @delete="handleDelete"
      />
    </div>
    <p v-else class="comment-empty">还没有讨论，来分享第一条经验吧。</p>
  </section>
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
  border-top: 1px solid var(--market-line);
  padding-top: 28px;
}
h2 {
  margin: 0 0 24px;
  font-size: 22px;
  font-weight: 650;
}
.comment-composer {
  margin-bottom: 28px;
}
.comment-author {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  color: var(--market-ink);
  font-size: 14px;
}
.comment-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.comment-empty {
  padding: 18px 0;
  color: var(--market-muted);
  font-size: 14px;
}
</style>
