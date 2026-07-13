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
          <span class="market-eyebrow">Guide Agent</span>
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
            <span
              class="ticket-delete"
              role="button"
              tabindex="0"
              aria-label="删除会话"
              @click.stop="confirmDeleteConversation(item)"
              @keydown.enter.stop="confirmDeleteConversation(item)"
            >
              删除
            </span>
          </span>
        </button>

        <div
          v-if="!conversationLoading && !conversations.length"
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
          :class="{ offline: backendUnavailable }"
        ></span>
        <span>{{
          backendUnavailable ? "Agent 后端等待接入" : "Agent 服务已连接"
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
            <strong>{{ activeConversation?.title || "校园交易咨询台" }}</strong>
            <small>先聊需求，再一起缩小选择范围</small>
          </div>
        </div>
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
      </header>

      <section ref="messageListRef" class="message-stage" aria-live="polite">
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

        <div v-else-if="!messages.length" class="welcome-card">
          <div class="welcome-stamp">AI 导购</div>
          <span class="market-eyebrow">Campus Trade Desk</span>
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
                  正在查看需求并整理建议
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
                <MdPreview
                  v-else
                  :model-value="message.content"
                  preview-theme="github"
                  code-theme="github"
                />
              </template>
            </div>

            <div
              v-if="message.structuredContent?.recommendations?.length"
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
              v-if="message.structuredContent?.sources?.length"
              class="source-block"
            >
              <div class="recommendation-heading">
                <strong>回答参考来源</strong>
                <span>{{ message.structuredContent.sources.length }} 条</span>
              </div>
              <button
                v-for="source in message.structuredContent.sources"
                :key="`${source.sourceType}-${source.sourceId}`"
                type="button"
                class="source-link"
                @click="router.push(source.targetPath)"
              >
                <span>{{ source.sourceType }}</span>
                <div>
                  <strong>{{ source.title }}</strong>
                  <p>{{ source.excerpt }}</p>
                </div>
                <b aria-hidden="true">↗</b>
              </button>
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
            :autosize="{ minRows: 2, maxRows: 6 }"
            maxlength="1000"
            resize="none"
            :disabled="sending"
            placeholder="例如：想买一台适合看论文的二手平板，预算还没想好"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!composer.trim() || sending"
              @click="sendMessage"
            >
              发送
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
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref
} from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { MdPreview } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import {
  AiChatResponse,
  AiConversation,
  AiMessage,
  AiShoppingContext,
  createAiConversation,
  deleteAiConversation,
  listAiConversationMessages,
  listAiConversations,
  sendAiConversationMessage
} from "@/api/aiController";
import { animateIn } from "@/utils/motion";

type Starter = {
  kicker: string;
  title: string;
  desc: string;
  prompt: string;
};

const router = useRouter();
const pageRef = ref<HTMLElement | null>(null);
const messageListRef = ref<HTMLElement | null>(null);
const composerRef = ref();
const composer = ref("");
const composerFocused = ref(false);
const sending = ref(false);
const conversationLoading = ref(false);
const messageLoading = ref(false);
const backendUnavailable = ref(false);
const historyDrawerOpen = ref(false);
const contextDrawerOpen = ref(false);
const viewportWidth = ref(window.innerWidth);
const conversations = ref<AiConversation[]>([]);
const messages = ref<AiMessage[]>([]);
const activeConversationId = ref<string | null>(null);
const conversationPage = ref(1);
const conversationTotal = ref(0);
const messagePage = ref(1);
const messageTotal = ref(0);

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
const hasOlderMessages = computed(
  () => messages.value.length < messageTotal.value
);

const handleResize = () => {
  viewportWidth.value = window.innerWidth;
  if (window.innerWidth > 900) historyDrawerOpen.value = false;
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

const scrollToBottom = async () => {
  await nextTick();
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
};

const loadConversations = async (append = false) => {
  conversationLoading.value = true;
  try {
    const res = await listAiConversations(conversationPage.value, 10);
    if (res.code !== 200 || !res.data)
      throw new Error(res.message || "会话加载失败");
    conversations.value = append
      ? [...conversations.value, ...res.data.records]
      : res.data.records;
    conversationTotal.value = res.data.total;
    backendUnavailable.value = false;
  } catch (_error) {
    backendUnavailable.value = true;
    if (!append) conversations.value = [];
  } finally {
    conversationLoading.value = false;
  }
};

const loadMoreConversations = async () => {
  conversationPage.value += 1;
  await loadConversations(true);
};

const loadMessages = async (conversationId: string, older = false) => {
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
    backendUnavailable.value = false;
    if (!older) await scrollToBottom();
  } catch (_error) {
    backendUnavailable.value = true;
    if (!older) messages.value = [];
  } finally {
    messageLoading.value = false;
  }
};

const loadOlderMessages = async () => {
  messagePage.value += 1;
  await loadMessages(activeConversationId.value as string, true);
};

const startNewChat = () => {
  activeConversationId.value = null;
  messages.value = [];
  messagePage.value = 1;
  messageTotal.value = 0;
  clearShoppingContext();
  historyDrawerOpen.value = false;
  nextTick(() => composerRef.value?.focus());
};

const selectConversation = async (item: AiConversation) => {
  activeConversationId.value = item.id;
  messagePage.value = 1;
  normalizeContext(item.shoppingContext);
  historyDrawerOpen.value = false;
  await loadMessages(item.id);
};

const confirmDeleteConversation = async (item: AiConversation) => {
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
  role: AiMessage["role"],
  content: string,
  status: AiMessage["status"]
): AiMessage => ({
  id: `local-${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
  role,
  content,
  status,
  createTime: new Date().toISOString()
});

const applyServerResponse = async (
  data: AiChatResponse,
  localIds: string[]
) => {
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
  backendUnavailable.value = false;
  await scrollToBottom();
};

const submitContent = async (
  content: string,
  options: { appendUser: boolean; failedMessageId?: string } = {
    appendUser: true
  }
) => {
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
    backendUnavailable.value = true;
    messages.value = messages.value.filter(
      (item) => item.id !== pendingMessage.id
    );
    messages.value.push({
      ...makeLocalMessage(
        "ASSISTANT",
        "当前页面已经按新会话接口准备好，但后端 Agent 服务尚未实现。接口接入后可在这里继续本次咨询。",
        "FAILED"
      ),
      retryable: true
    });
    if (error?.message && !String(error.message).includes("404")) {
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

const openCommodity = (commodityId: string) => {
  router.push(`/user/commodity/detail/${commodityId}`);
};

onMounted(async () => {
  window.addEventListener("resize", handleResize);
  await loadConversations();
  animateIn(
    pageRef.value?.querySelectorAll(".conversation-rail, .chat-workspace") || []
  );
});

onBeforeUnmount(() => window.removeEventListener("resize", handleResize));
</script>

<style scoped lang="scss">
.agent-desk {
  display: grid;
  grid-template-columns: 278px minmax(0, 1fr);
  height: 100%;
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
  flex-direction: column;
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
  min-height: 120px;
  overflow-y: auto;
}

.conversation-ticket {
  width: 100%;
  margin-bottom: 8px;
  padding: 13px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--market-ink);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: 0.2s ease;
}
.conversation-ticket:hover {
  border-color: var(--market-line);
  background: var(--market-card-bg);
}
.conversation-ticket.active {
  border-color: rgba(47, 125, 92, 0.32);
  background: var(--market-menu-active-bg);
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
  margin-top: 9px;
  color: var(--market-muted);
  font-size: 11px;
}
.ticket-delete {
  opacity: 0;
  color: var(--market-red);
  transition: opacity 0.2s ease;
}
.conversation-ticket:hover .ticket-delete,
.conversation-ticket.active .ticket-delete {
  opacity: 1;
}

.rail-empty {
  padding: 56px 10px;
  color: var(--market-muted);
  text-align: center;
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
  min-width: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: var(--market-paper);
}
.chat-toolbar {
  min-height: 72px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--market-line);
  background: var(--market-topbar-bg);
  backdrop-filter: blur(12px);
}
.chat-title {
  justify-content: flex-start;
  gap: 11px;
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
  font-size: 12px;
}
.icon-button,
.context-trigger {
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
  min-height: 0;
  overflow-y: auto;
  padding: 34px clamp(20px, 5vw, 72px);
  scroll-behavior: smooth;
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
  max-width: 900px;
  gap: 12px;
  margin: 0 auto 24px;
}
.chat-message.user {
  justify-content: flex-end;
}
.message-column {
  min-width: 0;
  max-width: min(760px, 84%);
}
.chat-message.user .message-column {
  display: grid;
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
  padding: 14px 17px;
  border: 1px solid var(--market-line);
  border-radius: 7px 14px 14px 14px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}
.chat-message.user .message-bubble {
  border-color: rgba(47, 125, 92, 0.24);
  border-radius: 14px 7px 14px 14px;
  background: var(--market-note-green-bg);
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
  color: var(--market-muted);
}
.thinking-line span,
.stage-loading span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--market-green);
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
:deep(.md-editor-preview-wrapper) {
  padding: 0;
}
:deep(.md-editor-preview) {
  color: var(--market-ink);
  font-size: 14px;
  background: transparent;
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
  padding: 12px clamp(20px, 5vw, 72px) 18px;
  border-top: 1px solid var(--market-line);
  background: var(--market-topbar-bg);
}
.composer-shell {
  max-width: 900px;
  margin: auto;
  padding: 9px 10px 8px 14px;
  border: 1px solid var(--market-line);
  border-radius: 12px;
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  transition: 0.2s ease;
}
.composer-shell.focused {
  border-color: rgba(47, 125, 92, 0.55);
  box-shadow: var(--market-focus);
}
.composer-shell :deep(.el-textarea__inner) {
  padding: 5px 0 8px;
  color: var(--market-ink);
  background: transparent;
  box-shadow: none;
}
.composer-actions {
  gap: 15px;
}
.composer-actions > span {
  color: var(--market-muted);
  font-size: 11px;
}
.active-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 900px;
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

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition: none !important;
    animation: none !important;
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
  .context-trigger {
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
  .recommendation-grid {
    grid-template-columns: 1fr;
  }
  .chat-message {
    gap: 7px;
  }
  .agent-seal {
    width: 28px;
    height: 28px;
    font-size: 10px;
  }
  .message-column {
    max-width: 88%;
  }
  .composer-dock {
    padding: 9px 10px 12px;
  }
  .composer-actions > span {
    display: none;
  }
  .composer-actions {
    justify-content: flex-end;
  }
}
</style>
