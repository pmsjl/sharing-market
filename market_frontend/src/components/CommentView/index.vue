<template>
  <div
    class="comment-view"
    :class="{ 'root-comment': depth === 0, 'reply-comment': depth > 0 }"
  >
    <el-avatar
      :src="comment.user?.userAvatar || '/assets/logo.png'"
      :size="depth === 0 ? 'default' : 'small'"
      class="avatar"
    />

    <div class="comment-content">
      <div class="comment-header">
        <span class="username">{{ comment.user?.userName }}</span>
        <span v-if="depth > 0 && repliedUserName" class="reply-target">
          回复 {{ repliedUserName }}
        </span>
      </div>

      <div class="comment-body">
        {{ comment.content }}
      </div>

      <div class="comment-actions">
        <span class="create-time">{{ comment.createTime }}</span>
        <div class="action-buttons-inline">
          <el-button link type="primary" size="small" @click="handleReply">
            回复
          </el-button>
          <template v-if="isCurrentUserComment">
            <el-popconfirm
              title="你确定要删除该评论吗？"
              @confirm="handleDelete"
              @cancel="cancelEvent"
            >
              <template #reference>
                <el-button
                  link
                  type="danger"
                  size="small"
                  class="delete-button"
                >
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </div>
      </div>

      <div v-if="isReplying" class="reply-editor">
        <el-input
          v-model="replyContent"
          type="textarea"
          :placeholder="`回复${comment.user?.userName || '用户'}：`"
          :autosize="{ minRows: 3, maxRows: 6 }"
        />
        <div class="reply-editor-actions">
          <el-button text @click="cancelReply">取消</el-button>
          <el-button type="primary" @click="submitReply">回复</el-button>
        </div>
      </div>

      <div v-if="replyList.length" class="replies-section">
        <CommentView
          v-for="reply in visibleReplies"
          :key="reply.id"
          :comment="reply"
          :postId="postId"
          :showCount="showCount"
          :depth="depth + 1"
          @delete="handleChildDelete"
          @getComment="handleRefresh"
          @doCancel="handleCancel"
        />

        <template v-if="replyList.length > 3 && visibleCount === 3">
          <el-button link type="primary" size="small" @click="handleExpand">
            展开全部
          </el-button>
        </template>

        <template v-if="replyList.length > 3 && visibleCount > 3">
          <el-button link type="success" size="small" @click="handleCollapse">
            收起
          </el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "CommentView"
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { GET_ID } from "@/utils/token";
import { ElMessage } from "element-plus";
import { addCommentUsingPost } from "@/api/commentController";

const props = defineProps({
  comment: {
    type: Object,
    required: true
  },
  postId: {
    type: String,
    required: true
  },
  showCount: {
    type: Map,
    required: true
  },
  depth: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(["delete", "getComment", "doCancel"]);

const loginUser = ref({
  id: GET_ID()
});
const replyContent = ref("");
const isReplying = ref(false);

const replyList = computed(() => props.comment.replies || []);
const visibleCount = computed(
  () => props.showCount?.get(props.comment.id) ?? 3
);
const visibleReplies = computed(() =>
  replyList.value.slice(0, visibleCount.value)
);
const repliedUserName = computed(() => props.comment.repliedUser?.userName);
const isCurrentUserComment = computed(() => {
  const loginUserId = loginUser.value.id;
  const commentUserId = props.comment.user?.id;
  return (
    loginUserId != null &&
    commentUserId != null &&
    String(loginUserId) === String(commentUserId)
  );
});

const handleReply = () => {
  isReplying.value = true;
};

const cancelReply = () => {
  isReplying.value = false;
  replyContent.value = "";
};

const submitReply = async () => {
  if (!props.postId) {
    return;
  }
  if (!loginUser.value.id) {
    return ElMessage.error({
      duration: 1500,
      message: "请先登录"
    });
  }
  if (!replyContent.value) {
    return ElMessage.warning("回复内容不能为空");
  }
  try {
    const res = await addCommentUsingPost({
      postId: props.postId,
      content: replyContent.value,
      parentId: props.comment.id
    });
    if (res.code !== 200) {
      isReplying.value = false;
      return ElMessage.error({
        duration: 1500,
        message: `回复失败，${res.message}`
      });
    }
    emit("getComment");
    emit("doCancel");
    ElMessage.success({
      duration: 1500,
      message: "回复评论成功"
    });
    cancelReply();
  } catch (e) {
    ElMessage.error("回复失败: " + e.message);
  }
};

const handleDelete = () => {
  emit("delete", props.comment.id);
};

const handleChildDelete = (commentId: number | string) => {
  emit("delete", commentId);
};

const handleRefresh = () => {
  emit("getComment");
};

const handleCancel = () => {
  emit("doCancel");
};

const handleExpand = () => {
  props.showCount?.set(props.comment.id, replyList.value.length);
};

const handleCollapse = () => {
  props.showCount?.set(props.comment.id, 3);
};

const cancelEvent = () => {
  ElMessage.success({
    duration: 1000,
    message: "取消删除成功"
  });
};
</script>

<style scoped lang="scss">
.comment-view {
  display: flex;
  gap: 14px;
}

.root-comment {
  padding: 18px 20px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  @include ruled-paper(28px, 28px);
}

.reply-comment {
  padding: 12px 0 0 16px;
  border-left: 2px solid var(--market-ticket-pink);
}

.avatar {
  flex: 0 0 auto;
}

.comment-content {
  flex: 1;
  min-width: 0;
  font-size: 14px;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.username {
  font-weight: bold;
  color: var(--market-green);
}

.reply-target {
  color: var(--market-muted);
  font-size: 13px;
}

.create-time {
  color: var(--market-muted);
  font-family: var(--market-font-mono);
  font-size: 0.875rem;
}

.comment-body {
  margin: 12px 0;
  line-height: 1.7;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--market-line);
}

.action-buttons-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-buttons-inline :deep(.el-button) {
  font-size: 14px;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 999px;
}

.action-buttons-inline :deep(.el-button:hover) {
  background: rgba(43, 110, 80, 0.1);
}

.action-buttons-inline :deep(.delete-button:hover) {
  background: rgba(197, 75, 66, 0.12);
}

.reply-editor {
  margin-top: 14px;
  padding: 14px;
  background: var(--market-paper-deep);
  border-radius: 8px;
}

.reply-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}

.replies-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}
</style>
