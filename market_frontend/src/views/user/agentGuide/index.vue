<template>
  <div class="agent-desk" ref="pageRef">
    <button
      v-if="historyDrawerOpen"
      type="button"
      class="mobile-scrim"
      aria-label="关闭会话列表"
      @click="historyDrawerOpen = false"
    ></button>

    <aside class="conversation-rail" :class="{ open: historyDrawerOpen }">
      <div class="rail-heading">
        <div>
          <span class="market-eyebrow">MARKET GUIDE</span>
          <h1>咨询记录</h1>
        </div>
        <button
          type="button"
          class="rail-close"
          aria-label="关闭会话列表"
          @click="historyDrawerOpen = false"
        >
          ×
        </button>
      </div>

      <el-button class="new-chat-button" type="primary" @click="startNewChat">
        <span aria-hidden="true">＋</span>
        新建咨询
      </el-button>

      <div class="conversation-list" v-loading="conversationLoading">
        <button
          v-for="item in conversations"
          :key="item.id"
          type="button"
          class="conversation-ticket"
          :class="{ active: item.id === activeConversationId }"
          @click="selectConversation(item)"
        >
          <span class="ticket-main">
            <strong>{{ item.title || "未命名咨询" }}</strong>
            <em>{{ item.lastMessagePreview || "还没有消息" }}</em>
          </span>
          <span class="ticket-foot">
            <time>{{ formatConversationTime(item.lastMessageTime) }}</time>
            <span class="ticket-actions">
              <span
                class="ticket-archive"
                role="button"
                :tabindex="sending && item.id === activeConversationId ? -1 : 0"
                :aria-disabled="
                  sending && item.id === activeConversationId ? 'true' : 'false'
                "
                aria-label="归档会话"
                @click.stop="archiveConversation(item)"
                @keydown.enter.stop="archiveConversation(item)"
                @keydown.space.prevent.stop="archiveConversation(item)"
              >
                归档
              </span>
              <span
                class="ticket-delete"
                role="button"
                tabindex="0"
                aria-label="删除会话"
                @click.stop="confirmDeleteConversation(item)"
                @keydown.enter.stop="confirmDeleteConversation(item)"
                @keydown.space.prevent.stop="confirmDeleteConversation(item)"
              >
                删除
              </span>
            </span>
          </span>
        </button>

        <div
          v-if="conversationLoadFailed"
          class="history-load-state rail-history-error"
        >
          <strong>暂时无法加载历史记录</strong>
          <p>不影响你发起新的咨询。</p>
          <button type="button" @click="reloadConversations">重新加载</button>
        </div>

        <div
          v-else-if="!conversationLoading && !conversations.length"
          class="rail-empty"
        >
          <span aria-hidden="true">⌁</span>
          <p>还没有历史咨询</p>
          <small>第一条消息会自动建立会话</small>
        </div>
      </div>

      <button
        v-if="conversations.length < conversationTotal"
        type="button"
        class="load-more"
        :disabled="conversationLoading"
        @click="loadMoreConversations"
      >
        加载更多
      </button>

      <div class="rail-note">
        <span class="status-dot" :class="{ offline: agentUnavailable }"></span>
        <span>{{
          agentUnavailable ? "AI 服务暂不可用" : "智能导购可开始咨询"
        }}</span>
      </div>
    </aside>

    <main class="chat-workspace">
      <header class="chat-toolbar">
        <button
          type="button"
          class="icon-button history-trigger"
          aria-label="打开会话列表"
          @click="historyDrawerOpen = true"
        >
          ☰
        </button>
        <div class="chat-title">
          <span class="desk-mark" aria-hidden="true">校</span>
          <div>
            <strong>{{
              activeConversation?.title || "校园市集智能导购台"
            }}</strong>
            <small>先聊需求，再一起缩小选择范围</small>
          </div>
        </div>
        <div class="toolbar-actions">
          <button
            type="button"
            class="context-trigger"
            :class="{ active: contextFieldCount > 0 }"
            @click="contextDrawerOpen = true"
          >
            <span aria-hidden="true">◎</span>
            购买条件
            <b v-if="contextFieldCount">{{ contextFieldCount }}</b>
          </button>
          <el-popover
            placement="bottom-end"
            :width="208"
            trigger="click"
            popper-class="typing-speed-popover"
          >
            <template #reference>
              <button
                type="button"
                class="typing-speed-trigger"
                :title="`回复显示速度：${currentTypingSpeedOption.label}`"
                :aria-label="`调整回复显示速度，当前${currentTypingSpeedOption.label}`"
              >
                {{ currentTypingSpeedOption.marker }}
              </button>
            </template>
            <div
              class="typing-speed-menu"
              role="radiogroup"
              aria-label="AI 回复显示速度"
            >
              <span>回复显示速度</span>
              <button
                v-for="option in typingSpeedOptions"
                :key="option.value"
                type="button"
                role="radio"
                :aria-checked="typingSpeed === option.value"
                :class="{ active: typingSpeed === option.value }"
                @click="setTypingSpeed(option.value)"
              >
                <strong>{{ option.label }}</strong>
                <small>{{ option.description }}</small>
              </button>
            </div>
          </el-popover>
          <button
            type="button"
            class="focus-toggle"
            :aria-label="
              layoutSettingStore.focusMode ? '退出专注模式' : '进入专注模式'
            "
            :aria-pressed="layoutSettingStore.focusMode"
            :title="
              layoutSettingStore.focusMode ? '退出专注模式' : '进入专注模式'
            "
            @click="toggleFocusMode"
          >
            <svg
              v-if="layoutSettingStore.focusMode"
              aria-hidden="true"
              viewBox="0 0 24 24"
            >
              <path d="M4 9h5V4M20 9h-5V4M4 15h5v5M20 15h-5v5" />
            </svg>
            <svg v-else aria-hidden="true" viewBox="0 0 24 24">
              <path d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5" />
            </svg>
          </button>
        </div>
      </header>

      <section
        ref="messageListRef"
        class="message-stage"
        role="log"
        aria-label="咨询消息记录"
        aria-live="polite"
        :aria-busy="Boolean(typingMessageId)"
        tabindex="0"
        @keydown="handleMessageStageKeydown"
        @scroll="handleMessageStageScroll"
      >
        <button
          v-if="hasOlderMessages"
          type="button"
          class="older-messages"
          :disabled="messageLoading"
          @click="loadOlderMessages"
        >
          {{ messageLoading ? "正在加载…" : "查看更早消息" }}
        </button>

        <div v-if="messageLoading && !messages.length" class="stage-loading">
          <span></span><span></span><span></span>
        </div>

        <div
          v-else-if="messageLoadFailed && !messages.length"
          class="history-load-state message-history-error"
        >
          <strong>暂时无法加载此会话的历史消息</strong>
          <p>请稍后重试；这不会影响你新建咨询。</p>
          <el-button size="small" @click="reloadMessages">重新加载</el-button>
        </div>

        <div v-else-if="!messages.length" class="welcome-card">
          <div class="welcome-stamp">智能导购</div>
          <span class="market-eyebrow">AFTER CLASS GUIDE DESK</span>
          <h2>直接说你想买什么</h2>
          <p>
            不用先填完整表单。告诉我商品、用途或困惑，我会继续追问预算和偏好；你也可以随时打开“购买条件”补充信息。
          </p>
          <div class="starter-grid">
            <button
              v-for="starter in starters"
              :key="starter.title"
              type="button"
              @click="applyStarter(starter.prompt)"
            >
              <span>{{ starter.kicker }}</span>
              <strong>{{ starter.title }}</strong>
              <em>{{ starter.desc }}</em>
            </button>
          </div>
        </div>

        <article
          v-for="message in messages"
          :key="message.id"
          class="chat-message"
          :class="message.role.toLowerCase()"
        >
          <div v-if="message.role === 'ASSISTANT'" class="agent-seal">AI</div>
          <div class="message-column">
            <div class="message-meta">
              <strong>{{ message.role === "USER" ? "你" : "校园导购" }}</strong>
              <time>{{ formatMessageTime(message.createTime) }}</time>
            </div>
            <div class="message-bubble" :class="message.status.toLowerCase()">
              <p v-if="message.role === 'USER'">{{ message.content }}</p>
              <template v-else>
                <div v-if="message.status === 'PENDING'" class="thinking-line">
                  <span></span><span></span><span></span>
                  正在翻看摊位清单并整理建议
                </div>
                <template v-else-if="message.status === 'FAILED'">
                  <strong class="failure-title">这次没有收到 Agent 回复</strong>
                  <p>{{ message.content }}</p>
                  <el-button
                    v-if="message.retryable !== false"
                    size="small"
                    :disabled="sending"
                    @click="retryMessage(message.id)"
                  >
                    重新发送
                  </el-button>
                </template>
                <div
                  v-else
                  class="markdown-answer"
                  :class="{ typing: isMessageTyping(message.id) }"
                >
                  <MdPreview
                    class="agent-markdown"
                    :model-value="getDisplayedContent(message)"
                    preview-theme="github"
                    code-theme="github"
                  />
                  <span
                    v-if="isMessageTyping(message.id)"
                    class="typing-caret"
                    aria-hidden="true"
                  ></span>
                </div>
              </template>
            </div>

            <div
              v-if="
                !isMessageTyping(message.id) &&
                message.structuredContent?.recommendations?.length
              "
              class="recommendation-block"
            >
              <div class="recommendation-heading">
                <strong>匹配到的在售商品</strong>
                <span
                  >{{
                    message.structuredContent.recommendations.length
                  }}
                  件</span
                >
              </div>
              <div class="recommendation-grid">
                <button
                  v-for="item in message.structuredContent.recommendations"
                  :key="item.commodity.id"
                  type="button"
                  class="commodity-card"
                  @click="openCommodity(item.commodity.id)"
                >
                  <img
                    v-if="item.commodity.commodityAvatar"
                    :src="item.commodity.commodityAvatar"
                    :alt="item.commodity.commodityName"
                  />
                  <div v-else class="commodity-placeholder">校园好物</div>
                  <div class="commodity-copy">
                    <span v-if="item.matchScore != null" class="match-score">
                      匹配 {{ item.matchScore }}%
                    </span>
                    <strong>{{ item.commodity.commodityName }}</strong>
                    <p v-if="item.reason">{{ item.reason }}</p>
                    <div>
                      <b>¥{{ item.commodity.price }}</b>
                      <em>{{ item.commodity.degree || "成色待确认" }}</em>
                    </div>
                    <small v-if="item.riskTip">验货：{{ item.riskTip }}</small>
                  </div>
                </button>
              </div>
            </div>

            <div
              v-if="
                !isMessageTyping(message.id) && guideSources(message).length
              "
              class="source-block"
            >
              <div class="recommendation-heading">
                <strong>回答参考来源</strong>
                <span>{{ guideSources(message).length }} 条</span>
              </div>
              <button
                v-for="source in guideSources(message)"
                :key="`${source.sourceType}-${source.sourceId}`"
                type="button"
                class="source-link"
                aria-haspopup="dialog"
                :aria-label="`查看来源详情：${source.title}`"
                @click="openSource(source)"
              >
                <span>{{ source.sourceType }}</span>
                <div>
                  <strong>{{ source.title }}</strong>
                  <p>{{ sourcePreview(source) }}</p>
                </div>
                <b aria-hidden="true">查看</b>
              </button>
            </div>

            <div
              v-if="
                !isMessageTyping(message.id) &&
                message.structuredContent?.relatedPosts?.length
              "
              class="related-post-block"
            >
              <div class="recommendation-heading">
                <strong>相关帖子</strong>
                <span
                  >{{ message.structuredContent.relatedPosts.length }} 篇</span
                >
              </div>
              <div class="related-post-grid">
                <button
                  v-for="post in orderedRelatedPosts(message)"
                  :key="post.postId"
                  type="button"
                  class="related-post-card"
                  @click="openRelatedPost(post.postId)"
                >
                  <span
                    v-if="citedPostIds(message).has(String(post.postId))"
                    class="cited-post-badge"
                    >回答引用</span
                  >
                  <strong>{{ post.title }}</strong>
                  <p>{{ post.excerpt }}</p>
                  <div v-if="post.tags?.length" class="related-post-tags">
                    <span v-for="tag in post.tags" :key="tag">#{{ tag }}</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </article>
      </section>

      <footer class="composer-dock">
        <div v-if="contextFieldCount" class="active-context">
          <span>本轮会带上 {{ contextFieldCount }} 项购买条件</span>
          <button type="button" @click="contextDrawerOpen = true">
            查看 / 修改
          </button>
        </div>
        <div class="composer-shell" :class="{ focused: composerFocused }">
          <el-input
            ref="composerRef"
            v-model="composer"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            maxlength="1000"
            resize="none"
            :disabled="sending"
            placeholder="例如：想买一台适合看论文的二手平板，预算还没想好"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <span v-if="messageLoadFailed">
              历史消息加载失败，请重新加载后再继续咨询
            </span>
            <span v-else>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              class="stamp-send"
              type="primary"
              :loading="sending"
              :disabled="!composer.trim() || sending || messageLoadFailed"
              @click="sendMessage"
            >
              盖戳发送
            </el-button>
          </div>
        </div>
      </footer>
    </main>

    <el-drawer
      v-model="contextDrawerOpen"
      title=""
      direction="rtl"
      :size="contextDrawerSize"
      class="shopping-context-drawer"
    >
      <template #header>
        <div class="context-heading">
          <span class="market-eyebrow">Optional Context</span>
          <h2>补充购买条件</h2>
          <p>这些内容不是发送门槛。填过的条件会随当前会话继续使用。</p>
        </div>
      </template>

      <el-form label-position="top" class="context-form">
        <el-form-item label="预算范围（元）">
          <div class="budget-row">
            <el-input-number
              v-model="shoppingContext.budgetMin"
              :min="0"
              :step="50"
              controls-position="right"
              placeholder="最低"
            />
            <span>—</span>
            <el-input-number
              v-model="shoppingContext.budgetMax"
              :min="0"
              :step="50"
              controls-position="right"
              placeholder="最高"
            />
          </div>
        </el-form-item>

        <el-form-item label="使用场景">
          <el-input
            v-model="shoppingContext.usageScene"
            maxlength="300"
            placeholder="例如：图书馆看 PDF、宿舍网课"
          />
        </el-form-item>

        <el-form-item label="偏好标签">
          <el-select
            v-model="shoppingContext.preferenceTags"
            multiple
            filterable
            allow-create
            default-first-option
            :multiple-limit="8"
            placeholder="输入后回车添加，最多 8 项"
          />
        </el-form-item>

        <el-form-item label="避雷项">
          <el-select
            v-model="shoppingContext.avoidances"
            multiple
            filterable
            allow-create
            default-first-option
            :multiple-limit="8"
            placeholder="例如：电池鼓包、账号锁"
          />
        </el-form-item>
      </el-form>

      <div class="context-tip">
        <strong>Agent 会怎么使用？</strong>
        <p>后端会把条件作为结构化上下文保存，不会让前端拼接系统提示词。</p>
      </div>

      <template #footer>
        <div class="context-footer">
          <el-button @click="clearShoppingContext">清空条件</el-button>
          <el-button type="primary" @click="contextDrawerOpen = false">
            保存到当前咨询
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="sourceDialogOpen"
      class="source-detail-dialog"
      :width="sourceDialogWidth"
      align-center
      append-to-body
      destroy-on-close
      :close-on-click-modal="true"
      :close-on-press-escape="true"
    >
      <template #header>
        <div class="source-detail-heading">
          <span class="source-detail-type">{{
            selectedSource?.sourceType
          }}</span>
          <div>
            <h2>{{ selectedSource?.title || "参考来源" }}</h2>
            <p>{{ selectedSource?.documentId || selectedSource?.sourceId }}</p>
          </div>
        </div>
      </template>
      <section class="source-detail-body" aria-label="来源引用正文">
        <div class="source-detail-label">
          本次回答引用
          {{ sourceCitations(selectedSource).length || 1 }} 个片段
        </div>
        <div
          v-if="sourceCitations(selectedSource).length"
          class="source-citation-list"
        >
          <article
            v-for="citation in sourceCitations(selectedSource)"
            :key="citation.chunkId"
            class="source-citation"
          >
            <h3>{{ citation.section || "引用片段" }}</h3>
            <p>{{ citation.content || citation.excerpt }}</p>
            <small>{{ citation.chunkId }}</small>
          </article>
        </div>
        <p v-else>
          {{ selectedSource?.content || selectedSource?.excerpt }}
        </p>
      </section>
      <template #footer>
        <el-button type="primary" @click="sourceDialogOpen = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { MdPreview } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import useLayOutSettingStore from "@/store/modules/setting";
import {
  AI_RAG_MAX_CITATION_COUNT,
  AI_RAG_MAX_SOURCE_COUNT,
  AiChatVO,
  AiConversationVO,
  AiMessageVO,
  AiRagSourceVO,
  AiShoppingContext,
  archiveAiConversation,
  createAiConversation,
  deleteAiConversation,
  listAiConversationMessages,
  listAiConversations,
  sendAiConversationMessage
} from "@/api/aiController";

type Starter = {
  kicker: string;
  title: string;
  desc: string;
  prompt: string;
};

type TypingSpeed = "relaxed" | "standard" | "fast" | "instant";

type TypingSpeedOption = {
  value: TypingSpeed;
  label: string;
  marker: string;
  description: string;
  charactersPerSecond: number;
};

const TYPING_SPEED_STORAGE_KEY = "market-ai-typing-speed";
const typingSpeedOptions: TypingSpeedOption[] = [
  {
    value: "relaxed",
    label: "舒缓",
    marker: "0.6×",
    description: "约 70 字/秒",
    charactersPerSecond: 70
  },
  {
    value: "standard",
    label: "标准",
    marker: "1×",
    description: "约 120 字/秒",
    charactersPerSecond: 120
  },
  {
    value: "fast",
    label: "快速",
    marker: "1.8×",
    description: "约 220 字/秒",
    charactersPerSecond: 220
  },
  {
    value: "instant",
    label: "立即显示",
    marker: "∞",
    description: "关闭打字效果",
    charactersPerSecond: Number.POSITIVE_INFINITY
  }
];

const getInitialTypingSpeed = (): TypingSpeed => {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    return "instant";
  }
  const stored = localStorage.getItem(TYPING_SPEED_STORAGE_KEY);
  return typingSpeedOptions.some((option) => option.value === stored)
    ? (stored as TypingSpeed)
    : "standard";
};

const router = useRouter();
const route = useRoute();
const layoutSettingStore = useLayOutSettingStore();
const pageRef = ref<HTMLElement | null>(null);
const messageListRef = ref<HTMLElement | null>(null);
const composerRef = ref();
const composer = ref("");
const composerFocused = ref(false);
const sending = ref(false);
const conversationLoading = ref(false);
const messageLoading = ref(false);
const agentUnavailable = ref(false);
const conversationLoadFailed = ref(false);
const messageLoadFailed = ref(false);
const historyDrawerOpen = ref(false);
const contextDrawerOpen = ref(false);
const viewportWidth = ref(window.innerWidth);
const conversations = ref<AiConversationVO[]>([]);
const messages = ref<AiMessageVO[]>([]);
const activeConversationId = ref<string | null>(null);
const conversationPage = ref(1);
const conversationTotal = ref(0);
const archivingConversationId = ref<string | null>(null);
const messagePage = ref(1);
const messageTotal = ref(0);
const typingSpeed = ref<TypingSpeed>(getInitialTypingSpeed());
const typingMessageId = ref<string | null>(null);
const typingBuffers = reactive<Record<string, string>>({});
const shouldFollowOutput = ref(true);
const sourceDialogOpen = ref(false);
const selectedSource = ref<AiRagSourceVO | null>(null);

let typingAnimationFrame: number | null = null;
let messageResizeObserver: ResizeObserver | null = null;
let resizeFollowFrame: number | null = null;

const shoppingContext = reactive<{
  budgetMin?: number;
  budgetMax?: number;
  usageScene: string;
  preferenceTags: string[];
  avoidances: string[];
}>({
  budgetMin: undefined,
  budgetMax: undefined,
  usageScene: "",
  preferenceTags: [],
  avoidances: []
});

const starters: Starter[] = [
  {
    kicker: "数码",
    title: "选一台学习平板",
    desc: "从用途和验机重点聊起",
    prompt: "想买一台主要看论文和上网课的二手平板，应该怎么选？"
  },
  {
    kicker: "教材",
    title: "判断教材值不值",
    desc: "版本、笔记与价格一起看",
    prompt: "我想买本学期的二手教材，怎么判断版本和价格是否合适？"
  },
  {
    kicker: "避雷",
    title: "帮我列验货清单",
    desc: "面交前先把风险问清楚",
    prompt: "校内面交二手数码产品时，有哪些必须检查和询问的项目？"
  }
];

const activeConversation = computed(() =>
  conversations.value.find((item) => item.id === activeConversationId.value)
);

const contextFieldCount = computed(() => {
  let count = 0;
  if (shoppingContext.budgetMin != null || shoppingContext.budgetMax != null)
    count += 1;
  if (shoppingContext.usageScene.trim()) count += 1;
  if (shoppingContext.preferenceTags.length) count += 1;
  if (shoppingContext.avoidances.length) count += 1;
  return count;
});

const contextDrawerSize = computed(() =>
  viewportWidth.value <= 720 ? "92%" : "420px"
);
const sourceDialogWidth = computed(() =>
  viewportWidth.value <= 660 ? "calc(100vw - 24px)" : "720px"
);
const hasOlderMessages = computed(
  () => messages.value.length < messageTotal.value
);
const currentTypingSpeedOption = computed(
  () =>
    typingSpeedOptions.find((option) => option.value === typingSpeed.value) ||
    typingSpeedOptions[1]
);

const handleResize = () => {
  viewportWidth.value = window.innerWidth;
  if (window.innerWidth > 900) historyDrawerOpen.value = false;
};

const toggleFocusMode = () => {
  layoutSettingStore.focusMode = !layoutSettingStore.focusMode;
  historyDrawerOpen.value = false;
};

const normalizeContext = (source?: AiShoppingContext | null) => {
  shoppingContext.budgetMin = source?.budgetMin;
  shoppingContext.budgetMax = source?.budgetMax;
  shoppingContext.usageScene = source?.usageScene || "";
  shoppingContext.preferenceTags = [...(source?.preferenceTags || [])];
  shoppingContext.avoidances = [...(source?.avoidances || [])];
};

const getShoppingContext = (): AiShoppingContext | undefined => {
  const context: AiShoppingContext = {};
  if (shoppingContext.budgetMin != null)
    context.budgetMin = shoppingContext.budgetMin;
  if (shoppingContext.budgetMax != null)
    context.budgetMax = shoppingContext.budgetMax;
  if (shoppingContext.usageScene.trim()) {
    context.usageScene = shoppingContext.usageScene.trim();
  }
  if (shoppingContext.preferenceTags.length) {
    context.preferenceTags = shoppingContext.preferenceTags
      .map((item) => item.trim())
      .filter(Boolean);
  }
  if (shoppingContext.avoidances.length) {
    context.avoidances = shoppingContext.avoidances
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return Object.keys(context).length ? context : undefined;
};

const clearShoppingContext = () => normalizeContext(null);

const formatConversationTime = (value?: string) => {
  if (!value) return "刚刚";
  const date = new Date(value.replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric"
  }).format(date);
};

const formatMessageTime = (value?: string) => {
  if (!value) return "刚刚";
  const date = new Date(value.replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
};

const scrollStageToBottom = () => {
  const messageStage = messageListRef.value;
  if (!messageStage) return;
  messageStage.scrollTop = messageStage.scrollHeight;
};

const scrollToBottom = async () => {
  shouldFollowOutput.value = true;
  await nextTick();
  scrollStageToBottom();
};

const handleMessageStageScroll = () => {
  const messageStage = messageListRef.value;
  if (!messageStage) return;
  const distanceFromBottom =
    messageStage.scrollHeight -
    messageStage.scrollTop -
    messageStage.clientHeight;
  shouldFollowOutput.value = distanceFromBottom <= 96;
};

const refreshMessageResizeTargets = async () => {
  await nextTick();
  const messageStage = messageListRef.value;
  if (!messageStage || typeof ResizeObserver === "undefined") return;

  if (!messageResizeObserver) {
    messageResizeObserver = new ResizeObserver(() => {
      if (!shouldFollowOutput.value || resizeFollowFrame != null) return;
      resizeFollowFrame = window.requestAnimationFrame(() => {
        resizeFollowFrame = null;
        scrollStageToBottom();
      });
    });
  }

  messageResizeObserver.disconnect();
  messageStage
    .querySelectorAll(
      ".chat-message, .welcome-card, .stage-loading, .history-load-state"
    )
    .forEach((element) => messageResizeObserver?.observe(element));
};

const splitGraphemes = (content: string): string[] => {
  const Segmenter = (Intl as any).Segmenter;
  if (!Segmenter) return Array.from(content);
  const segmenter = new Segmenter("zh-CN", { granularity: "grapheme" });
  return Array.from(
    segmenter.segment(content),
    (entry: { segment: string }) => entry.segment
  );
};

const isMessageTyping = (messageId: string) =>
  typingMessageId.value === messageId;

const getDisplayedContent = (message: AiMessageVO) =>
  Object.prototype.hasOwnProperty.call(typingBuffers, message.id)
    ? typingBuffers[message.id]
    : message.content;

const finishActiveTyping = () => {
  if (typingAnimationFrame != null) {
    window.cancelAnimationFrame(typingAnimationFrame);
    typingAnimationFrame = null;
  }
  const messageId = typingMessageId.value;
  if (messageId) delete typingBuffers[messageId];
  typingMessageId.value = null;
  if (shouldFollowOutput.value) {
    window.requestAnimationFrame(scrollStageToBottom);
  }
};

const setTypingSpeed = (value: TypingSpeed) => {
  typingSpeed.value = value;
  localStorage.setItem(TYPING_SPEED_STORAGE_KEY, value);
  if (value === "instant") finishActiveTyping();
};

const startTypingMessage = (message: AiMessageVO) => {
  finishActiveTyping();
  if (
    message.role !== "ASSISTANT" ||
    message.status !== "SUCCESS" ||
    !message.content ||
    !Number.isFinite(currentTypingSpeedOption.value.charactersPerSecond)
  ) {
    return;
  }

  const units = splitGraphemes(message.content);
  if (units.length <= 1) return;

  typingBuffers[message.id] = "";
  typingMessageId.value = message.id;
  shouldFollowOutput.value = true;

  let index = 0;
  let characterBudget = 0;
  let previousTime = performance.now();
  let previousPaint = previousTime;

  const renderFrame = (currentTime: number) => {
    if (typingMessageId.value !== message.id) return;
    const charactersPerSecond =
      currentTypingSpeedOption.value.charactersPerSecond;
    if (!Number.isFinite(charactersPerSecond)) {
      finishActiveTyping();
      return;
    }

    characterBudget +=
      ((currentTime - previousTime) * charactersPerSecond) / 1000;
    previousTime = currentTime;

    if (currentTime - previousPaint >= 30) {
      const revealCount = Math.floor(characterBudget);
      if (revealCount > 0) {
        const nextIndex = Math.min(units.length, index + revealCount);
        typingBuffers[message.id] += units.slice(index, nextIndex).join("");
        index = nextIndex;
        characterBudget -= revealCount;
      }
      previousPaint = currentTime;
    }

    if (index >= units.length) {
      finishActiveTyping();
      return;
    }
    typingAnimationFrame = window.requestAnimationFrame(renderFrame);
  };

  typingAnimationFrame = window.requestAnimationFrame(renderFrame);
};

watch(
  () => messages.value.map((message) => message.id).join("|"),
  () => {
    void refreshMessageResizeTargets();
  },
  { flush: "post" }
);

const handleMessageStageKeydown = (event: KeyboardEvent) => {
  if (event.currentTarget !== event.target || !messageListRef.value) return;

  const messageStage = messageListRef.value;
  const pageStep = Math.max(160, Math.floor(messageStage.clientHeight * 0.8));
  const keyScrollOffsets: Record<string, number> = {
    ArrowDown: 48,
    ArrowUp: -48,
    PageDown: pageStep,
    PageUp: -pageStep
  };

  if (event.key === "Home") {
    event.preventDefault();
    messageStage.scrollTo({ top: 0, behavior: "smooth" });
    return;
  }
  if (event.key === "End") {
    event.preventDefault();
    messageStage.scrollTo({
      top: messageStage.scrollHeight,
      behavior: "smooth"
    });
    return;
  }
  if (!(event.key in keyScrollOffsets)) return;

  event.preventDefault();
  messageStage.scrollBy({
    top: keyScrollOffsets[event.key],
    behavior: "smooth"
  });
};

const loadConversations = async (append = false) => {
  conversationLoading.value = true;
  try {
    const res = await listAiConversations(
      conversationPage.value,
      10,
      "lastMessageTime",
      "desc",
      "ACTIVE"
    );
    if (res.code !== 200 || !res.data)
      throw new Error(res.message || "会话加载失败");
    conversations.value = append
      ? [...conversations.value, ...res.data.records]
      : res.data.records;
    conversationTotal.value = res.data.total;
    conversationLoadFailed.value = false;
    return true;
  } catch (_error) {
    if (!append) {
      conversationLoadFailed.value = true;
      conversations.value = [];
    } else {
      ElMessage.error("更多历史记录暂时无法加载");
    }
    return false;
  } finally {
    conversationLoading.value = false;
  }
};

const loadMoreConversations = async () => {
  const previousPage = conversationPage.value;
  conversationPage.value += 1;
  const loaded = await loadConversations(true);
  if (!loaded) conversationPage.value = previousPage;
};

const reloadConversations = async () => {
  conversationPage.value = 1;
  await loadConversations();
};

const loadMessages = async (conversationId: string, older = false) => {
  const previousScrollHeight = older
    ? messageListRef.value?.scrollHeight || 0
    : 0;
  messageLoading.value = true;
  try {
    const res = await listAiConversationMessages(
      conversationId,
      messagePage.value,
      20
    );
    if (res.code !== 200 || !res.data)
      throw new Error(res.message || "消息加载失败");
    const records = [...res.data.records].sort(
      (a, b) => (a.sequenceNo || 0) - (b.sequenceNo || 0)
    );
    messages.value = older ? [...records, ...messages.value] : records;
    messageTotal.value = res.data.total;
    messageLoadFailed.value = false;
    if (older) {
      await nextTick();
      if (messageListRef.value) {
        messageListRef.value.scrollTop +=
          messageListRef.value.scrollHeight - previousScrollHeight;
      }
    } else {
      await scrollToBottom();
    }
    return true;
  } catch (_error) {
    if (!older) {
      messages.value = [];
      messageLoadFailed.value = true;
    }
    return false;
  } finally {
    messageLoading.value = false;
  }
};

const loadOlderMessages = async () => {
  if (!activeConversationId.value) return;
  const previousPage = messagePage.value;
  messagePage.value += 1;
  const loaded = await loadMessages(activeConversationId.value, true);
  if (!loaded) messagePage.value = previousPage;
};

const reloadMessages = async () => {
  if (!activeConversationId.value) return;
  messagePage.value = 1;
  await loadMessages(activeConversationId.value);
};

const startNewChat = () => {
  finishActiveTyping();
  activeConversationId.value = null;
  messages.value = [];
  messagePage.value = 1;
  messageTotal.value = 0;
  messageLoadFailed.value = false;
  clearShoppingContext();
  historyDrawerOpen.value = false;
  nextTick(() => composerRef.value?.focus());
};

watch(activeConversationId, (conversationId) => {
  const current = Array.isArray(route.query.conversationId)
    ? route.query.conversationId[0]
    : route.query.conversationId;
  if ((current || null) === conversationId) return;
  const query = { ...route.query };
  if (conversationId) query.conversationId = conversationId;
  else delete query.conversationId;
  void router.replace({ query });
});

const selectConversation = async (item: AiConversationVO) => {
  finishActiveTyping();
  activeConversationId.value = item.id;
  messagePage.value = 1;
  messageTotal.value = 0;
  messages.value = [];
  messageLoadFailed.value = false;
  normalizeContext(item.shoppingContext);
  historyDrawerOpen.value = false;
  await loadMessages(item.id);
};

const archiveConversation = async (item: AiConversationVO) => {
  if (
    archivingConversationId.value ||
    (sending.value && item.id === activeConversationId.value)
  ) {
    if (sending.value && item.id === activeConversationId.value) {
      ElMessage.warning("当前会话正在回复中，回复完成后再归档");
    }
    return;
  }
  archivingConversationId.value = item.id;
  try {
    const res = await archiveAiConversation(item.id);
    if (res.code !== 200 || res.data !== true) {
      throw new Error(res.message || "归档失败");
    }
    conversations.value = conversations.value.filter(
      (record) => record.id !== item.id
    );
    conversationTotal.value = Math.max(0, conversationTotal.value - 1);
    if (activeConversationId.value === item.id) startNewChat();
    ElMessage.success("会话已归档");
  } catch (error: any) {
    ElMessage.error(error?.message || "归档失败");
  } finally {
    archivingConversationId.value = null;
  }
};

const confirmDeleteConversation = async (item: AiConversationVO) => {
  try {
    await ElMessageBox.confirm(
      `删除「${item.title || "未命名咨询"}」后将无法在列表中恢复。`,
      "删除咨询",
      { confirmButtonText: "删除", cancelButtonText: "取消", type: "warning" }
    );
    const res = await deleteAiConversation(item.id);
    if (res.code !== 200 || res.data !== true)
      throw new Error(res.message || "删除失败");
    conversations.value = conversations.value.filter(
      (record) => record.id !== item.id
    );
    conversationTotal.value = Math.max(0, conversationTotal.value - 1);
    if (activeConversationId.value === item.id) startNewChat();
    ElMessage.success("咨询已删除");
  } catch (error: any) {
    if (error === "cancel" || error === "close") return;
    if (error?.message && error.message !== "cancel")
      ElMessage.error(error.message);
  }
};

const makeLocalMessage = (
  role: AiMessageVO["role"],
  content: string,
  status: AiMessageVO["status"]
): AiMessageVO => ({
  id: `local-${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
  role,
  content,
  status,
  createTime: new Date().toISOString()
});

const applyServerResponse = async (data: AiChatVO, localIds: string[]) => {
  messages.value = messages.value.filter(
    (message) => !localIds.includes(message.id)
  );
  messages.value.push(data.userMessage, data.assistantMessage);
  activeConversationId.value = data.conversation.id;
  const index = conversations.value.findIndex(
    (item) => item.id === data.conversation.id
  );
  if (index >= 0) conversations.value.splice(index, 1);
  conversations.value.unshift(data.conversation);
  conversationTotal.value = Math.max(
    conversationTotal.value,
    conversations.value.length
  );
  normalizeContext(data.conversation.shoppingContext || getShoppingContext());
  agentUnavailable.value = false;
  conversationLoadFailed.value = false;
  startTypingMessage(data.assistantMessage);
  await scrollToBottom();
};

const submitContent = async (
  content: string,
  options: { appendUser: boolean; failedMessageId?: string } = {
    appendUser: true
  }
) => {
  finishActiveTyping();
  const localIds: string[] = [];
  if (options.failedMessageId) {
    messages.value = messages.value.filter(
      (item) => item.id !== options.failedMessageId
    );
  }
  if (options.appendUser) {
    const userMessage = makeLocalMessage("USER", content, "SUCCESS");
    messages.value.push(userMessage);
    localIds.push(userMessage.id);
  }
  const pendingMessage = makeLocalMessage("ASSISTANT", "", "PENDING");
  messages.value.push(pendingMessage);
  localIds.push(pendingMessage.id);
  sending.value = true;
  await scrollToBottom();

  try {
    const body = { content, shoppingContext: getShoppingContext() };
    const res = activeConversationId.value
      ? await sendAiConversationMessage(activeConversationId.value, body)
      : await createAiConversation(body);
    if (res.code !== 200 || !res.data)
      throw new Error(res.message || "Agent 服务暂时不可用");
    await applyServerResponse(res.data, localIds);
  } catch (error: any) {
    agentUnavailable.value = true;
    messages.value = messages.value.filter(
      (item) => item.id !== pendingMessage.id
    );
    messages.value.push({
      ...makeLocalMessage(
        "ASSISTANT",
        error?.message || "AI 服务暂不可用，请检查服务后重试。",
        "FAILED"
      ),
      retryable: true
    });
    if (
      error?.message &&
      !error?.requestMessageShown &&
      !String(error.message).includes("404")
    ) {
      ElMessage.error(error.message);
    }
    await scrollToBottom();
  } finally {
    sending.value = false;
  }
};

const sendMessage = async () => {
  const content = composer.value.trim();
  if (!content || sending.value) return;
  composer.value = "";
  await submitContent(content);
};

const retryMessage = async (failedMessageId: string) => {
  const failedIndex = messages.value.findIndex(
    (item) => item.id === failedMessageId
  );
  const userMessage = [...messages.value.slice(0, failedIndex)]
    .reverse()
    .find((item) => item.role === "USER");
  if (!userMessage) return;
  await submitContent(userMessage.content, {
    appendUser: false,
    failedMessageId
  });
};

const handleComposerKeydown = (event: KeyboardEvent) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }
};

const applyStarter = (prompt: string) => {
  composer.value = prompt;
  nextTick(() => composerRef.value?.focus());
};

const openSource = (source: AiRagSourceVO) => {
  if (source.sourceType === "GUIDE") {
    selectedSource.value = source;
    sourceDialogOpen.value = true;
    return;
  }
  if (source.targetPath) void router.push(source.targetPath);
};

const sourcePreview = (source: AiRagSourceVO) =>
  source.citations?.[0]?.excerpt || source.excerpt || "查看本次引用片段";

const boundedSources = (message: AiMessageVO) =>
  (message.structuredContent?.sources || []).slice(0, AI_RAG_MAX_SOURCE_COUNT);

const sourceCitations = (source?: AiRagSourceVO | null) =>
  (source?.citations || []).slice(0, AI_RAG_MAX_CITATION_COUNT);

const guideSources = (message: AiMessageVO) =>
  boundedSources(message).filter((source) => source.sourceType === "GUIDE");

const citedPostIds = (message: AiMessageVO) =>
  new Set(
    boundedSources(message)
      .filter((source) => source.sourceType === "POST")
      .map((source) => source.sourceId)
  );

const orderedRelatedPosts = (message: AiMessageVO) => {
  const posts = message.structuredContent?.relatedPosts || [];
  const citedIds = citedPostIds(message);
  return [
    ...posts.filter((post) => citedIds.has(String(post.postId))),
    ...posts.filter((post) => !citedIds.has(String(post.postId)))
  ];
};

const openRelatedPost = (postId: number) => {
  void router.push({
    path: `/user/post/${postId}`,
    query: {
      from: "agent",
      ...(activeConversationId.value
        ? { conversationId: activeConversationId.value }
        : {})
    }
  });
};

const openCommodity = (commodityId: string) => {
  void router.push({
    path: `/user/commodity/detail/${commodityId}`,
    query: {
      from: "agent",
      ...(activeConversationId.value
        ? { conversationId: activeConversationId.value }
        : {})
    }
  });
};

onMounted(async () => {
  layoutSettingStore.focusMode = true;
  window.addEventListener("resize", handleResize);
  const loaded = await loadConversations();
  const requestedConversationId = Array.isArray(route.query.conversationId)
    ? route.query.conversationId[0]
    : route.query.conversationId;
  if (!loaded || !requestedConversationId) return;
  const conversation = conversations.value.find(
    (item) => item.id === requestedConversationId
  );
  if (conversation) {
    await selectConversation(conversation);
    return;
  }
  activeConversationId.value = requestedConversationId;
  messagePage.value = 1;
  const restored = await loadMessages(requestedConversationId);
  if (!restored) {
    startNewChat();
    ElMessage.warning("原咨询已不可用，已为你打开新咨询");
  }
});

onBeforeUnmount(() => {
  finishActiveTyping();
  messageResizeObserver?.disconnect();
  messageResizeObserver = null;
  if (resizeFollowFrame != null) {
    window.cancelAnimationFrame(resizeFollowFrame);
    resizeFollowFrame = null;
  }
  layoutSettingStore.focusMode = false;
  window.removeEventListener("resize", handleResize);
});
</script>

<style scoped lang="scss">
.agent-desk {
  display: grid;
  grid-template-columns: 278px minmax(0, 1fr);
  width: 100%;
  height: 100%;
  max-height: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: var(--market-radius-lg);
  background: var(--market-surface);
  box-shadow: var(--market-shadow);
}

button {
  font: inherit;
}

.conversation-rail {
  position: relative;
  z-index: 4;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 22px 16px 16px;
  border-right: 1px solid var(--market-line);
  background: var(--market-sidebar-bg);
}

.rail-heading,
.chat-toolbar,
.chat-title,
.ticket-foot,
.composer-actions,
.context-footer,
.recommendation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rail-heading h1 {
  margin: 5px 0 0;
  color: var(--market-ink);
  font-size: 24px;
}
.rail-close {
  display: none;
  border: 0;
  color: var(--market-muted);
  font-size: 28px;
  background: transparent;
}
.new-chat-button {
  width: 100%;
  margin: 20px 0 14px;
  font-weight: 800;
}
.new-chat-button span {
  margin-right: 5px;
  font-size: 20px;
}
.conversation-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.conversation-ticket {
  position: relative;
  width: 100%;
  margin-bottom: 8px;
  padding: 13px 12px 13px 20px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-surface);
  cursor: pointer;
  transition: 0.2s ease;

  &::before {
    position: absolute;
    top: 6px;
    bottom: 6px;
    left: 3px;
    width: 8px;
    background: radial-gradient(
      circle,
      transparent 0 2.5px,
      var(--market-line) 3px 3.5px,
      transparent 4px
    );
    background-size: 8px 13px;
    content: "";
  }
}
.conversation-ticket:hover {
  border-color: rgba(47, 125, 92, 0.28);
  background: var(--market-card-bg);
  box-shadow: 0 8px 18px rgba(62, 45, 24, 0.07);
  transform: translateY(-1px);
}
.conversation-ticket:focus-visible {
  border-color: rgba(47, 125, 92, 0.48);
  box-shadow: var(--market-focus);
  outline: none;
}
.conversation-ticket.active {
  border-color: rgba(47, 125, 92, 0.32);
  background: var(--market-menu-active-bg);

  .ticket-main {
    padding-right: 56px;
  }

  &::after {
    position: absolute;
    top: 7px;
    right: 8px;
    padding: 2px 5px;
    border: 1.5px solid var(--market-stamp-red);
    border-radius: 3px;
    color: var(--market-stamp-red);
    font-family: var(--market-font-display);
    font-size: 9px;
    font-weight: 900;
    letter-spacing: 1px;
    content: "咨询中";
    transform: rotate(-7deg);
  }
}
.ticket-main {
  display: grid;
  gap: 5px;
}
.ticket-main strong,
.ticket-main em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ticket-main strong {
  font-size: 14px;
}
.ticket-main em {
  color: var(--market-muted);
  font-size: 12px;
  font-style: normal;
}
.ticket-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  color: var(--market-muted);
  font-size: 11px;
}
.ticket-actions {
  display: inline-flex;
  gap: 10px;
}
.ticket-archive,
.ticket-delete {
  opacity: 0;
  transition: opacity 0.2s ease;
}
.ticket-archive {
  color: var(--market-green);
}
.ticket-archive[aria-disabled="true"] {
  cursor: not-allowed;
  opacity: 0.42;
}
.ticket-archive:focus-visible,
.ticket-delete:focus-visible {
  border-radius: 2px;
  outline: 2px solid var(--market-green);
  outline-offset: 2px;
}
.ticket-delete {
  color: var(--market-red);
}
.conversation-ticket:hover .ticket-archive,
.conversation-ticket.active .ticket-archive,
.conversation-ticket:hover .ticket-delete,
.conversation-ticket.active .ticket-delete {
  opacity: 1;
}

.rail-empty {
  padding: 56px 10px;
  color: var(--market-muted);
  text-align: center;
}
.history-load-state {
  display: grid;
  justify-items: start;
  gap: 7px;
  padding: 22px 16px;
  border: 1px dashed rgba(217, 108, 44, 0.42);
  border-radius: 8px;
  color: var(--market-ink);
  background: var(--market-note-yellow-bg);
}
.history-load-state p {
  margin: 0;
  color: var(--market-muted);
  font-size: 12px;
  line-height: 1.6;
}
.rail-history-error > button {
  border: 0;
  color: var(--market-orange);
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}
.rail-history-error {
  margin: 12px 0;
}
.rail-empty span {
  display: block;
  color: var(--market-board);
  font-size: 48px;
}
.rail-empty p {
  margin: 6px 0;
  color: var(--market-ink);
  font-weight: 800;
}
.load-more {
  margin: 4px auto 10px;
  border: 0;
  color: var(--market-green);
  background: transparent;
  cursor: pointer;
}
.rail-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 11px;
  border-top: 1px solid var(--market-line);
  color: var(--market-muted);
  font-size: 12px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--market-green);
}
.status-dot.offline {
  background: var(--market-orange);
}

.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  background: radial-gradient(
      ellipse at 52% -8%,
      rgba(244, 201, 93, 0.16),
      transparent 36%
    ),
    linear-gradient(rgba(35, 49, 63, 0.018), rgba(35, 49, 63, 0.018)),
    var(--market-paper);
}
.chat-toolbar,
.composer-dock {
  min-width: 0;
  flex-shrink: 0;
}
.chat-toolbar {
  flex: 0 0 auto;
  min-height: 72px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--market-line);
  background: linear-gradient(90deg, rgba(47, 125, 92, 0.05), transparent 28%),
    var(--market-topbar-bg);
  backdrop-filter: blur(12px);
  box-shadow: 0 5px 18px rgba(62, 45, 24, 0.04);
}
.chat-title {
  justify-content: flex-start;
  gap: 11px;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.typing-speed-trigger {
  min-width: 34px;
  height: 32px;
  padding: 0 4px;
  border: 0;
  border-bottom: 1px solid transparent;
  color: var(--market-muted);
  font-family: var(--market-font-display);
  font-size: 11px;
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}
.typing-speed-trigger:hover,
.typing-speed-trigger:focus-visible {
  border-bottom-color: var(--market-orange);
  color: var(--market-green);
}
.chat-title strong {
  display: block;
  color: var(--market-ink);
  font-size: 16px;
}
.chat-title small {
  display: block;
  margin-top: 3px;
  color: var(--market-muted);
}
.desk-mark,
.agent-seal {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: #fff;
  font-weight: 900;
  background: var(--market-green);
}
.desk-mark {
  width: 38px;
  height: 38px;
  box-shadow: 0 0 0 4px var(--market-note-green-bg);
}
.agent-seal {
  width: 34px;
  height: 34px;
  margin-top: 20px;
  border: 2px solid rgba(255, 255, 255, 0.7);
  font-size: 12px;
  box-shadow: 0 0 0 3px rgba(47, 125, 92, 0.16);
  transform: rotate(-6deg);
}
.icon-button,
.context-trigger,
.focus-toggle {
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  background: var(--market-surface);
  cursor: pointer;
}
.history-trigger {
  display: none;
  width: 38px;
  height: 38px;
  margin-right: 10px;
}
.context-trigger {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 12px;
  font-weight: 800;
}
.focus-toggle {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  flex: 0 0 36px;
  padding: 0;
  border-color: transparent;
  border-radius: 50%;
  color: var(--market-muted);
  background: transparent;
}
.focus-toggle svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
.focus-toggle:hover {
  color: var(--market-green);
  background: var(--market-note-green-bg);
}
.context-trigger {
  flex: 0 0 auto;
}
.focus-toggle:focus-visible {
  border-color: rgba(47, 125, 92, 0.35);
}
.context-trigger.active {
  border-color: rgba(217, 108, 44, 0.42);
  color: var(--market-orange);
}
.context-trigger b {
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  background: var(--market-orange);
}

.message-stage {
  height: auto;
  max-height: 100%;
  min-height: 0;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 38px clamp(20px, 4vw, 64px);
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  -webkit-overflow-scrolling: touch;
  contain: size layout;
  outline: none;
}
.message-stage:focus-visible {
  box-shadow: inset 0 0 0 2px rgba(47, 125, 92, 0.42);
}
.welcome-card {
  position: relative;
  max-width: 800px;
  margin: 7vh auto 0;
  padding: 34px;
  border: 1px solid var(--market-line);
  border-radius: 10px;
  background: var(--market-card-bg);
  box-shadow: var(--market-shadow-soft);
}
.welcome-card::before {
  position: absolute;
  top: 0;
  left: 28px;
  width: 88px;
  height: 7px;
  background: var(--market-orange);
  content: "";
}
.welcome-stamp {
  position: absolute;
  top: 24px;
  right: 28px;
  padding: 6px 10px;
  border: 2px solid var(--market-green);
  border-radius: 999px;
  color: var(--market-green);
  font-size: 12px;
  font-weight: 900;
  transform: rotate(5deg);
}
.welcome-card h2 {
  max-width: 620px;
  margin: 12px 0 10px;
  color: var(--market-ink);
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.15;
}
.welcome-card > p {
  max-width: 660px;
  color: var(--market-muted);
  line-height: 1.8;
}
.starter-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 26px;
}
.starter-grid button {
  display: grid;
  gap: 7px;
  padding: 15px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-surface);
  cursor: pointer;
}
.starter-grid button:hover {
  border-color: rgba(217, 108, 44, 0.45);
  transform: translateY(-2px);
}
.starter-grid span {
  color: var(--market-orange);
  font-size: 11px;
  font-weight: 900;
}
.starter-grid strong {
  font-size: 15px;
}
.starter-grid em {
  color: var(--market-muted);
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.chat-message {
  display: flex;
  width: min(100%, 960px);
  gap: 12px;
  margin: 0 auto 28px;
}
.chat-message.user {
  justify-content: flex-end;
}
.message-column {
  min-width: 0;
  max-width: min(780px, calc(100% - 48px));
}
.chat-message.assistant .message-column {
  width: min(780px, calc(100% - 48px));
}
.chat-message.user .message-column {
  display: grid;
  max-width: min(720px, 78%);
  justify-items: end;
}
.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 5px 6px;
  color: var(--market-muted);
  font-size: 11px;
}
.message-meta strong {
  color: var(--market-ink);
  font-size: 12px;
}
.message-bubble {
  position: relative;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  padding: 15px 18px;
  border: 1px solid var(--market-line);
  border-radius: 6px 13px 13px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  overflow-wrap: anywhere;
  word-break: break-word;
}
.chat-message.assistant .message-bubble:not(.failed) {
  overflow: visible;
  padding: 20px 22px;
  border-color: rgba(253, 246, 227, 0.18);
  border-radius: 5px 14px 14px 14px;
  color: var(--market-chalk);
  background: radial-gradient(
        circle at 1px 1px,
        rgba(253, 246, 227, 0.045) 1px,
        transparent 1.2px
      )
      0 0 / 17px 17px,
    linear-gradient(145deg, #1e4f8f, #183d70);
  box-shadow: 0 15px 30px rgba(27, 53, 44, 0.19),
    inset 0 0 0 1px rgba(253, 246, 227, 0.035);

  &::before {
    position: absolute;
    top: -9px;
    left: 19px;
    width: 9px;
    height: 25px;
    border: 2px solid var(--market-ticket-pink);
    border-radius: 999px;
    content: "";
    transform: rotate(18deg);
  }
}
.chat-message.user .message-bubble {
  border-color: rgba(47, 125, 92, 0.24);
  border-radius: 14px 7px 14px 14px;
  background: linear-gradient(
      135deg,
      transparent calc(100% - 15px),
      rgba(47, 125, 92, 0.08) 0
    ),
    var(--market-paper-deep);
  box-shadow: 0 8px 20px rgba(62, 45, 24, 0.08);
  transform: rotate(-0.35deg);
}
.message-bubble p {
  margin: 0;
  line-height: 1.75;
  white-space: pre-wrap;
}
.message-bubble.failed {
  border-color: rgba(198, 69, 69, 0.3);
  background: rgba(198, 69, 69, 0.07);
}
.failure-title {
  display: block;
  margin-bottom: 7px;
  color: var(--market-red);
}
.failure-title + p {
  margin-bottom: 12px;
  color: var(--market-muted);
}
.thinking-line {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  border-left: 3px solid var(--market-ticket-pink);
  border-radius: 4px;
  color: var(--market-chalk);
  background: rgba(253, 246, 227, 0.08);
}
.thinking-line span,
.stage-loading span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--market-orange);
  animation: bounce 1s infinite ease-in-out;
}
.thinking-line span:nth-child(2),
.stage-loading span:nth-child(2) {
  animation-delay: 0.12s;
}
.thinking-line span:nth-child(3),
.stage-loading span:nth-child(3) {
  margin-right: 5px;
  animation-delay: 0.24s;
}
.stage-loading {
  display: flex;
  justify-content: center;
  gap: 7px;
  padding: 80px;
}
.older-messages {
  display: block;
  margin: 0 auto 24px;
  border: 0;
  color: var(--market-green);
  background: transparent;
  cursor: pointer;
}
.markdown-answer {
  min-width: 0;
  max-width: 100%;
  color: var(--market-chalk);
  overscroll-behavior-x: contain;
}

.markdown-answer :deep(.md-editor) {
  --md-color: var(--market-chalk);
  --md-hover-color: #fff4d6;
  --md-bk-color: transparent;
  --md-bk-color-outstand: rgba(253, 246, 227, 0.08);
  --md-bk-hover-color: rgba(253, 246, 227, 0.1);
  --md-border-color: rgba(253, 246, 227, 0.18);
  --md-border-hover-color: rgba(253, 246, 227, 0.3);
  --md-border-active-color: rgba(253, 246, 227, 0.42);
  width: 100%;
  min-width: 0;
  height: auto;
  border: 0;
  color: var(--market-chalk);
  background: transparent;
}

.markdown-answer :deep(.md-editor-content) {
  min-width: 0;
  height: auto;
}

.markdown-answer :deep(.md-editor-preview-wrapper) {
  min-width: 0;
  overflow: visible;
  padding: 0;
  background: transparent;
}

.markdown-answer.typing {
  position: relative;
  padding-bottom: 9px;
}

.typing-caret {
  position: absolute;
  right: 3px;
  bottom: 0;
  width: 7px;
  height: 14px;
  border-radius: 2px;
  background: var(--market-ticket-pink);
  animation: typing-blink 0.75s steps(1, end) infinite;
}

.markdown-answer :deep(.md-editor-preview) {
  --md-theme-color: var(--market-chalk);
  --md-theme-heading-color: #ffe0a3;
  --md-theme-heading-1-color: #ffe0a3;
  --md-theme-heading-2-color: #ffe0a3;
  --md-theme-heading-3-color: #ffe8bb;
  --md-theme-heading-4-color: #ffe8bb;
  --md-theme-heading-5-color: var(--market-chalk);
  --md-theme-heading-6-color: rgba(253, 246, 227, 0.78);
  --md-theme-heading-1-border: 1px solid rgba(253, 246, 227, 0.14);
  --md-theme-heading-2-border: 1px solid rgba(253, 246, 227, 0.12);
  --md-theme-link-color: #ffc27a;
  --md-theme-link-hover-color: #ffe0a3;
  --md-theme-border-color: rgba(253, 246, 227, 0.18);
  --md-theme-border-color-inset: rgba(253, 246, 227, 0.23);
  --md-theme-code-inline-color: #ffe0a3;
  --md-theme-code-inline-bg-color: rgba(15, 38, 31, 0.5);
  --md-theme-code-block-color: #f4ead4;
  --md-theme-code-block-bg-color: #111c31;
  --md-theme-code-before-bg-color: #111c31;
  --md-theme-quote-color: rgba(253, 246, 227, 0.88);
  --md-theme-quote-border: 3px solid var(--market-ticket-pink);
  --md-theme-quote-bg-color: rgba(253, 246, 227, 0.065);
  --md-theme-table-stripe-color: rgba(253, 246, 227, 0.055);
  --md-theme-table-tr-bg-color: transparent;
  --md-theme-table-td-border-color: rgba(253, 246, 227, 0.18);
  min-width: 0;
  overflow: visible;
  color: var(--market-chalk);
  font-family: var(--market-font-body);
  font-size: 14px;
  line-height: 1.82;
  background: transparent;
}

.markdown-answer :deep(.github-theme) {
  color: var(--market-chalk);
  background: transparent;
}

.markdown-answer :deep(h1),
.markdown-answer :deep(h2),
.markdown-answer :deep(h3),
.markdown-answer :deep(h4),
.markdown-answer :deep(h5),
.markdown-answer :deep(h6) {
  color: #ffe0a3;
  font-family: var(--market-font-display);
  letter-spacing: 0.02em;
}

.markdown-answer :deep(h1:first-child),
.markdown-answer :deep(h2:first-child),
.markdown-answer :deep(h3:first-child),
.markdown-answer :deep(p:first-child) {
  margin-top: 0;
}

.markdown-answer :deep(p) {
  margin: 0.7em 0;
  color: var(--market-chalk);
}

.markdown-answer :deep(strong) {
  color: #ffe4ad;
}

.markdown-answer :deep(a) {
  color: #ffc27a;
  text-decoration: underline;
  text-decoration-color: rgba(255, 194, 122, 0.45);
  text-underline-offset: 3px;
}

.markdown-answer :deep(blockquote) {
  margin: 1em 0;
  padding: 8px 13px;
  border-left: 3px solid var(--market-ticket-pink);
  border-radius: 0 5px 5px 0;
  color: rgba(253, 246, 227, 0.88);
  background: rgba(253, 246, 227, 0.065);
}

.markdown-answer :deep(ul),
.markdown-answer :deep(ol) {
  padding-left: 1.75em;
}

.markdown-answer :deep(p),
.markdown-answer :deep(li),
.markdown-answer :deep(blockquote),
.markdown-answer :deep(td),
.markdown-answer :deep(th) {
  overflow-wrap: anywhere;
  word-break: break-word;
}
.markdown-answer :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
}

.markdown-answer :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-answer :deep(th),
.markdown-answer :deep(td) {
  min-width: 110px;
  padding: 7px 9px;
  color: var(--market-chalk);
}
.markdown-answer :deep(img),
.markdown-answer :deep(video),
.markdown-answer :deep(canvas) {
  max-width: 100%;
  height: auto;
}

.recommendation-block {
  margin-top: 12px;
  padding: 15px;
  border: 1px dashed rgba(217, 108, 44, 0.38);
  border-radius: 8px;
  background: var(--market-paper-deep);
}

.source-block {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-surface);
}

.related-post-block {
  margin-top: 12px;
  padding: 14px;
  border: 1px dashed rgba(47, 125, 92, 0.38);
  border-radius: 8px;
  background: var(--market-paper-deep);
}

.related-post-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.related-post-card {
  position: relative;
  min-width: 0;
  padding: 13px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-surface);
  cursor: pointer;
}

.related-post-card strong,
.related-post-card p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
}

.related-post-card strong {
  padding-right: 64px;
  -webkit-line-clamp: 2;
}

.related-post-card p {
  margin: 7px 0;
  color: var(--market-muted);
  font-size: 12px;
  line-height: 1.55;
  -webkit-line-clamp: 3;
}

.cited-post-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 6px;
  border-radius: 999px;
  color: var(--market-green);
  font-size: 10px;
  font-weight: 900;
  background: var(--market-note-green-bg);
}

.related-post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  color: var(--market-orange);
  font-size: 10px;
}

.source-link {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--market-line);
  border-radius: 7px;
  color: var(--market-ink);
  text-align: left;
  background: var(--market-paper);
  cursor: pointer;
}

.source-link > span {
  padding: 3px 6px;
  border-radius: 999px;
  color: var(--market-green);
  font-size: 10px;
  font-weight: 900;
  background: var(--market-note-green-bg);
}

.source-link div {
  min-width: 0;
}

.source-link strong,
.source-link p {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-link p {
  margin: 3px 0 0;
  color: var(--market-muted);
  font-size: 11px;
}

.source-link b {
  color: var(--market-orange);
  font-size: 11px;
  white-space: nowrap;
}
.source-detail-heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
  padding-right: 28px;
}
.source-detail-heading h2 {
  margin: 0;
  color: var(--market-ink);
  font-size: 20px;
  line-height: 1.35;
}
.source-detail-heading p {
  margin: 5px 0 0;
  overflow-wrap: anywhere;
  color: var(--market-muted);
  font-family: var(--market-font-mono);
  font-size: 12px;
}
.source-detail-type {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: var(--market-green);
  background: var(--market-note-green-bg);
  font-size: 11px;
  font-weight: 900;
}
.source-detail-body {
  max-height: min(58dvh, 560px);
  overflow-y: auto;
  padding: 18px;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background: var(--market-paper);
  overscroll-behavior: contain;
}
.source-detail-label {
  margin-bottom: 10px;
  color: var(--market-orange);
  font-size: 12px;
  font-weight: 900;
}
.source-citation-list {
  display: grid;
  gap: 16px;
}
.source-citation {
  min-width: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--market-line);
}
.source-citation:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.source-citation h3 {
  margin: 0 0 8px;
  color: var(--market-ink);
  font-size: 15px;
}
.source-citation small {
  display: block;
  margin-top: 8px;
  overflow-wrap: anywhere;
  color: var(--market-muted);
  font-family: var(--market-font-mono);
  font-size: 10px;
}
.source-detail-body p {
  margin: 0;
  color: var(--market-ink);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.recommendation-heading {
  margin-bottom: 10px;
  color: var(--market-ink);
}
.recommendation-heading span {
  color: var(--market-orange);
  font-size: 12px;
  font-weight: 800;
}
.recommendation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.commodity-card {
  display: grid;
  grid-template-columns: 86px 1fr;
  min-width: 0;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  text-align: left;
  background: var(--market-surface);
  cursor: pointer;
}
.commodity-card img,
.commodity-placeholder {
  width: 86px;
  height: 100%;
  min-height: 120px;
  object-fit: cover;
}
.commodity-placeholder {
  display: grid;
  place-items: center;
  color: var(--market-muted);
  font-size: 12px;
  background: var(--market-note-yellow-bg);
}
.commodity-copy {
  display: grid;
  align-content: center;
  gap: 5px;
  min-width: 0;
  padding: 10px;
}
.commodity-copy strong,
.commodity-copy p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commodity-copy p,
.commodity-copy small {
  margin: 0;
  color: var(--market-muted);
  font-size: 11px;
}
.commodity-copy div {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.commodity-copy b {
  color: var(--market-orange);
}
.commodity-copy em {
  color: var(--market-muted);
  font-size: 11px;
  font-style: normal;
}
.match-score {
  width: fit-content;
  padding: 2px 6px;
  border-radius: 999px;
  color: var(--market-green);
  font-size: 10px;
  font-weight: 900;
  background: var(--market-note-green-bg);
}

.composer-dock {
  position: relative;
  z-index: 2;
  min-width: 0;
  padding: 11px clamp(20px, 4vw, 64px) max(16px, env(safe-area-inset-bottom));
  border-top: 1px solid var(--market-line);
  background: radial-gradient(
      ellipse at 50% 0,
      rgba(244, 201, 93, 0.09),
      transparent 52%
    ),
    var(--market-topbar-bg);
  backdrop-filter: blur(12px);
}
.composer-shell {
  position: relative;
  max-width: 960px;
  margin: auto;
  padding: 9px 10px 8px 15px;
  border: 1px solid var(--market-line);
  border-radius: 7px;
  background: repeating-linear-gradient(
      0deg,
      transparent 0 27px,
      rgba(94, 160, 181, 0.09) 27px 28px
    ),
    var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  transition: 0.2s ease;
  transform: rotate(-0.16deg);

  &::before {
    position: absolute;
    top: -5px;
    left: 24px;
    width: 74px;
    height: 12px;
    background: rgba(217, 173, 101, 0.42);
    content: "";
    transform: rotate(-1deg);
  }
}
.stamp-send {
  min-width: 88px;
  min-height: 34px;
  border: 2px solid var(--market-stamp-red) !important;
  border-radius: 5px !important;
  color: var(--market-stamp-red) !important;
  font-family: var(--market-font-display);
  font-weight: 900;
  letter-spacing: 1px;
  background: transparent !important;
  transform: rotate(-2.5deg);
}
.stamp-send:hover:not(.is-disabled) {
  color: var(--market-chalk) !important;
  background: var(--market-stamp-red) !important;
}
.stamp-send.is-disabled {
  border-color: var(--market-line) !important;
  color: var(--market-muted) !important;
  opacity: 0.58;
  transform: none;
}
.composer-shell.focused {
  border-color: rgba(47, 125, 92, 0.55);
  box-shadow: var(--market-focus);
  transform: rotate(0);
}
.composer-shell :deep(.el-textarea__inner) {
  max-height: min(118px, 30dvh) !important;
  overflow-y: auto !important;
  padding: 7px 0 5px;
  color: var(--market-ink);
  line-height: 1.7;
  background: transparent;
  box-shadow: none;
}
.composer-actions {
  gap: 15px;
  margin-top: 2px;
}
.composer-actions > span {
  color: var(--market-muted);
  font-size: 11px;
}
.active-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 960px;
  margin: 0 auto 7px;
  color: var(--market-muted);
  font-size: 11px;
}
.active-context button {
  border: 0;
  color: var(--market-orange);
  background: transparent;
  cursor: pointer;
}

.context-heading h2 {
  margin: 6px 0 7px;
  color: var(--market-ink);
  font-size: 26px;
}
.context-heading p {
  margin: 0;
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.6;
}
.context-form :deep(.el-select) {
  width: 100%;
}
.budget-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.budget-row :deep(.el-input-number) {
  width: 100%;
}
.context-tip {
  padding: 15px;
  border-left: 4px solid var(--market-orange);
  border-radius: 8px;
  color: var(--market-ink);
  background: var(--market-note-yellow-bg);
}
.context-tip p {
  margin: 6px 0 0;
  color: var(--market-muted);
  font-size: 13px;
  line-height: 1.65;
}
.context-footer {
  justify-content: flex-end;
}
.mobile-scrim {
  display: none;
}

:global(.typing-speed-popover.el-popper) {
  padding: 10px;
  border-color: var(--market-line);
  border-radius: 9px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow);
}

:global(.typing-speed-menu) {
  display: grid;
  gap: 4px;
}

:global(.typing-speed-menu > span) {
  padding: 3px 7px 7px;
  color: var(--market-muted);
  font-size: 11px;
  font-weight: 800;
}

:global(.typing-speed-menu button) {
  display: grid;
  grid-template-columns: 64px 1fr;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 9px;
  border: 0;
  border-radius: 7px;
  color: var(--market-ink);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

:global(.typing-speed-menu button:hover) {
  background: var(--market-soft-bg);
}

:global(.typing-speed-menu button.active) {
  color: var(--market-green);
  background: var(--market-note-green-bg);
}

:global(.typing-speed-menu strong) {
  font-size: 12px;
}

:global(.typing-speed-menu small) {
  color: var(--market-muted);
  font-size: 11px;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

@keyframes typing-blink {
  0%,
  48% {
    opacity: 1;
  }
  49%,
  100% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition: none !important;
    animation: none !important;
  }
}

@media (max-height: 720px) {
  .chat-toolbar {
    min-height: 64px;
    padding: 8px 18px;
  }

  .message-stage {
    padding-top: 18px;
    padding-bottom: 18px;
  }

  .welcome-card {
    margin-top: 0;
    padding: 24px 28px;
  }

  .welcome-card h2 {
    margin-top: 8px;
    font-size: clamp(26px, 3vw, 36px);
  }

  .welcome-card > p {
    line-height: 1.55;
  }

  .starter-grid {
    margin-top: 16px;
  }

  .starter-card {
    padding: 14px 16px;
  }

  .composer-dock {
    padding-top: 8px;
    padding-bottom: max(10px, env(safe-area-inset-bottom));
  }
}

@media (max-width: 900px) {
  .agent-desk {
    grid-template-columns: 1fr;
    height: 100%;
    min-height: 0;
  }
  .conversation-rail {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: min(84vw, 330px);
    transform: translateX(-105%);
    transition: transform 0.25s ease;
    box-shadow: var(--market-shadow);
  }
  .conversation-rail.open {
    transform: translateX(0);
  }
  .rail-close,
  .history-trigger,
  .mobile-scrim {
    display: block;
  }
  .mobile-scrim {
    position: fixed;
    z-index: 3;
    inset: 0;
    border: 0;
    background: rgba(22, 29, 34, 0.46);
  }
  .chat-toolbar {
    justify-content: flex-start;
  }
  .toolbar-actions {
    margin-left: auto;
  }
}

@media (max-width: 660px) {
  .agent-desk {
    height: 100%;
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }
  .chat-toolbar {
    min-height: 64px;
    padding: 9px 12px;
  }
  .chat-title small {
    display: none;
  }
  .context-trigger {
    padding: 8px;
    font-size: 0;
  }
  .context-trigger span,
  .context-trigger b {
    font-size: 12px;
  }
  .message-stage {
    padding: 22px 12px;
    scrollbar-gutter: auto;
  }
  .welcome-card {
    margin-top: 10px;
    padding: 26px 20px;
  }
  .welcome-stamp {
    position: static;
    width: fit-content;
    margin-bottom: 15px;
  }
  .starter-grid,
  .recommendation-grid,
  .related-post-grid {
    grid-template-columns: 1fr;
  }
  .chat-message {
    gap: 7px;
  }
  .agent-seal {
    width: 28px;
    height: 28px;
    margin-top: 18px;
    font-size: 10px;
  }
  .message-column {
    max-width: 88%;
  }
  .chat-message.assistant .message-column {
    width: calc(100% - 35px);
  }
  .chat-message.assistant .message-bubble:not(.failed) {
    padding: 17px 16px;
  }
  .markdown-answer :deep(.md-editor-preview) {
    font-size: 13.5px;
    line-height: 1.75;
  }
  .composer-dock {
    padding: 9px 10px max(12px, env(safe-area-inset-bottom));
  }
  .composer-actions > span {
    display: none;
  }
  .composer-actions {
    justify-content: flex-end;
  }
  .composer-shell {
    transform: none;
  }
}

@media (max-width: 420px) {
  .chat-title strong {
    max-width: 148px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .message-stage {
    padding-right: 9px;
    padding-left: 9px;
  }
  .chat-message {
    gap: 6px;
    margin-bottom: 22px;
  }
  .agent-seal {
    width: 25px;
    height: 25px;
    border-width: 1px;
    font-size: 9px;
  }
  .chat-message.assistant .message-column {
    width: calc(100% - 31px);
    max-width: calc(100% - 31px);
  }
  .chat-message.user .message-column {
    max-width: 88%;
  }
  .message-bubble {
    padding: 13px 14px;
  }
  .chat-message.assistant .message-bubble:not(.failed) {
    padding: 16px 14px;
  }
  .markdown-answer :deep(h1) {
    font-size: 1.55em;
  }
  .markdown-answer :deep(h2) {
    font-size: 1.3em;
  }
  .stamp-send {
    min-width: 76px;
    padding-right: 9px;
    padding-left: 9px;
    font-size: 12px;
  }
}

@media (max-height: 560px) {
  .chat-toolbar {
    min-height: 56px;
    padding-top: 6px;
    padding-bottom: 6px;
  }
  .chat-title small {
    display: none;
  }
  .message-stage {
    padding-top: 14px;
    padding-bottom: 14px;
  }
  .composer-dock {
    padding-top: 6px;
    padding-bottom: max(7px, env(safe-area-inset-bottom));
  }
}
</style>

<style scoped lang="scss">
/* 海盐蓝 · 校园市集智能导购台 */
.agent-desk {
  border-radius: 14px 22px 14px 22px;
  background: var(--market-surface);
}
.conversation-rail {
  background: linear-gradient(
      180deg,
      var(--market-primary-soft),
      transparent 190px
    ),
    var(--market-surface-soft);
}
.conversation-rail::before {
  display: block;
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 6px;
  background: linear-gradient(
    90deg,
    var(--market-primary) 0 64%,
    var(--market-orange) 64% 80%,
    var(--market-yellow) 80%
  );
  content: "";
}
.rail-heading h1,
.chat-title strong {
  font-family: var(--market-font-display);
}
.conversation-ticket {
  border-color: transparent;
  border-radius: 7px 13px 7px 13px;
  background: transparent;
}
.conversation-ticket:hover,
.conversation-ticket.active {
  border-color: rgba(37, 99, 235, 0.2);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  transform: translateX(3px);
}
.new-chat-button {
  border-radius: 7px 13px 7px 13px;
  color: #fff;
  background: var(--market-primary);
  box-shadow: 0 9px 18px rgba(37, 99, 235, 0.18);
}
.rail-note {
  border-color: var(--market-line);
  color: var(--market-muted);
  background: var(--market-yellow-soft);
}
.chat-workspace {
  background: linear-gradient(
      90deg,
      transparent 0 42px,
      rgba(37, 99, 235, 0.05) 42px 44px,
      transparent 44px
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0 31px,
      rgba(37, 99, 235, 0.045) 31px 32px
    ),
    var(--market-canvas);
}
.chat-toolbar {
  border-bottom-color: var(--market-line);
  background: color-mix(in srgb, var(--market-surface) 91%, transparent);
  backdrop-filter: blur(18px) saturate(1.15);
}
.desk-mark {
  border-color: rgba(249, 115, 22, 0.32);
  color: var(--market-orange);
  background: var(--market-orange-soft);
}
.status-dot {
  background: var(--market-success);
  box-shadow: 0 0 0 4px var(--market-success-soft);
}
.welcome-card {
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 12px 22px 12px 22px;
  color: var(--market-ink);
  background: radial-gradient(
      circle at 92% 14%,
      rgba(246, 196, 83, 0.16),
      transparent 22%
    ),
    linear-gradient(125deg, var(--market-surface), var(--market-primary-soft));
  box-shadow: var(--market-shadow);
}
.welcome-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 7px;
  background: linear-gradient(
    90deg,
    var(--market-primary) 0 65%,
    var(--market-orange) 65% 80%,
    var(--market-yellow) 80%
  );
  content: "";
}
.welcome-stamp {
  border-color: var(--market-orange);
  color: var(--market-orange);
  background: var(--market-orange-soft);
}
.starter-card {
  border-color: var(--market-line);
  border-radius: 7px 14px 7px 14px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}
.starter-card:hover {
  border-color: rgba(37, 99, 235, 0.34);
  color: var(--market-primary);
  box-shadow: var(--market-shadow-lift);
  transform: translateY(-3px) rotate(0);
}
.agent-seal {
  border-color: rgba(37, 99, 235, 0.24);
  color: var(--market-primary);
  background: var(--market-primary-soft);
  box-shadow: none;
}
.chat-message {
  animation: message-arrive 0.24s var(--market-ease-standard) both;
}
.chat-message.assistant .message-bubble:not(.failed) {
  overflow: visible;
  border-color: var(--market-line);
  border-radius: 7px 16px 16px 16px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}
.chat-message.assistant .message-bubble:not(.failed)::before {
  top: -8px;
  left: 21px;
  width: 8px;
  height: 23px;
  border-color: var(--market-primary);
  opacity: 0.42;
}
.chat-message.user .message-bubble {
  border-color: rgba(37, 99, 235, 0.22);
  color: var(--market-ink);
  background: linear-gradient(
      135deg,
      transparent calc(100% - 15px),
      rgba(37, 99, 235, 0.08) 0
    ),
    var(--market-primary-soft);
  box-shadow: 0 8px 20px rgba(30, 64, 109, 0.08);
}
.thinking-line {
  border-left-color: var(--market-primary);
  color: var(--market-muted);
  background: var(--market-primary-soft);
}
.markdown-answer,
.markdown-answer :deep(.md-editor),
.markdown-answer :deep(.md-editor-preview),
.markdown-answer :deep(.github-theme) {
  color: var(--market-ink);
}
.markdown-answer :deep(.md-editor) {
  --md-color: var(--market-ink);
  --md-hover-color: var(--market-primary);
  --md-bk-color: transparent;
  --md-bk-color-outstand: var(--market-surface-soft);
  --md-bk-hover-color: var(--market-primary-soft);
  --md-border-color: var(--market-line);
  --md-border-hover-color: var(--market-line-strong);
  --md-border-active-color: var(--market-primary);
}
.markdown-answer :deep(.md-editor-preview) {
  --md-theme-color: var(--market-ink);
  --md-theme-heading-color: var(--market-ink);
  --md-theme-heading-1-color: var(--market-ink);
  --md-theme-heading-2-color: var(--market-ink);
  --md-theme-heading-3-color: var(--market-primary);
  --md-theme-heading-4-color: var(--market-primary);
  --md-theme-heading-5-color: var(--market-ink);
  --md-theme-heading-6-color: var(--market-muted);
  --md-theme-heading-1-border: 1px solid var(--market-line);
  --md-theme-heading-2-border: 1px solid var(--market-line);
  --md-theme-link-color: var(--market-primary);
  --md-theme-link-hover-color: var(--market-primary-hover);
  --md-theme-border-color: var(--market-line);
  --md-theme-border-color-inset: var(--market-line-strong);
  --md-theme-code-inline-color: var(--market-primary-hover);
  --md-theme-code-inline-bg-color: var(--market-primary-soft);
  --md-theme-code-block-color: #dce8f8;
  --md-theme-code-block-bg-color: #111c31;
  --md-theme-code-before-bg-color: #111c31;
  --md-theme-quote-color: var(--market-muted);
  --md-theme-quote-border: 3px solid var(--market-primary);
  --md-theme-quote-bg-color: var(--market-primary-soft);
  --md-theme-table-stripe-color: var(--market-surface-soft);
  --md-theme-table-tr-bg-color: transparent;
  --md-theme-table-td-border-color: var(--market-line);
  font-size: 15.5px;
  line-height: 1.82;
}
.markdown-answer :deep(h1),
.markdown-answer :deep(h2),
.markdown-answer :deep(h3),
.markdown-answer :deep(h4),
.markdown-answer :deep(h5),
.markdown-answer :deep(h6),
.markdown-answer :deep(p),
.markdown-answer :deep(strong) {
  color: var(--market-ink);
}
.markdown-answer :deep(a) {
  color: var(--market-primary);
  text-decoration-color: rgba(37, 99, 235, 0.36);
}
.markdown-answer :deep(blockquote) {
  border-left-color: var(--market-primary);
  color: var(--market-muted);
  background: var(--market-primary-soft);
}
.typing-caret {
  background: var(--market-primary);
}
.recommendation-block,
.source-block,
.related-post-block {
  border: 1px solid var(--market-line);
  border-radius: 9px 16px 9px 16px;
  background: var(--market-surface-soft);
}
.recommendation-block {
  border-top: 4px solid var(--market-orange);
}
.source-block {
  border-top: 4px solid var(--market-primary);
}
.related-post-block {
  border-top: 4px solid var(--market-yellow);
}
.commodity-card,
.source-link,
.related-post-card {
  border-color: var(--market-line);
  border-radius: 7px 13px 7px 13px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: 0 5px 14px rgba(30, 64, 109, 0.06);
  transition: transform var(--market-dur-fast),
    border-color var(--market-dur-fast), box-shadow var(--market-dur-fast);
}
.commodity-card:hover,
.source-link:hover,
.related-post-card:hover {
  border-color: rgba(37, 99, 235, 0.34);
  box-shadow: var(--market-shadow-soft);
  transform: translateY(-2px);
}
.match-score,
.cited-post-badge {
  color: var(--market-primary);
  background: var(--market-primary-soft);
}
.commodity-copy b {
  color: var(--market-orange);
}
.source-link b {
  color: var(--market-primary);
}
.composer-dock {
  border-top-color: var(--market-line);
  background: color-mix(in srgb, var(--market-surface) 94%, transparent);
  backdrop-filter: blur(18px);
}
.composer-shell {
  border-color: var(--market-line);
  border-radius: 12px 20px 12px 20px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}
.composer-shell.focused {
  border-color: var(--market-primary);
  box-shadow: var(--market-focus), var(--market-shadow-soft);
}
.stamp-send {
  border-radius: 8px 13px 8px 13px;
  color: #fff;
  background: var(--market-primary);
}
@keyframes message-arrive {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
html.dark .chat-workspace {
  background: linear-gradient(
      90deg,
      transparent 0 42px,
      rgba(96, 165, 250, 0.05) 42px 44px,
      transparent 44px
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0 31px,
      rgba(96, 165, 250, 0.035) 31px 32px
    ),
    var(--market-canvas);
}
html.dark .markdown-answer :deep(.md-editor-preview) {
  --md-theme-code-inline-color: #93c5fd;
  --md-theme-code-inline-bg-color: rgba(96, 165, 250, 0.12);
}
</style>

<style scoped lang="scss">
/* Markdown 表格最终对比度覆盖：覆盖 github 主题内部变量 */
.markdown-answer :deep(.github-theme) {
  --md-theme-color: var(--market-ink) !important;
  --md-theme-table-tr-bg-color: var(--market-surface) !important;
  --md-theme-table-stripe-color: var(--market-surface-soft) !important;
  --md-theme-table-td-border-color: var(--market-line) !important;
  --md-theme-border-color: var(--market-line) !important;
  color: var(--market-ink) !important;
  background: transparent !important;
}
.markdown-answer :deep(.github-theme table) {
  color: var(--market-ink) !important;
  background: var(--market-surface) !important;
}
.markdown-answer :deep(.github-theme table tr),
.markdown-answer :deep(.github-theme table tr:nth-child(2n)) {
  color: var(--market-ink) !important;
  background: var(--market-surface) !important;
}
.markdown-answer :deep(.github-theme table tr:nth-child(2n)) {
  background: var(--market-surface-soft) !important;
}
.markdown-answer :deep(.github-theme table th),
.markdown-answer :deep(.github-theme table td) {
  color: var(--market-ink) !important;
  border-color: var(--market-line) !important;
  background: inherit !important;
}
.markdown-answer :deep(.github-theme table th) {
  color: var(--market-primary-hover) !important;
  font-weight: 800;
  background: var(--market-primary-soft) !important;
}
html.dark .markdown-answer :deep(.github-theme) {
  --md-theme-color: var(--market-ink) !important;
  --md-theme-table-tr-bg-color: var(--market-surface) !important;
  --md-theme-table-stripe-color: var(--market-surface-soft) !important;
  --md-theme-table-td-border-color: var(--market-line) !important;
}
html.dark .markdown-answer :deep(.github-theme table th) {
  color: var(--market-ink) !important;
  background: var(--market-primary-soft) !important;
}
</style>
