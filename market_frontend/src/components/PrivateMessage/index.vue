<template>
  <div class="private-mail" :class="{ 'has-contact': chat.activeId }">
    <aside class="mail-contacts" aria-label="私信会话">
      <div class="mail-list-heading">
        <strong>最近联系</strong
        ><span>{{ chat.conversations.length }} 位同学</span>
      </div>
      <el-select
        v-if="isAdmin"
        v-model="directoryId"
        class="mail-directory"
        filterable
        remote
        :remote-method="loadDirectory"
        :loading="directoryLoading"
        placeholder="查找同学，发起私聊"
        aria-label="查找同学"
        @visible-change="(visible) => visible && loadDirectory('')"
        @change="selectDirectoryContact"
      >
        <el-option
          v-for="user in directory"
          :key="user.id"
          :label="user.userName || '对方用户'"
          :value="String(user.id)"
        />
      </el-select>
      <p v-if="chat.error" class="mail-status" role="status">
        {{ chat.error }} <button @click="chat.refresh()">重试</button>
      </p>
      <p
        v-else-if="chat.loading && !chat.conversations.length"
        class="mail-status"
      >
        正在整理来信…
      </p>
      <el-empty
        v-else-if="!chat.conversations.length"
        description="暂无来信，去商品页联系同学吧"
        :image-size="80"
      />
      <div class="mail-contact-scroll">
        <button
          v-for="contact in chat.conversations"
          :key="contact.contactUserId"
          class="mail-contact"
          :class="{ selected: contact.contactUserId === chat.activeId }"
          :aria-pressed="contact.contactUserId === chat.activeId"
          @click="chat.activeId = contact.contactUserId"
        >
          <el-avatar :size="38" :src="contact.userAvatar">{{
            contact.userName.slice(0, 1)
          }}</el-avatar>
          <span class="mail-contact-copy">
            <span class="mail-contact-top"
              ><strong>{{ contact.userName }}</strong
              ><time>{{ shortTime(contact.lastMessageTime) }}</time></span
            >
            <span class="mail-contact-preview">{{
              contact.lastMessageContent || "打个招呼，开始聊天"
            }}</span>
            <span v-if="chat.isUnread(contact)" class="mail-unread"
              >新消息</span
            >
          </span>
        </button>
      </div>
      <p class="mail-list-note">有过来往的同学，会一直留在这里</p>
    </aside>
    <section class="mail-thread" aria-label="聊天内容">
      <div v-if="!chat.activeConversation" class="mail-welcome">
        <el-icon :size="38"><Message /></el-icon>
        <h3>每一件闲置，都能聊出新故事</h3>
        <p>选择一位同学，接着上次的话题聊</p>
      </div>
      <template v-else>
        <header class="mail-thread-header">
          <button
            class="mail-back"
            aria-label="返回会话列表"
            @click="chat.activeId = ''"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <el-avatar :size="36" :src="chat.activeConversation.userAvatar">{{
            chat.activeConversation.userName.slice(0, 1)
          }}</el-avatar>
          <div>
            <strong>{{ chat.activeConversation.userName }}</strong
            ><small>一对一私聊</small>
          </div>
        </header>
        <p v-if="loadError" class="mail-status" role="status">
          {{ loadError }} <button @click="loadLatest()">重试</button>
        </p>
        <div ref="scrollArea" class="mail-messages" @scroll="onScroll">
          <button
            v-if="messages.length < total"
            class="mail-history"
            :disabled="loading"
            @click="loadOlder"
          >
            {{ loading ? "加载中…" : "查看更早的消息" }}
          </button>
          <p v-if="loading && !messages.length" class="mail-empty">
            正在读取聊天记录…
          </p>
          <p v-else-if="!messages.length && !loadError" class="mail-empty">
            还没有消息，和同学打个招呼吧
          </p>
          <div
            v-for="message in messages"
            :key="message.id"
            class="mail-message"
            :class="{ sent: String(message.senderId) === chat.owner }"
          >
            <p>{{ message.content }}</p>
            <time>{{ shortTime(message.createTime, true) }}</time>
          </div>
        </div>
        <button
          v-if="
            !atBottom &&
            chat.activeConversation &&
            chat.isUnread(chat.activeConversation)
          "
          class="mail-new"
          @click="scrollBottom"
        >
          有新消息，点击查看 ↓
        </button>
        <form class="mail-compose" @submit.prevent="sendMessage">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            maxlength="1024"
            resize="none"
            placeholder="和同学说点什么…"
            aria-label="私信内容"
            @keydown="onKeydown"
          />
          <div class="mail-compose-actions">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              class="mail-emoji"
              aria-label="插入表情"
              :aria-expanded="showEmoji"
              @click="showEmoji = !showEmoji"
              >表情</el-button
            >
            <el-button
              native-type="submit"
              type="primary"
              :loading="sending"
              :disabled="!draft.trim() || sending"
              >发送 <el-icon><Position /></el-icon
            ></el-button>
          </div>
          <div v-if="showEmoji" class="mail-emoji-picker">
            <EmojiPicker :native="true" @select="insertEmoji" />
          </div>
        </form>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch
} from "vue";
import { ElMessage } from "element-plus";
import EmojiPicker from "vue3-emoji-picker";
import "vue3-emoji-picker/css";
import usePrivateMessageStore from "@/store/modules/privateMessage";
import {
  addPrivateMessageUsingPost,
  listMyPrivateMessageVoByPageUsingPost
} from "@/api/privateMessageController";
import { newerMessageId } from "@/utils/privateMessageState";
import { GET_ROLE } from "@/utils/token";
import { listUserVoByPageUsingPost } from "@/api/userController";

const chat = usePrivateMessageStore();
// Preserve administrators' existing ability to start a chat from the user directory.
const isAdmin = GET_ROLE() === "admin";
const directory = ref<API.UserVO[]>([]);
const directoryId = ref("");
const directoryLoading = ref(false);
let directoryRevision = 0;
async function loadDirectory(userName: string) {
  const version = ++directoryRevision;
  directoryLoading.value = true;
  try {
    const result = (await listUserVoByPageUsingPost(
      { userRole: "user", userName, current: 1, pageSize: 50 },
      { silent: true }
    )) as unknown as API.BaseResponsePageUserVO_;
    if (version === directoryRevision) {
      if (result.code !== 200) throw new Error("暂时无法查找同学");
      directory.value = result.data?.records || [];
    }
  } catch {
    if (version === directoryRevision)
      ElMessage.error("暂时无法查找同学，请重试");
  } finally {
    if (version === directoryRevision) directoryLoading.value = false;
  }
}
function selectDirectoryContact(id: string) {
  const user = directory.value.find((item) => String(item.id) === id);
  if (user) chat.openContact({ ...user, id });
  directoryId.value = "";
}
const messages = ref<API.PrivateMessageVO[]>([]);
const total = ref(0);
const loading = ref(false);
const sending = computed(() => !!chat.sendingContacts[chat.activeId]);
const loadError = ref("");
const atBottom = ref(true);
const showEmoji = ref(false);
const scrollArea = ref<HTMLElement>();
let revision = 0;
let timer: ReturnType<typeof setInterval>;
const draft = computed({
  get: () => chat.drafts[chat.activeId] || "",
  set: (value: string) => {
    chat.drafts[chat.activeId] = value;
  }
});
function shortTime(value?: string, full = false) {
  if (!value) return "";
  const today = new Date();
  const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(
    2,
    "0"
  )}-${String(today.getDate()).padStart(2, "0")}`;
  return value.startsWith(date)
    ? value.slice(11, 16)
    : value.slice(5, full ? 16 : 10);
}
function merge(rows: API.PrivateMessageVO[]) {
  messages.value = [
    ...new Map(
      [...messages.value, ...rows].map((m) => [String(m.id), m])
    ).values()
  ].sort((a, b) => (newerMessageId(String(a.id), String(b.id)) ? 1 : -1));
}
function markVisibleRead() {
  if (!atBottom.value || !chat.opened || document.visibilityState !== "visible")
    return;
  const last = [...messages.value]
    .reverse()
    .find((m) => String(m.recipientId) === chat.owner);
  if (last?.id) chat.markRead(chat.activeId, String(last.id));
}
function onScroll() {
  const area = scrollArea.value;
  atBottom.value =
    !!area && area.scrollHeight - area.scrollTop - area.clientHeight < 24;
  markVisibleRead();
}
async function scrollBottom() {
  await nextTick();
  const area = scrollArea.value;
  if (area) area.scrollTop = area.scrollHeight;
  atBottom.value = true;
  markVisibleRead();
}
async function fetchPage(contact: string, current: number) {
  const result = (await listMyPrivateMessageVoByPageUsingPost(
    {
      contactUserId: contact,
      current,
      pageSize: 50,
      sortField: "id",
      sortOrder: "desc"
    },
    { silent: true }
  )) as unknown as API.BaseResponsePagePrivateMessageVO_;
  if (result.code !== 200 || !result.data) throw new Error("无法读取聊天记录");
  return result.data;
}
async function loadLatest(forceBottom = false) {
  const contact = chat.activeId;
  if (!contact || loading.value) return;
  const version = revision;
  const wasBottom = atBottom.value;
  const anchor = messages.value[messages.value.length - 1]?.id;
  loading.value = true;
  try {
    const rows: API.PrivateMessageVO[] = [];
    let current = 1;
    let keepLoading = false;
    do {
      const page = await fetchPage(contact, current++);
      if (version !== revision) return;
      total.value = Number(page.total || 0);
      const batch = page.records || [];
      rows.push(...batch);
      keepLoading =
        !!anchor &&
        batch.length === 50 &&
        rows.length < total.value &&
        batch.every((m) => newerMessageId(String(m.id), String(anchor)));
    } while (keepLoading);
    merge(rows);
    loadError.value = "";
    if (wasBottom || forceBottom) await scrollBottom();
  } catch {
    if (version === revision) loadError.value = "聊天记录暂未同步，请稍后重试";
  } finally {
    if (version === revision) loading.value = false;
  }
}
async function loadOlder() {
  if (loading.value) return;
  const version = revision;
  const area = scrollArea.value;
  const height = area?.scrollHeight || 0;
  const top = area?.scrollTop || 0;
  loading.value = true;
  try {
    const page = await fetchPage(
      chat.activeId,
      Math.floor(messages.value.length / 50) + 1
    );
    if (version !== revision) return;
    total.value = Number(page.total || 0);
    merge(page.records || []);
    loadError.value = "";
    await nextTick();
    if (area) area.scrollTop = top + area.scrollHeight - height;
  } catch {
    if (version === revision) loadError.value = "更早的消息加载失败，请重试";
  } finally {
    if (version === revision) loading.value = false;
  }
}
async function sendMessage() {
  const contact = chat.activeId;
  const content = draft.value.trim();
  if (!contact || !content || sending.value) return;
  chat.sendingContacts[contact] = true;
  const session = chat.sessionVersion;
  try {
    const result = (await addPrivateMessageUsingPost(
      { recipientId: contact, content },
      { silent: true }
    )) as unknown as API.BaseResponseLong_;
    if (result.code !== 200)
      throw new Error(result.message || "发送失败，请重试");
    if (chat.sessionVersion !== session) return;
    if ((chat.drafts[contact] || "").trim() === content)
      chat.drafts[contact] = "";
    showEmoji.value = false;
    await chat.refresh();
    if (chat.activeId === contact) await loadLatest(true);
  } catch (error) {
    if (chat.sessionVersion === session)
      ElMessage.error(
        error instanceof Error ? error.message : "发送失败，请重试"
      );
  } finally {
    if (chat.sessionVersion === session) delete chat.sendingContacts[contact];
  }
}
function onKeydown(event: KeyboardEvent) {
  if (
    event.key === "Enter" &&
    !event.shiftKey &&
    !event.isComposing &&
    event.keyCode !== 229
  ) {
    event.preventDefault();
    void sendMessage();
  }
}
function insertEmoji(event: { i: string }) {
  if (draft.value.length + event.i.length <= 1024) draft.value += event.i;
}
function poll() {
  if (document.visibilityState === "visible" && chat.opened) void loadLatest();
}
watch(
  () => chat.activeId,
  () => {
    revision++;
    messages.value = [];
    total.value = 0;
    loading.value = false;
    loadError.value = "";
    atBottom.value = true;
    showEmoji.value = false;
    void loadLatest(true);
  },
  { immediate: true }
);
watch(() => chat.activeConversation?.lastMessageId, poll);
onMounted(() => {
  timer = setInterval(poll, 8000);
  document.addEventListener("visibilitychange", poll);
});
onBeforeUnmount(() => {
  revision++;
  directoryRevision++;
  clearInterval(timer);
  document.removeEventListener("visibilitychange", poll);
});
</script>

<style scoped lang="scss">
.private-mail {
  display: flex;
  height: 100%;
  min-height: 0;
  color: var(--market-ink);
}
.mail-contacts {
  display: flex;
  flex-direction: column;
  width: 280px;
  flex-shrink: 0;
  min-height: 0;
  border-right: 1px dashed var(--market-line-strong);
  background: var(--market-paper-deep);
}
.mail-list-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 18px 14px;
  strong {
    font-family: var(--market-font-display);
    font-size: 18px;
  }
  span {
    font-size: 12px;
    color: var(--market-muted);
  }
}
.mail-contact-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px;
}
.mail-directory {
  margin: 0 10px 12px;
}
.mail-contact {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 10px;
  margin-bottom: 8px;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 5px 12px 5px 5px;
  color: var(--market-ink);
  background: var(--market-surface);
  cursor: pointer;
  transition: background 160ms, border-color 160ms;
  &:hover,
  &.selected {
    border-color: var(--market-primary);
    background: var(--market-primary-soft);
  }
  .el-avatar {
    flex-shrink: 0;
  }
}
.mail-contact-copy {
  min-width: 0;
  flex: 1;
}
.mail-contact-top {
  display: flex;
  gap: 5px;
  justify-content: space-between;
  align-items: center;
  strong {
    font-size: 14px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  time {
    flex-shrink: 0;
    font-size: 11px;
    color: var(--market-muted);
  }
}
.mail-contact-preview {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 7px;
  font-size: 12px;
  color: var(--market-muted);
}
.mail-unread {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 7px;
  font-size: 11px;
  color: var(--market-danger);
  &::before {
    content: "";
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }
}
.mail-list-note {
  padding: 10px 15px;
  margin: 0;
  font-size: 11px;
  color: var(--market-muted);
  border-top: 1px dashed var(--market-line);
}
.mail-thread {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--market-surface);
}
.mail-thread-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px dashed var(--market-line);
  small {
    display: block;
    margin-top: 4px;
    color: var(--market-muted);
    font-size: 11px;
  }
}
.mail-welcome {
  margin: auto;
  padding: 26px;
  text-align: center;
  .el-icon {
    color: var(--market-primary);
  }
  h3 {
    font-family: var(--market-font-display);
    font-size: 21px;
  }
  p {
    font-size: 13px;
    color: var(--market-muted);
    line-height: 1.8;
  }
}
.mail-messages {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 20px;
  background: repeating-linear-gradient(
      transparent 0 31px,
      var(--market-wash) 31px 32px
    ),
    var(--market-paper);
}
.mail-message {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin: 0 0 18px;
  p {
    max-width: 86%;
    padding: 11px 14px;
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    font-size: 14px;
    line-height: 1.7;
    border: 1px solid var(--market-line);
    border-radius: 3px 12px 12px 12px;
    background: var(--market-surface);
  }
  time {
    margin-top: 5px;
    font-size: 10px;
    color: var(--market-muted);
  }
  &.sent {
    align-items: flex-end;
    p {
      border-radius: 12px 3px 12px 12px;
      background: var(--market-primary-soft);
    }
  }
}
.mail-compose {
  position: relative;
  padding: 16px 18px;
  border-top: 1px dashed var(--market-line-strong);
  background: var(--market-surface);
}
.mail-compose-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  span {
    margin-right: auto;
    color: var(--market-muted);
    font-size: 11px;
  }
  .el-button {
    margin-left: 0;
    border-radius: 5px 10px 5px 5px;
  }
  .el-icon {
    margin-left: 5px;
  }
}
.mail-emoji-picker {
  position: absolute;
  right: 12px;
  bottom: 65px;
  z-index: 2;
  max-width: calc(100vw - 32px);
  :deep(.v3-emoji-picker) {
    max-width: 100%;
  }
}
.mail-status {
  font-size: 12px;
  padding: 10px 16px;
  margin: 0;
  background: var(--market-yellow-soft);
  color: var(--market-ink);
  button {
    border: 0;
    background: transparent;
    color: var(--market-primary);
    cursor: pointer;
    min-height: 32px;
  }
}
.mail-empty {
  text-align: center;
  margin-top: 35px;
  font-size: 13px;
  color: var(--market-muted);
}
.mail-history {
  display: block;
  margin: 0 auto 20px;
  border: 0;
  background: transparent;
  color: var(--market-primary);
  padding: 8px 14px;
  cursor: pointer;
}
.mail-new {
  position: absolute;
  bottom: 186px;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  padding: 10px 16px;
  border: 1px solid var(--market-primary);
  border-radius: 20px;
  color: var(--market-primary);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
  cursor: pointer;
}
.mail-back {
  display: none;
}
button:focus-visible {
  outline: 2px solid var(--market-primary);
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .mail-contacts {
    width: 100%;
    border: 0;
  }
  .mail-thread {
    display: none;
  }
  .has-contact .mail-contacts {
    display: none;
  }
  .has-contact .mail-thread {
    display: flex;
  }
  .mail-back {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    border: 1px solid var(--market-line);
    border-radius: 6px;
    background: var(--market-paper);
    color: var(--market-ink);
    cursor: pointer;
  }
  .mail-thread-header {
    padding: 10px 12px;
  }
  .mail-messages {
    padding: 14px;
  }
  .mail-compose {
    padding: 12px 12px max(12px, env(safe-area-inset-bottom));
  }
  .mail-compose-actions > span {
    font-size: 10px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .mail-contact {
    transition: none;
  }
}
</style>
