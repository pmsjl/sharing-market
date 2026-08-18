<template>
  <div class="post-detail">
    <div v-if="isAgentEntry" class="agent-return-bar">
      <el-button :icon="ArrowLeft" plain @click="returnToAgent">
        返回智能导购
      </el-button>
      <span>继续查看刚才的咨询与推荐理由</span>
    </div>

    <!-- 帖子详情 -->
    <div class="post-content">
      <div class="post-header">
        <el-avatar :src="post.user?.userAvatar" class="user-avatar" />
        <div class="user-details">
          <span class="user-name">{{ post.user?.userName }}</span>
          <span class="post-time">{{ post.createTime }}</span>
        </div>
        <el-button
          v-if="canChatWithAuthor"
          class="chat-author-button"
          size="small"
          type="primary"
          plain
          @click="goToPrivateChat"
        >
          私聊作者
        </el-button>
      </div>
      <h1 class="post-title">{{ post.title }}</h1>
      <MdPreview
        class="post-body"
        editor-id="mdPreview"
        :modelValue="post.content"
        previewTheme="github"
        showCodeRowNumber
      />

      <!-- 分割线 -->
      <el-divider />

      <!-- 三个图标 -->
      <div class="icon-container">
        <div
          class="icon-item stamp-action"
          :class="{ 'is-stamped': initLikeStatus === 1 }"
          @click="doThumb"
        >
          <template v-if="initLikeStatus === 0">
            <img src="@/assets/icons/dianzan.svg" width="17" height="17" />
          </template>
          <template v-if="initLikeStatus === 1">
            <img src="@/assets/icons/alreadyLike.svg" width="17" height="17" />
          </template>

          <span>{{ likeCount }}</span>
        </div>
        <div
          class="icon-item stamp-action"
          :class="{ 'is-stamped': initCollectStatus === 1 }"
          @click="handleCollect"
        >
          <el-icon :size="20">
            <template v-if="initCollectStatus === 0">
              <Star />
            </template>
            <template v-if="initCollectStatus === 1">
              <StarFilled color="#fadb14" :size="20" />
            </template>
          </el-icon>
          <span>{{ collectCount }}</span>
        </div>
        <div class="icon-item" @click="handleShare">
          <el-icon :size="20">
            <Share />
          </el-icon>
          <span>分享</span>
        </div>
        <!-- 分享对话框 -->
        <el-dialog v-model="shareDialogVisible" width="400px">
          <div class="share-dialog-content">
            <!-- 标题 -->
            <h3
              style="
                font-weight: 700;
                font-size: 24px;
                margin: 0;
                text-align: center;
              "
            >
              分享此题目
            </h3>
            <el-divider />
            <!-- 分享链接 -->
            <div class="share-section">
              <p style="margin: 0 0 10px 0; font-weight: 700; font-size: 20px">
                分享链接：
              </p>
              <el-card>
                <div class="link-container">
                  <span>{{ currentPageUrl }}</span>
                  <el-button type="primary" @click="copyLink">复制</el-button>
                </div>
              </el-card>
            </div>
            <el-divider />
            <!-- 二维码分享 -->
            <div class="share-section">
              <p style="margin: 0 0 10px 0; font-weight: 700; font-size: 20px">
                二维码分享：
              </p>
              <el-card style="margin: 0 auto">
                <QRCodeVue3
                  :value="currentPageUrl"
                  :width="200"
                  :height="200"
                  :imageOptions="{
                    hideBackgroundDots: false,
                    imageSize: 0.4,
                    margin: 0
                  }"
                />
              </el-card>
            </div>
          </div>
        </el-dialog>
      </div>
    </div>

    <!-- 评论区 -->
    <Comments :postId="postId" style="margin-top: 20px" />
  </div>
</template>

<script setup lang="ts">
import Comments from "@/components/Comment/index.vue";
import { computed, ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getPostVoByIdUsingGet } from "@/api/postController";
import { doPostFavourUsingPost } from "@/api/postFavourController";
import { ElMessage } from "element-plus";
import { doThumbUsingPost } from "@/api/postThumbController";
import QRCodeVue3 from "qrcode-vue3";
import useClipboard from "vue-clipboard3";
import { MdPreview } from "md-editor-v3";
import { GET_ID } from "@/utils/token";
import { ArrowLeft } from "@element-plus/icons-vue";
// 获取路由参数
const route = useRoute();
const router = useRouter();
const postId = Array.isArray(route.params.id)
  ? route.params.id[0]
  : String(route.params.id || "");
const currentUserId = String(GET_ID() || "");
const routeValue = (value: unknown) =>
  Array.isArray(value) ? String(value[0] || "") : String(value || "");
const isAgentEntry = computed(() => routeValue(route.query.from) === "agent");
const sourceConversationId = computed(() =>
  routeValue(route.query.conversationId)
);
// 分享对话框的显示状态
const shareDialogVisible = ref(false);
// 当前页面地址
const buildShareUrl = () => {
  const url = new URL(window.location.href);
  url.searchParams.delete("from");
  url.searchParams.delete("conversationId");
  return url.toString();
};
const currentPageUrl = ref(buildShareUrl());

// 帖子详情数据
const post = ref<API.PostVO>({
  id: postId,
  title: "",
  content: "",
  createTime: "",
  userId: "",
  user: {
    id: "",
    userName: "",
    userAvatar: ""
  }
});

const authorId = computed(() =>
  String(post.value.user?.id || post.value.userId || "")
);
const canChatWithAuthor = computed(
  () => Boolean(authorId.value) && authorId.value !== currentUserId
);

// 点赞和收藏计数
const likeCount = ref(); // 示例查看次数
const collectCount = ref(); // 示例收藏次数
const initCollectStatus = ref(0); // 示例收藏状态，0 表示未收藏，1 表示已收藏
const initLikeStatus = ref(0); // 点赞状态 0 未点赞  1已点赞
// 获取帖子详情
const fetchPostDetail = async () => {
  try {
    const response = (await getPostVoByIdUsingGet({
      id: postId
    })) as unknown as API.BaseResponsePostVO_;
    if (response?.data) {
      post.value = {
        id: response.data.id || postId,
        title: response.data.title,
        content: response.data.content,
        createTime: response.data.createTime,
        userId: response.data.userId,
        user: {
          id: response.data.user?.id,
          userName: response.data.user?.userName,
          userAvatar: response.data.user?.userAvatar
        }
      };
      // 直接从 PostVO 获取点赞和收藏状态
      initLikeStatus.value = response.data.hasThumb ? 1 : 0;
      initCollectStatus.value = response.data.hasFavour ? 1 : 0;
      likeCount.value = response.data.thumbNum;
      collectCount.value = response.data.favourNum;
    }
  } catch (error) {
    ElMessage.error({
      duration: 1000,
      message: "获取帖子详情失败"
    });
  }
};
// 点赞处理
const doThumb = async () => {
  const res = (await doThumbUsingPost({
    postId: post.value.id
  })) as unknown as API.BaseResponseInt_;
  if (res.code !== 200) {
    return ElMessage.error({
      duration: 1000,
      message: "点赞/取消点赞操作失败"
    });
  }
  if (res.data === -1) {
    ElMessage.success({
      duration: 1000,
      message: "取消点赞成功"
    });
    initLikeStatus.value = 0;
  } else {
    ElMessage.success({
      duration: 1000,
      message: "点赞成功"
    });
    initLikeStatus.value = 1;
  }
  await getPostLikeAndCollect();
};
// 收藏处理
const handleCollect = async () => {
  const res = (await doPostFavourUsingPost({
    postId: post.value.id
  })) as unknown as API.BaseResponseInt_;
  if (res.code !== 200) {
    return ElMessage.error({
      duration: 1000,
      message: "收藏/取消收藏操作失败"
    });
  }
  if (res.data === -1) {
    initCollectStatus.value = 0;
    ElMessage.success({
      duration: 1000,
      message: "取消收藏帖子成功"
    });
  } else {
    initCollectStatus.value = 1;
    ElMessage.success({
      duration: 1000,
      message: "收藏该帖子成功"
    });
  }
  await getPostLikeAndCollect();
};
// 复制链接
const { toClipboard } = useClipboard();
const copyLink = async () => {
  try {
    await toClipboard(currentPageUrl.value);
    ElMessage.success({
      message: "链接已复制到剪贴板",
      duration: 1000
    });
  } catch (e) {
    ElMessage.error({
      duration: 1000,
      message: "复制失败"
    });
  }
};
// 获取帖子原来的点赞量和收藏量
const getPostLikeAndCollect = async () => {
  const res = (await getPostVoByIdUsingGet({
    id: post.value.id
  })) as unknown as API.BaseResponsePostVO_;
  if (res.code !== 200) {
    ElMessage.error({
      duration: 1000,
      message: "获取帖子点赞和收藏量失败"
    });
  }
  likeCount.value = res.data?.thumbNum;
  collectCount.value = res.data?.favourNum;
  initLikeStatus.value = res.data?.hasThumb ? 1 : 0;
  initCollectStatus.value = res.data?.hasFavour ? 1 : 0;
};
// 分享处理
// 处理分享的点击事件
const handleShare = () => {
  shareDialogVisible.value = true;
};
const returnToAgent = () => {
  const previousPath = String(window.history.state?.back || "");
  if (previousPath.startsWith("/user/agentGuide")) {
    router.back();
    return;
  }
  void router.push({
    path: "/user/agentGuide",
    query: sourceConversationId.value
      ? { conversationId: sourceConversationId.value }
      : {}
  });
};
const goToPrivateChat = () => {
  if (!canChatWithAuthor.value) return;
  router.push({
    path: "/user/account",
    query: {
      tab: "chat",
      contactUserId: authorId.value,
      contactName: post.value.user?.userName || "帖子作者",
      contactAvatar: post.value.user?.userAvatar || ""
    }
  });
};
// 在组件挂载时获取数据（hasThumb/hasFavour 已在 fetchPostDetail 中获取）
onMounted(async () => {
  await fetchPostDetail();
});
</script>

<style scoped lang="scss">
.post-detail {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;

  .agent-return-bar {
    display: flex;
    min-height: 48px;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    padding: 8px 12px;
    border: 1px dashed var(--market-line);
    border-radius: 8px;
    color: var(--market-muted);
    background: var(--market-surface);
    font-size: 13px;

    .el-button {
      min-height: 40px;
    }
  }

  .post-content {
    position: relative;
    background: var(--market-surface);
    border-radius: 8px;
    padding: 28px 30px 34px 58px;
    border: 1px solid var(--market-line);
    box-shadow: var(--market-shadow-soft);
    @include ruled-paper(28px, 44px);

    &::before {
      position: absolute;
      top: 24px;
      bottom: 24px;
      left: 14px;
      width: 14px;
      background: radial-gradient(
        circle,
        var(--market-paper-deep) 0 4px,
        rgba(35, 49, 63, 0.24) 4.5px 5.5px,
        transparent 6px
      );
      background-size: 14px 38px;
      content: "";
    }

    .post-header {
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
          color: var(--market-ink);
        }

        .post-time {
          font-size: 14px;
          margin-top: 10px;
          color: var(--market-muted);
          font-family: var(--market-font-mono);
        }
      }

      .chat-author-button {
        margin-left: auto;
      }
    }

    .post-title {
      font-size: 24px;
      font-weight: bold;
      font-family: var(--market-font-display);
      margin-bottom: 16px;
    }

    .post-body {
      font-size: 16px;
      color: var(--market-ink);
      line-height: 28px;

      :deep(.md-editor-preview-wrapper) {
        padding-inline: 0;
        background: transparent;
      }

      :deep(.md-editor-preview > p:first-of-type::first-letter) {
        float: left;
        margin: 8px 8px 0 0;
        color: var(--market-orange);
        font-family: var(--market-font-display);
        font-size: 3.2em;
        font-weight: 900;
        line-height: 0.78;
      }
    }
  }

  .comment-section {
    margin-top: 20px;

    .comment-title {
      font-size: 20px;
      font-weight: bold;
      margin-bottom: 16px;
    }

    .comment-list {
      .comment-item {
        display: flex;
        align-items: flex-start;
        margin-bottom: 16px;

        .comment-avatar {
          margin-right: 12px;
        }

        .comment-content {
          display: flex;
          flex-direction: column;

          .comment-user {
            font-size: 14px;
            font-weight: bold;
            color: #333;
          }

          .comment-text {
            font-size: 14px;
            color: #666;
            margin: 8px 0;
          }

          .comment-time {
            font-size: 12px;
            color: #999;
          }
        }
      }
    }
  }

  .icon-container {
    display: flex;
    justify-content: space-around;
    align-items: center;
    margin-top: 20px;

    .icon-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5px;
      cursor: pointer;
      color: var(--market-muted);
      transition: color 0.3s;

      &:hover {
        color: var(--market-orange);
      }

      span {
        font-size: 14px;
      }
    }
  }
}

@media (max-width: 520px) {
  .post-detail .agent-return-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}

.stamp-action {
  min-width: 82px;
  padding: 8px 14px;
  border: 2px solid var(--market-muted);
  border-radius: 6px;
  color: var(--market-muted) !important;
  font-family: var(--market-font-display);
  transform: rotate(-3deg);

  &.is-stamped {
    border-color: var(--market-stamp-red);
    color: var(--market-stamp-red) !important;
    transform: rotate(-7deg);
  }
}

@media (max-width: 600px) {
  .post-detail {
    padding: 10px;

    .post-content {
      padding: 22px 16px 26px 38px;
    }

    .icon-container {
      flex-wrap: wrap;
      gap: 12px;
    }
  }
}

.share-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 20px;

  .share-section {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .link-container {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;

    span {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
