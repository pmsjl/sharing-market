<template>
  <div
    class="agent-desk"
    :class="{
      'has-selection':
        selectionOpen && selectionMessage && viewportWidth >= 1100
    }"
    ref="pageRef"
  >
    <el-drawer
      v-model="historyDrawerOpen"
      title="咨询记录"
      direction="ltr"
      :size="Math.min(340, viewportWidth - 24)"
      append-to-body
      class="agent-history-drawer"
      ><div class="conversation-rail">
        <el-button class="new-chat-button" type="primary" @click="startNewChat">
          <span aria-hidden="true">＋</span>
          新建咨询
        </el-button>

        <div class="conversation-list" v-loading="conversationLoading">
          <button
            v-for="draft in pendingDrafts"
            :key="draft.key"
            type="button"
            class="conversation-ticket"
            :class="{ active: !activeConversationId && draftKey === draft.key }"
            @click="selectDraft(draft.key)"
          >
            <span class="ticket-main">
              <strong>{{
                draft.state.messages[0]?.content ||
                draft.state.composer ||
                "新咨询"
              }}</strong>
              <em>{{
                draft.state.submissionUnknown
                  ? "提交结果待确认，点击查看"
                  : draft.state.sending
                  ? "正在回复…"
                  : "发送失败，点击重试"
              }}</em>
            </span>
          </button>
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
              <em>{{
                isConversationSending(item.id)
                  ? "正在回复…"
                  : item.lastMessagePreview || "还没有消息"
              }}</em>
            </span>
            <span class="ticket-foot">
              <time>{{ formatConversationTime(item.lastMessageTime) }}</time>
              <span class="ticket-actions">
                <span
                  class="ticket-archive"
                  role="button"
                  :tabindex="isConversationSending(item.id) ? -1 : 0"
                  :aria-disabled="
                    isConversationSending(item.id) ? 'true' : 'false'
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
          <span
            class="status-dot"
            :class="{ offline: agentUnavailable }"
          ></span>
          <span>{{
            agentUnavailable ? "AI 服务暂不可用" : "智能导购可开始咨询"
          }}</span>
        </div>
      </div></el-drawer
    >

    <main class="chat-workspace">
      <header class="chat-toolbar">
        <button
          type="button"
          class="icon-button history-trigger"
          aria-label="打开会话列表"
          :aria-expanded="historyDrawerOpen"
          @click="historyDrawerOpen = true"
        >
          ☰
        </button>
        <button type="button" class="new-conversation" @click="startNewChat">
          ＋ <span>新咨询</span>
        </button>
        <div class="chat-title">
          <span class="desk-mark" aria-hidden="true">AI</span>
          <div>
            <strong>{{ activeConversation?.title || "你的选物搭子" }}</strong>
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
        <div
          v-if="
            activeChat.submissionUnknown ||
            (messageLoadFailed && messages.length)
          "
          class="history-load-state"
        >
          <p>
            {{
              activeChat.submissionUnknown
                ? "尚未确认消息是否提交成功，请重新加载会话确认后再发送。"
                : "无法继续获取回复，请重新加载会话。"
            }}
          </p>
          <el-button
            v-if="activeConversationId"
            size="small"
            @click="reloadMessages"
          >
            重新加载消息
          </el-button>
          <el-button v-else size="small" @click="reloadConversations">
            重新加载会话列表
          </el-button>
        </div>
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
          <span class="welcome-stamp" aria-hidden="true"
            >一起挑<br />不踩坑</span
          >
          <span class="market-eyebrow">聊聊需求，再遇见好物</span>
          <h2>有点心动，<br /><span>一起选明白。</span></h2>
          <p>想买什么、预算多少，或者哪里拿不准，都可以直接问。</p>
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
          :data-message-id="message.id"
          tabindex="-1"
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
                  {{ activeChat.pollingError || "正在翻看摊位清单并整理建议" }}
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

            <button
              v-if="
                message.status === 'SUCCESS' &&
                !isMessageTyping(message.id) &&
                message.structuredContent?.recommendations?.length
              "
              type="button"
              class="selection-trigger"
              :aria-expanded="
                selectionOpen && selectedRecommendationId === message.id
              "
              :aria-label="`查看本轮 ${message.structuredContent.recommendations.length} 件好物`"
              @click="openSelection(message.id, $event)"
            >
              <span class="selection-trigger-mark" aria-hidden="true">↗</span
              ><span
                ><strong
                  >这轮帮你找到了
                  {{ message.structuredContent.recommendations.length }}
                  件好物</strong
                ><small>查看商品、推荐理由和验货提醒</small></span
              ><span class="selection-trigger-action">展开清单 →</span>
            </button>

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
                <span>{{
                  source.sourceType === "GUIDE" ? "指南" : "参考"
                }}</span>
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
        <details v-if="aiQuota" class="quota-disclosure">
          <summary>
            今日可用 {{ aiQuota.remaining }} 次 <span>额度说明</span>
          </summary>
          <div>
            <p>
              个人：已用 {{ aiQuota.usedCount }} / {{ aiQuota.dailyLimit }} 次
            </p>
            <p>
              平台：剩余 {{ aiQuota.globalRemaining }} /
              {{ aiQuota.globalDailyLimit }} 次
            </p>
            <p>重置时间：{{ quotaResetLabel }}</p>
          </div>
        </details>
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
            :disabled="sending || quotaExhausted"
            placeholder="例如：想买一台适合看论文的二手平板，预算还没想好"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <span v-if="messageLoadFailed">
              历史消息加载失败，请重新加载后再继续咨询
            </span>
            <span v-else-if="aiQuota && aiQuota.globalRemaining <= 0">
              今日平台体验额度已用完，明天再来
            </span>
            <span v-else-if="aiQuota && aiQuota.remaining <= 0">
              你今天的 {{ aiQuota.dailyLimit }} 次咨询已用完，明天再来
            </span>
            <span v-else>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              class="stamp-send"
              type="primary"
              :loading="sending"
              :disabled="
                !composer.trim() ||
                sending ||
                messageLoading ||
                messageLoadFailed ||
                quotaExhausted
              "
              @click="sendMessage"
            >
              发送
            </el-button>
          </div>
        </div>
      </footer>
    </main>

    <aside
      v-if="selectionOpen && selectionMessage && viewportWidth >= 1100"
      class="desktop-selection"
      aria-label="本轮选物清单"
      @keydown.esc="closeSelection"
    >
      <AgentSelection
        :message="selectionMessage"
        :prompt="selectionPrompt"
        @close="closeSelection"
        @return-to-answer="returnToSelectionAnswer"
        @open-commodity="openCommodity"
      />
    </aside>
    <el-drawer
      :model-value="
        selectionOpen && Boolean(selectionMessage) && viewportWidth < 1100
      "
      :with-header="false"
      :size="Math.min(460, viewportWidth - 12)"
      append-to-body
      class="agent-selection-drawer"
      aria-label="本轮选物清单"
      @update:model-value="onSelectionDrawerChange"
      ><AgentSelection
        v-if="selectionMessage"
        :message="selectionMessage"
        :prompt="selectionPrompt"
        @close="closeSelection"
        @return-to-answer="returnToSelectionAnswer"
        @open-commodity="openCommodity"
    /></el-drawer>

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
        <el-form-item label="预算范围（校园币）">
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
        <strong>让推荐更懂你</strong>
        <p>
          这些条件会随咨询保留，帮助我找到更适合你的商品。你可以随时修改或清空。
        </p>
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
  AiQuotaVO,
  archiveAiConversation,
  createAiConversation,
  deleteAiConversation,
  listAiConversationMessages,
  listAiConversations,
  getMyAiQuota,
  sendAiConversationMessage
} from "@/api/aiController";
import AgentSelection from "@/components/AgentSelection/index.vue";
import { startAiMessagePolling } from "@/utils/aiMessagePolling";

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
const composerFocused = ref(false);
const conversationLoading = ref(false);
const conversationLoadFailed = ref(false);
const historyDrawerOpen = ref(false);
const contextDrawerOpen = ref(false);
const viewportWidth = ref(window.innerWidth);
const conversations = ref<AiConversationVO[]>([]);
const activeConversationId = ref<string | null>(null);
// A new conversation receives its server ID as soon as the message is accepted.
let draftSequence = 0;
const draftKey = ref(`draft-${draftSequence}`);
const chatStates = reactive<Record<string, ChatState>>({});
type ChatState = {
  messages: AiMessageVO[];
  composer: string;
  sending: boolean;
  loading: boolean;
  unavailable: boolean;
  loadFailed: boolean;
  page: number;
  total: number;
  revision: number;
  pendingMessageId?: string;
  submissionUnknown?: boolean;
  pollingError?: string;
  context?: AiShoppingContext;
};
const getChatState = (key: string): ChatState => {
  if (!chatStates[key]) {
    chatStates[key] = {
      messages: [],
      composer: "",
      sending: false,
      loading: false,
      unavailable: false,
      loadFailed: false,
      page: 1,
      total: 0,
      revision: 0
    };
  }
  return chatStates[key];
};
const activeChat = computed(() =>
  getChatState(activeConversationId.value || draftKey.value)
);
const chatField = <K extends keyof ChatState>(key: K) =>
  computed({
    get: () => activeChat.value[key],
    set: (value: ChatState[K]) => {
      activeChat.value[key] = value;
    }
  });
const composer = chatField("composer");
const messages = chatField("messages");
const selectedRecommendationId = ref<string | null>(null);
const selectionOpen = ref(false);
let selectionOrigin: HTMLElement | null = null;
const selectionMessage = computed(
  () =>
    messages.value.find(
      (message) =>
        message.id === selectedRecommendationId.value &&
        message.role === "ASSISTANT" &&
        message.status === "SUCCESS" &&
        message.structuredContent?.recommendations?.length
    ) || null
);
const selectionPrompt = computed(() => {
  const index = messages.value.findIndex(
    (message) => message.id === selectedRecommendationId.value
  );
  if (index < 0) return "";
  return (
    messages.value
      .slice(0, index)
      .reverse()
      .find((message) => message.role === "USER")?.content || ""
  );
});
const openSelection = (messageId: string, event?: Event) => {
  selectedRecommendationId.value = messageId;
  if (!selectionMessage.value) {
    selectionOpen.value = false;
    return;
  }
  selectionOrigin =
    event && event.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : null;
  selectionOpen.value = true;
  if (viewportWidth.value >= 1100)
    void nextTick(() =>
      pageRef.value
        ?.querySelector<HTMLElement>(".desktop-selection button")
        ?.focus()
    );
};
const closeSelection = () => {
  selectionOpen.value = false;
  void nextTick(() => {
    if (selectionOrigin?.isConnected) selectionOrigin.focus();
  });
};
const onSelectionDrawerChange = (open: boolean) => {
  if (!open) closeSelection();
};
const returnToSelectionAnswer = async () => {
  const id = selectedRecommendationId.value;
  selectionOpen.value = false;
  await nextTick();
  const answer = Array.from(
    messageListRef.value?.querySelectorAll<HTMLElement>("[data-message-id]") ||
      []
  ).find((el) => el.dataset.messageId === id);
  answer?.scrollIntoView({ block: "center", behavior: "auto" });
  answer?.focus({ preventScroll: true });
};
watch(
  () => activeConversationId.value || draftKey.value,
  () => {
    selectionOpen.value = false;
    selectedRecommendationId.value = null;
    selectionOrigin = null;
  },
  { flush: "sync" }
);
watch(
  selectionMessage,
  (message) => {
    if (!message) selectionOpen.value = false;
  },
  { flush: "sync" }
);
const sending = chatField("sending");
const messageLoading = chatField("loading");
const agentUnavailable = chatField("unavailable");
const messageLoadFailed = chatField("loadFailed");
const messagePage = chatField("page");
const messageTotal = chatField("total");
const isConversationSending = (id: string) => !!chatStates[id]?.sending;
const pendingDrafts = computed(() =>
  Object.entries(chatStates)
    .filter(
      ([key, state]) =>
        key.startsWith("draft-") &&
        (state.messages.length > 0 || state.submissionUnknown)
    )
    .map(([key, state]) => ({ key, state }))
);
let disposed = false;
const messagePollers = new Map<
  string,
  ReturnType<typeof startAiMessagePolling>
>();
const conversationPage = ref(1);
const conversationTotal = ref(0);
const archivingConversationId = ref<string | null>(null);
const typingSpeed = ref<TypingSpeed>(getInitialTypingSpeed());
const typingMessageId = ref<string | null>(null);
const typingBuffers = reactive<Record<string, string>>({});
const shouldFollowOutput = ref(true);
const sourceDialogOpen = ref(false);
const selectedSource = ref<AiRagSourceVO | null>(null);
const aiQuota = ref<AiQuotaVO | null>(null);

let typingAnimationFrame: number | null = null;
let messageResizeObserver: ResizeObserver | null = null;
let resizeFollowFrame: number | null = null;

const quotaExhausted = computed(
  () =>
    Boolean(aiQuota.value) &&
    (Number(aiQuota.value?.remaining || 0) <= 0 ||
      Number(aiQuota.value?.globalRemaining || 0) <= 0)
);

const quotaResetLabel = computed(() => {
  if (!aiQuota.value?.resetAt) return "次日 00:00";
  const resetAt = new Date(aiQuota.value.resetAt);
  if (Number.isNaN(resetAt.getTime())) return "次日 00:00";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(resetAt);
});

const loadAiQuota = async () => {
  try {
    const res = await getMyAiQuota();
    if (res.code === 200 && res.data) aiQuota.value = res.data;
  } catch (error) {
    // 额度展示失败不阻塞历史记录浏览，发送时仍由后端强制校验。
  }
};

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

const resumePendingMessage = (conversationId: string, state: ChatState) => {
  const pending = [...state.messages]
    .reverse()
    .find(
      (message) => message.role === "ASSISTANT" && message.status === "PENDING"
    );
  if (!pending || pending.id.startsWith("local-")) return;
  if (
    state.pendingMessageId === pending.id &&
    messagePollers.has(conversationId)
  )
    return;
  messagePollers.get(conversationId)?.stop();
  state.pendingMessageId = pending.id;
  state.sending = true;
  state.pollingError = undefined;
  const isCurrent = () =>
    !disposed &&
    chatStates[conversationId] === state &&
    state.pendingMessageId === pending.id;
  const unavailable = () => {
    if (!isCurrent()) return;
    messagePollers.delete(conversationId);
    state.pendingMessageId = undefined;
    state.sending = false;
    state.loadFailed = true;
    state.pollingError = "无法继续获取回复，请重新加载会话。";
    if (activeChat.value === state) ElMessage.warning(state.pollingError);
  };
  const poller = startAiMessagePolling({
    messageId: pending.id,
    fetchPage: async (page) => {
      const res = await listAiConversationMessages(
        conversationId,
        page,
        20,
        "sequenceNo",
        "desc",
        true
      );
      if (res.code !== 200 || !res.data) {
        throw Object.assign(new Error(res.message || "消息查询失败"), {
          businessCode: res.code
        });
      }
      return res.data;
    },
    onSnapshot: (records, total) => {
      if (!isCurrent()) return;
      const merged = new Map(
        state.messages.map((message) => [message.id, message])
      );
      records.forEach((message) => merged.set(message.id, message));
      state.messages = [...merged.values()].sort(
        (a, b) => (a.sequenceNo || 0) - (b.sequenceNo || 0)
      );
      state.total = total;
      state.pollingError = undefined;
    },
    onResult: (message) => {
      if (!isCurrent()) return;
      messagePollers.delete(conversationId);
      state.pendingMessageId = undefined;
      state.sending = false;
      state.loadFailed = false;
      if (activeChat.value === state) {
        if (message.status === "SUCCESS") startTypingMessage(message);
        void scrollToBottom();
      }
      resumePendingMessage(conversationId, state);
      void loadConversations();
      void loadAiQuota();
    },
    onMissing: unavailable,
    onError: (error) => {
      if (!isCurrent()) return false;
      const failure = error as {
        response?: { status?: number };
        businessCode?: number;
      };
      if (
        [401, 403, 404].includes(Number(failure.response?.status)) ||
        [40100, 40101, 40400].includes(Number(failure.businessCode))
      ) {
        unavailable();
        return false;
      }
      state.pollingError = "连接暂时中断，正在重新获取回复。";
      return true;
    }
  });
  messagePollers.set(conversationId, poller);
};

const loadMessages = async (conversationId: string, older = false) => {
  const state = getChatState(conversationId);
  if (state.sending && !state.pendingMessageId && !state.submissionUnknown)
    return true;
  if (messagePollers.has(conversationId)) return true;
  const revision = ++state.revision;
  const previousScrollHeight = older
    ? messageListRef.value?.scrollHeight || 0
    : 0;
  state.loading = true;
  try {
    const res = await listAiConversationMessages(
      conversationId,
      state.page,
      20
    );
    if (disposed || state.revision !== revision) return false;
    if (res.code !== 200 || !res.data)
      throw new Error(res.message || "消息加载失败");
    const records = [...res.data.records].sort(
      (a, b) => (a.sequenceNo || 0) - (b.sequenceNo || 0)
    );
    state.messages = older
      ? [
          ...new Map(
            [...records, ...state.messages].map((message) => [
              message.id,
              message
            ])
          ).values()
        ].sort((a, b) => (a.sequenceNo || 0) - (b.sequenceNo || 0))
      : records;
    state.total = res.data.total;
    state.loadFailed = false;
    state.submissionUnknown = false;
    state.sending = false;
    resumePendingMessage(conversationId, state);
    if (activeChat.value !== state) return true;
    if (older) {
      await nextTick();
      if (activeChat.value === state && messageListRef.value) {
        messageListRef.value.scrollTop +=
          messageListRef.value.scrollHeight - previousScrollHeight;
      }
    } else {
      await scrollToBottom();
    }
    return true;
  } catch (_error) {
    if (disposed || state.revision !== revision) return false;
    if (!older) {
      if (!state.submissionUnknown) state.messages = [];
      state.loadFailed = true;
    }
    return false;
  } finally {
    if (state.revision === revision) state.loading = false;
  }
};

const loadOlderMessages = async () => {
  if (!activeConversationId.value || sending.value || messageLoading.value)
    return;
  const state = activeChat.value;
  const previousPage = state.page;
  state.page += 1;
  const loaded = await loadMessages(activeConversationId.value, true);
  if (!loaded) state.page = previousPage;
};

const reloadMessages = async () => {
  if (!activeConversationId.value) return;
  messagePage.value = 1;
  await loadMessages(activeConversationId.value);
};

const startNewChat = () => {
  finishActiveTyping();
  activeChat.value.context = getShoppingContext();
  draftKey.value = `draft-${++draftSequence}`;
  activeConversationId.value = null;
  messages.value = [];
  messagePage.value = 1;
  messageTotal.value = 0;
  messageLoadFailed.value = false;
  clearShoppingContext();
  historyDrawerOpen.value = false;
  nextTick(() => composerRef.value?.focus());
};

const selectDraft = (key: string) => {
  finishActiveTyping();
  activeChat.value.context = getShoppingContext();
  draftKey.value = key;
  activeConversationId.value = null;
  normalizeContext(activeChat.value.context);
  historyDrawerOpen.value = false;
  void scrollToBottom();
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
  activeChat.value.context = getShoppingContext();
  activeConversationId.value = item.id;
  normalizeContext(activeChat.value.context || item.shoppingContext);
  if (!sending.value) {
    messagePage.value = 1;
    messageLoadFailed.value = false;
  }
  historyDrawerOpen.value = false;
  if (activeChat.value.unavailable) return;
  await loadMessages(item.id);
};

const archiveConversation = async (item: AiConversationVO) => {
  if (archivingConversationId.value || isConversationSending(item.id)) {
    if (isConversationSending(item.id)) {
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
  if (isConversationSending(item.id)) {
    ElMessage.warning("当前会话正在回复中，回复完成后再删除");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `删除「${item.title || "未命名咨询"}」后将无法在列表中恢复。`,
      "删除咨询",
      { confirmButtonText: "删除", cancelButtonText: "取消", type: "warning" }
    );
    if (isConversationSending(item.id)) return;
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

const applyServerResponse = async (
  data: AiChatVO,
  localIds: string[],
  state: ChatState,
  key: string
) => {
  state.messages = state.messages.filter(
    (message) =>
      !localIds.includes(message.id) &&
      message.id !== data.userMessage.id &&
      message.id !== data.assistantMessage.id
  );
  state.messages.push(data.userMessage, data.assistantMessage);
  const wasActive = activeChat.value === state;
  chatStates[data.conversation.id] = state;
  if (key !== data.conversation.id) {
    if (wasActive) activeConversationId.value = data.conversation.id;
    delete chatStates[key];
  }
  const index = conversations.value.findIndex(
    (item) => item.id === data.conversation.id
  );
  if (index >= 0) conversations.value.splice(index, 1);
  conversations.value.unshift(data.conversation);
  conversationTotal.value = Math.max(
    conversationTotal.value,
    conversations.value.length
  );
  state.context = data.conversation.shoppingContext || state.context;
  state.unavailable = false;
  conversationLoadFailed.value = false;
  if (wasActive) {
    normalizeContext(state.context);
    if (data.assistantMessage.status === "SUCCESS")
      startTypingMessage(data.assistantMessage);
    await scrollToBottom();
  }
  state.submissionUnknown = false;
  if (data.assistantMessage.status === "PENDING")
    resumePendingMessage(data.conversation.id, state);
};

const submitContent = async (
  content: string,
  options: { appendUser: boolean; failedMessageId?: string } = {
    appendUser: true
  }
) => {
  const state = activeChat.value;
  if (
    state.sending ||
    state.loading ||
    state.loadFailed ||
    state.submissionUnknown ||
    quotaExhausted.value
  )
    return;
  const conversationId = activeConversationId.value;
  const key = conversationId || draftKey.value;
  const body = { content, shoppingContext: getShoppingContext() };
  state.context = body.shoppingContext;
  state.revision += 1;
  state.loading = false;
  finishActiveTyping();
  const localIds: string[] = [];
  let retriedUserId: string | undefined;
  if (options.failedMessageId) {
    const failedIndex = state.messages.findIndex(
      (item) => item.id === options.failedMessageId
    );
    retriedUserId = [...state.messages.slice(0, failedIndex)]
      .reverse()
      .find((item) => item.role === "USER")?.id;
    state.messages = state.messages.filter(
      (item) => item.id !== options.failedMessageId
    );
  }
  if (options.appendUser) {
    const userMessage = makeLocalMessage("USER", content, "SUCCESS");
    state.messages.push(userMessage);
    localIds.push(userMessage.id);
  }
  const pendingMessage = makeLocalMessage("ASSISTANT", "", "PENDING");
  state.messages.push(pendingMessage);
  localIds.push(pendingMessage.id);
  state.sending = true;

  try {
    await scrollToBottom();
    const res = conversationId
      ? await sendAiConversationMessage(conversationId, body)
      : await createAiConversation(body);
    if (disposed) return;
    if (res.code !== 200 || !res.data) {
      const businessError = new Error(
        res.message || "Agent 服务暂时不可用"
      ) as Error & { businessCode?: number };
      businessError.businessCode = res.code;
      throw businessError;
    }
    await applyServerResponse(
      res.data,
      retriedUserId ? [...localIds, retriedUserId] : localIds,
      state,
      key
    );
  } catch (error: any) {
    if (disposed) return;
    const protectedRejection = [40900, 40901, 42901, 42902].includes(
      Number(error?.businessCode)
    );
    if (protectedRejection) {
      state.messages = state.messages.filter(
        (item) => !localIds.includes(item.id)
      );
      state.composer = content;
      if (
        conversationId &&
        [40900, 40901].includes(Number(error?.businessCode))
      ) {
        state.sending = false;
        await loadMessages(conversationId);
      }
      if (activeChat.value === state)
        ElMessage.warning(error?.message || "当前暂时无法继续咨询");
      return;
    }
    // POST 失败可能发生在服务端提交之后，不能伪造 FAILED 或自动再发一轮。
    state.submissionUnknown = true;
    state.loadFailed = true;
    state.messages = state.messages.filter(
      (item) => !localIds.includes(item.id)
    );
    state.composer = content;
    if (conversationId) await loadMessages(conversationId);
    else await loadConversations();
    if (
      activeChat.value === state &&
      error?.message &&
      !error?.requestMessageShown &&
      !String(error.message).includes("404")
    ) {
      ElMessage.error(error.message);
    }
    if (activeChat.value === state) await scrollToBottom();
  } finally {
    state.sending = !!state.pendingMessageId;
    if (!disposed) await loadAiQuota();
  }
};

const sendMessage = async () => {
  const content = composer.value.trim();
  if (
    !content ||
    sending.value ||
    messageLoading.value ||
    messageLoadFailed.value ||
    quotaExhausted.value
  )
    return;
  composer.value = "";
  await submitContent(content);
};

const retryMessage = async (failedMessageId: string) => {
  if (sending.value || messageLoading.value) return;
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
  layoutSettingStore.focusMode = false;
  window.addEventListener("resize", handleResize);
  await loadAiQuota();
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
  disposed = true;
  messagePollers.forEach((poller) => poller.stop());
  messagePollers.clear();
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

<style scoped lang="scss" src="./guide.scss"></style>
