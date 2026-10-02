<template>
  <div
    class="post-detail"
    :class="{ 'has-catalog': catalog.length >= 3 }"
    ref="articleRef"
  >
    <div v-if="isAgentEntry" class="agent-return-bar">
      <el-button :icon="ArrowLeft" plain @click="returnToAgent">
        返回智能导购
      </el-button>
      <span>继续查看刚才的咨询与推荐理由</span>
    </div>

    <router-link v-if="!isAgentEntry" class="back-to-journal" to="/user/post"
      >‹ 返回同学攻略</router-link
    >
    <!-- 帖子详情 -->
    <div class="post-content">
      <h1 class="post-title">{{ post.title || "未命名攻略" }}</h1>
      <div class="post-header">
        <el-avatar :src="post.user?.userAvatar" class="user-avatar">{{
          (post.user?.userName || "同学").slice(0, 1)
        }}</el-avatar>
        <div class="user-details">
          <span class="user-name">{{ post.user?.userName || "同学" }}</span>
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
      <div class="article-topics">
        <span v-for="tag in post.tagList || []" :key="tag">#{{ tag }}</span>
      </div>
      <details v-if="catalog.length >= 3" class="mobile-catalog">
        <summary>文章目录</summary>
        <nav aria-label="文章目录">
          <button
            v-for="item in catalog"
            :key="item.id"
            :class="{ subheading: item.level === 3 }"
            type="button"
            @click="jumpToHeading(item.id)"
          >
            {{ item.text }}
          </button>
        </nav>
      </details>
      <MdPreview
        class="post-body"
        editor-id="mdPreview"
        :modelValue="post.content"
        previewTheme="github"
        :mdHeadingId="headingId"
        @onGetCatalog="receiveCatalog"
        showCodeRowNumber
      />

      <!-- 分割线 -->
      <el-divider />

      <div class="post-reactions" aria-label="帖子互动">
        <button
          type="button"
          :class="{ active: initLikeStatus === 1 }"
          :aria-pressed="initLikeStatus === 1"
          :disabled="liking"
          @click="doThumb"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M7 10v11H3V10h4Zm0 0 5-7c1-1 3 0 2 3l-1 4h6a2 2 0 0 1 2 2l-2 7a2 2 0 0 1-2 2H7"
            />
          </svg>
          <span>{{ initLikeStatus === 1 ? "已赞" : "点赞" }}</span
          ><span class="reaction-count">{{ likeCount || 0 }}</span>
        </button>
        <button
          type="button"
          :class="{ active: initCollectStatus === 1 }"
          :aria-pressed="initCollectStatus === 1"
          :disabled="collecting"
          @click="handleCollect"
        >
          <el-icon
            ><StarFilled v-if="initCollectStatus === 1" /><Star v-else
          /></el-icon>
          <span>{{ initCollectStatus === 1 ? "已收藏" : "收藏" }}</span
          ><span class="reaction-count">{{ collectCount || 0 }}</span>
        </button>
        <button type="button" @click="handleShare">
          <el-icon><Share /></el-icon><span>分享</span>
        </button>
      </div>
      <button class="join-discussion" type="button" @click="joinDiscussion">
        参与讨论 ↓
      </button>
      <ShareDialog
        v-model="shareDialogVisible"
        title="分享这篇攻略"
        subject="攻略详情"
        :url="currentPageUrl"
      />
    </div>

    <aside v-if="catalog.length >= 3" class="desktop-catalog">
      <span>这篇文章</span>
      <nav aria-label="文章目录">
        <button
          v-for="item in catalog"
          :key="item.id"
          :class="{ subheading: item.level === 3 }"
          type="button"
          @click="jumpToHeading(item.id)"
        >
          {{ item.text }}
        </button>
      </nav>
    </aside>
    <!-- 评论区 -->
    <div class="article-discussion" ref="discussionRef">
      <Comments :postId="postId" />
    </div>
  </div>
</template>

<script setup lang="ts">
import usePrivateMessageStore from "@/store/modules/privateMessage";
import Comments from "@/components/Comment/index.vue";
import { computed, ref, onMounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getPostVoByIdUsingGet } from "@/api/postController";
import { doPostFavourUsingPost } from "@/api/postFavourController";
import { ElMessage } from "element-plus";
import { doThumbUsingPost } from "@/api/postThumbController";
import ShareDialog from "@/components/ShareDialog/index.vue";
import { buildPublicShareUrl } from "@/utils/shareUrl";
import { MdPreview } from "md-editor-v3";
import "md-editor-v3/lib/preview.css";
import { GET_ID } from "@/utils/token";
import { ArrowLeft, Star, StarFilled, Share } from "@element-plus/icons-vue";
const articleRef = ref<HTMLElement | null>(null);
const discussionRef = ref<HTMLElement | null>(null);
const catalog = ref<{ text: string; level: number; id: string }[]>([]);
const headingId = (_text: string, _level: number, index: number) =>
  `post-heading-${index}`;
const receiveCatalog = (heads: { text: string; level: number }[]) => {
  catalog.value = heads
    .map((head, index) => ({
      ...head,
      id: headingId(head.text, head.level, index + 1)
    }))
    .filter((head) => head.level === 2 || head.level === 3);
};
const jumpToHeading = async (id: string) => {
  const details =
    articleRef.value?.querySelector<HTMLDetailsElement>(".mobile-catalog");
  if (details) details.open = false;
  await nextTick();
  const heading = articleRef.value?.querySelector<HTMLElement>(`#${id}`);
  if (!heading) return;
  heading.tabIndex = -1;
  heading.scrollIntoView({ block: "start", behavior: "auto" });
  heading.focus({ preventScroll: true });
};
const joinDiscussion = () => {
  discussionRef.value?.scrollIntoView({ block: "start", behavior: "auto" });
  discussionRef.value
    ?.querySelector<HTMLTextAreaElement>("textarea")
    ?.focus({ preventScroll: true });
};
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
const currentPageUrl = computed(() =>
  buildPublicShareUrl(window.location.href)
);

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
        tagList: response.data.tagList || [],
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
const liking = ref(false);
const collecting = ref(false);
const doThumb = async () => {
  if (liking.value) return;
  liking.value = true;
  try {
    const res = await doThumbUsingPost({ postId: post.value.id });
    if (res.code !== 200) {
      ElMessage.error("点赞操作失败，请重试");
      return;
    }
    initLikeStatus.value = res.data === -1 ? 0 : 1;
    await getPostLikeAndCollect();
  } catch {
    ElMessage.error("点赞操作失败，请重试");
  } finally {
    liking.value = false;
  }
};
const handleCollect = async () => {
  if (collecting.value) return;
  collecting.value = true;
  try {
    const res = await doPostFavourUsingPost({ postId: post.value.id });
    if (res.code !== 200) {
      ElMessage.error("收藏操作失败，请重试");
      return;
    }
    initCollectStatus.value = res.data === -1 ? 0 : 1;
    await getPostLikeAndCollect();
  } catch {
    ElMessage.error("收藏操作失败，请重试");
  } finally {
    collecting.value = false;
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
const privateChat = usePrivateMessageStore();
const goToPrivateChat = () => {
  if (!canChatWithAuthor.value) return;
  privateChat.openContact({
    id: authorId.value,
    userName: post.value.user?.userName || "帖子作者",
    userAvatar: post.value.user?.userAvatar || ""
  });
};
// 在组件挂载时获取数据（hasThumb/hasFavour 已在 fetchPostDetail 中获取）
onMounted(async () => {
  await fetchPostDetail();
});
</script>

<style scoped lang="scss">
.post-detail {
  max-width: 740px;
  margin: 0 auto;
  padding: 20px 0;
}
.agent-return-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
  color: var(--market-muted);
  font-size: 13px;
}
.post-content {
  padding: 0;
  min-width: 0;
}
.post-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}
.user-details {
  display: grid;
  gap: 5px;
  min-width: 0;
}
.user-name {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.post-time {
  color: var(--market-muted);
  font-size: 12px;
}
.chat-author-button {
  margin-left: auto;
  flex-shrink: 0;
}
.post-title {
  font-size: clamp(26px, 3vw, 38px);
  font-weight: 750;
  line-height: 1.4;
  margin: 0 0 28px;
  overflow-wrap: anywhere;
}
.post-body {
  color: var(--market-ink);
  background: transparent;
  font-size: 17px;
  line-height: 1.85;
  --md-bk-color: transparent;
  --md-color: var(--market-ink);
  --md-border-color: var(--market-line);
  :deep(.md-editor-preview-wrapper) {
    padding: 0;
    background: transparent;
  }
  :deep(.md-editor-preview) {
    color: var(--market-ink);
    overflow-wrap: anywhere;
    word-break: normal;
    font-family: var(--market-font-body);
    font-size: inherit;
    line-height: inherit;
  }
  :deep(.github-theme) {
    --md-theme-color: var(--market-ink);
    --md-theme-heading-color: var(--market-ink);
    --md-theme-link-color: var(--market-primary);
    --md-theme-border-color: var(--market-line);
    --md-theme-table-stripe-color: var(--market-surface-soft);
  }
  :deep(.md-editor-preview h1),
  :deep(.md-editor-preview h2),
  :deep(.md-editor-preview h3) {
    color: var(--market-ink);
    border: 0;
    line-height: 1.5;
    margin-top: 1.6em;
  }
  :deep(.md-editor-preview ul) {
    list-style: disc;
    padding-left: 1.5em;
  }
  :deep(.md-editor-preview ol) {
    list-style: decimal;
    padding-left: 1.5em;
  }
  :deep(.md-editor-preview li) {
    margin: 0.35em 0;
  }
  :deep(.md-editor-preview pre) {
    overflow-x: auto;
  }
  :deep(.md-editor-preview img) {
    max-width: 100%;
  }
}
.post-reactions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  align-items: center;
  button {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 44px;
    padding: 0 4px;
    border: 0;
    background: transparent;
    color: var(--market-muted);
    font: inherit;
    font-size: 14px;
    cursor: pointer;
    transition: color 160ms ease;
    &:hover,
    &.active {
      color: var(--market-primary);
    }
    &:disabled {
      cursor: wait;
      opacity: 0.6;
    }
    &:focus-visible {
      outline: 2px solid var(--market-primary);
      outline-offset: 4px;
    }
  }
  svg,
  .el-icon {
    width: 19px;
    height: 19px;
    font-size: 19px;
  }
  > button > svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 1.6;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .reaction-count {
    font-variant-numeric: tabular-nums;
  }
}
@media (max-width: 600px) {
  .post-detail {
    padding: 8px 0;
  }
  .post-header {
    gap: 10px;
  }
  .post-reactions {
    gap: 10px 20px;
  }
}

.back-to-journal {
  display: inline-block;
  margin-bottom: 28px;
  color: var(--market-muted);
  text-decoration: none;
  font-size: 13px;
}
.article-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: -8px 0 30px;
  color: var(--market-muted);
  font-size: 13px;
}
.article-discussion {
  margin-top: 32px;
  min-width: 0;
}
.join-discussion {
  border: 0;
  background: transparent;
  color: var(--market-primary);
  padding: 12px 0;
  min-height: 44px;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}
.desktop-catalog {
  display: none;
}
.mobile-catalog {
  border-block: 1px solid var(--market-line);
  padding: 14px 0;
  margin-bottom: 28px;
  summary {
    cursor: pointer;
    font-size: 14px;
    color: var(--market-muted);
  }
}
.desktop-catalog,
.mobile-catalog {
  nav {
    display: grid;
    gap: 6px;
    margin-top: 12px;
  }
  button {
    border: 0;
    background: transparent;
    text-align: left;
    padding: 8px 0;
    color: var(--market-muted);
    font: inherit;
    font-size: 13px;
    line-height: 1.6;
    overflow-wrap: anywhere;
    cursor: pointer;
    &:hover {
      color: var(--market-primary);
    }
    &.subheading {
      padding-left: 14px;
    }
  }
}
.post-detail :deep(.md-editor-preview h2),
.post-detail :deep(.md-editor-preview h3) {
  scroll-margin-top: 24px;
}
.post-body :deep(blockquote) {
  border-left: 3px solid var(--market-line-strong);
  padding: 4px 20px;
  color: var(--market-muted);
  background: transparent;
}
.post-body :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
}
.post-body :deep(pre) {
  max-width: 100%;
}
@media (min-width: 1200px) {
  .post-detail.has-catalog {
    max-width: 1000px;
    display: grid;
    grid-template-columns: minmax(0, 740px) 200px;
    gap: 0 60px;
  }
  .has-catalog > .back-to-journal,
  .has-catalog > .agent-return-bar {
    grid-column: 1 / -1;
  }
  .has-catalog > .post-content {
    grid-column: 1;
    grid-row: 2;
  }
  .has-catalog > .article-discussion {
    grid-column: 1;
  }
  .desktop-catalog {
    display: block;
    grid-column: 2;
    grid-row: 2;
    align-self: start;
    position: sticky;
    top: 24px;
    max-height: calc(100dvh - 170px);
    overflow-y: auto;
    border-left: 1px solid var(--market-line);
    padding-left: 20px;
    > span {
      color: var(--market-ink);
      font-size: 13px;
    }
  }
  .mobile-catalog {
    display: none;
  }
}
@media (max-width: 600px) {
  .post-body {
    font-size: 16px;
  }
  .back-to-journal {
    margin-bottom: 20px;
  }
}
</style>
