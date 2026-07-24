<template>
  <div class="chat-room">
    <div class="contact-list">
      <div class="contact-title">
        {{ props.allowDirectoryContacts ? "联系人" : "本次私聊" }}
      </div>
      <el-empty
        v-if="contacts.length === 0"
        description="暂无联系人"
        :image-size="80"
      />
      <el-scrollbar v-else>
        <el-menu :default-active="activeContact" @select="handleContactSelect">
          <el-menu-item
            v-for="contact in contacts"
            :key="contact.id ?? contact.userName"
            :index="String(contact.id ?? '')"
          >
            <el-avatar
              :size="28"
              :src="contact.userAvatar"
              class="contact-avatar"
            />
            <span class="contact-name">{{
              contact.userName || "对方用户"
            }}</span>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </div>

    <div class="chat-area">
      <div v-if="activeContactUser" class="chat-header">
        <el-avatar :size="34" :src="activeContactUser.userAvatar" />
        <div class="chat-user-copy">
          <strong>{{ activeContactUser.userName || "对方用户" }}</strong>
          <span>一对一私聊</span>
        </div>
      </div>

      <div class="message-list">
        <el-empty
          v-if="!activeContact"
          description="请选择联系人"
          :image-size="110"
        />
        <el-empty
          v-else-if="messages.length === 0"
          description="暂无消息，开始对话"
          :image-size="110"
        />
        <el-scrollbar v-else>
          <div
            v-for="message in messages"
            :key="message.id"
            class="message-item"
          >
            <div
              :class="[
                'message-content',
                message.senderId === currentUserId ? 'sent' : 'received'
              ]"
            >
              {{ message.content }}
            </div>
          </div>
        </el-scrollbar>
      </div>

      <div class="message-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="2"
          placeholder="请输入消息"
          :disabled="!activeContact"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button
          class="send-message-button"
          type="primary"
          :disabled="!activeContact"
          @click="sendMessage"
        >
          发送
        </el-button>
        <el-button
          class="emoji-button"
          :disabled="!activeContact"
          @click="toggleEmojiPicker"
        >
          😀
        </el-button>

        <EmojiPicker
          v-if="showEmojiPicker"
          :native="true"
          @select="onEmojiSelect"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import EmojiPicker from "vue3-emoji-picker";
import "vue3-emoji-picker/css";
import { listUserVoByPageUsingPost } from "@/api/userController";
import {
  addPrivateMessageUsingPost,
  listMyPrivateMessageVoByPageUsingPost
} from "@/api/privateMessageController";
import { GET_ID, GET_ROLE } from "@/utils/token";

type ChatContact = Pick<API.UserVO, "id" | "userName" | "userAvatar">;

const props = withDefaults(
  defineProps<{
    initialContact?: ChatContact;
    allowDirectoryContacts?: boolean;
  }>(),
  {
    allowDirectoryContacts: true
  }
);

const userRole = GET_ROLE() || "";
const currentUserId = ref(GET_ID() || "");
const contacts = ref<API.UserVO[]>([]);
const activeContact = ref("");
const messages = ref<API.PrivateMessageVO[]>([]);
const inputMessage = ref("");
const showEmojiPicker = ref(false);

const activeContactUser = computed(() =>
  contacts.value.find(
    (contact) => String(contact.id || "") === activeContact.value
  )
);

const loadContacts = async () => {
  if (!props.allowDirectoryContacts) {
    contacts.value = [];
    return;
  }

  try {
    const queryRole = userRole === "admin" ? "user" : "admin";
    const response = (await listUserVoByPageUsingPost({
      userRole: queryRole
    })) as unknown as API.BaseResponsePageUserVO_;
    if (response.data?.records) {
      contacts.value = response.data.records;
    }
  } catch (error) {
    ElMessage.error("加载联系人失败");
  }
};

const loadMessages = async (recipientId: string) => {
  try {
    const response = (await listMyPrivateMessageVoByPageUsingPost({
      contactUserId: recipientId,
      current: 1,
      pageSize: 50
    })) as unknown as API.BaseResponsePagePrivateMessageVO_;

    const allMessages = response.data?.records || [];
    allMessages.sort(
      (a, b) =>
        new Date(a.createTime || "").getTime() -
        new Date(b.createTime || "").getTime()
    );

    messages.value = allMessages;
  } catch (error) {
    ElMessage.error("加载聊天记录失败");
  }
};

const syncInitialContact = async () => {
  const contactId = String(props.initialContact?.id || "");

  if (!contactId) {
    if (!props.allowDirectoryContacts) {
      contacts.value = [];
      activeContact.value = "";
      messages.value = [];
    }
    return;
  }

  const normalizedContact: API.UserVO = {
    ...props.initialContact,
    id: contactId,
    userName: props.initialContact?.userName || "对方用户",
    userAvatar: props.initialContact?.userAvatar || ""
  };

  const exists = contacts.value.some(
    (contact) => String(contact.id || "") === contactId
  );

  if (exists) {
    contacts.value = contacts.value.map((contact) =>
      String(contact.id || "") === contactId
        ? { ...contact, ...normalizedContact }
        : contact
    );
  } else {
    contacts.value = props.allowDirectoryContacts
      ? [normalizedContact, ...contacts.value]
      : [normalizedContact];
  }

  activeContact.value = contactId;
  await loadMessages(contactId);
};

const handleContactSelect = (index: string) => {
  activeContact.value = index;
  loadMessages(index);
};

const sendMessage = async () => {
  if (!inputMessage.value.trim()) {
    return;
  }

  const recipientId = activeContact.value;
  if (!recipientId) {
    ElMessage.warning("请先选择联系人");
    return;
  }

  try {
    await addPrivateMessageUsingPost({
      recipientId,
      content: inputMessage.value
    });
    inputMessage.value = "";
    loadMessages(recipientId);
  } catch (error) {
    ElMessage.error("发送消息失败");
  }
};

const toggleEmojiPicker = () => {
  showEmojiPicker.value = !showEmojiPicker.value;
};

const onEmojiSelect = (event: any) => {
  const emoji = event.i;
  if (emoji) {
    inputMessage.value += emoji;
  }
};

watch(
  () => props.initialContact,
  () => {
    syncInitialContact();
  },
  { deep: true }
);

onMounted(async () => {
  await loadContacts();
  await syncInitialContact();
});
</script>

<style scoped lang="scss">
.chat-room {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  color: var(--market-ink);
  background: var(--market-surface);
  box-shadow: var(--market-shadow-soft);
}

.contact-list {
  width: 244px;
  flex: 0 0 244px;
  border-right: 1px dashed var(--market-line);
  background: var(--market-paper-deep);
}

.contact-title {
  position: relative;
  padding: 16px 18px 13px;
  color: var(--market-ink);
  font-size: 14px;
  font-weight: 900;
  border-bottom: 1px dashed var(--market-line);
  font-family: var(--market-font-display);
  letter-spacing: 1px;

  &::after {
    position: absolute;
    right: 14px;
    bottom: 13px;
    color: var(--market-orange);
    font-family: var(--market-font-mono);
    font-size: 8px;
    letter-spacing: 1px;
    content: "DIRECTORY";
  }
}

.contact-list :deep(.el-menu) {
  border-right: 0;
  background: transparent;
}

.contact-list .el-menu-item {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 8px;
  align-items: center;
  margin: 6px 8px;
  padding: 0 13px !important;
  border: 1px solid transparent;
  border-radius: 4px;
  background: var(--market-surface);
  transition: background var(--market-dur-fast) ease,
    transform var(--market-dur-fast) ease;
}

.contact-name {
  min-width: 0;
  overflow: hidden;
  color: var(--market-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-avatar {
  flex: 0 0 auto;
}

.contact-list .el-menu-item.is-active,
.contact-list .el-menu-item:hover {
  border-color: var(--market-line);
  color: var(--market-green);
  background: var(--market-note-green-bg);
  transform: translateX(2px);
}

.contact-list .el-menu-item.is-active::after {
  width: 7px;
  height: 7px;
  margin-left: auto;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--market-orange);
  box-shadow: 0 0 0 3px rgba(224, 101, 31, 0.1);
  content: "";
}

.chat-area {
  flex: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.chat-header {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 62px;
  padding: 12px 16px;
  border-bottom: 1px dashed var(--market-line);
  background: var(--market-header-bg);
}

.chat-user-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.chat-user-copy strong {
  overflow: hidden;
  color: var(--market-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-header span {
  color: var(--market-muted);
  font-size: 12px;
}

.message-list {
  flex: 1;
  padding: 10px;
  overflow-y: auto;
  background: linear-gradient(
      90deg,
      transparent 40px,
      rgba(192, 57, 43, 0.18) 40px 41px,
      transparent 41px
    ),
    repeating-linear-gradient(
      transparent,
      transparent 31px,
      var(--market-line) 31px 32px
    ),
    var(--market-surface);
}

.message-item {
  margin-bottom: 10px;
}

.message-content {
  position: relative;
  min-width: 0;
  max-width: 60%;
  padding: 11px 14px;
  border: 1px solid var(--market-line);
  border-radius: 4px;
  line-height: 1.65;
  box-shadow: 0 6px 14px rgba(62, 45, 24, 0.09);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-content.sent {
  border-color: rgba(43, 110, 80, 0.24);
  background: linear-gradient(
      135deg,
      transparent calc(100% - 13px),
      rgba(43, 110, 80, 0.1) 0
    ),
    var(--market-note-green-bg);
  margin-left: auto;
  transform: rotate(0.6deg);
}

.message-content.received {
  background: linear-gradient(
      225deg,
      transparent calc(100% - 13px),
      rgba(224, 101, 31, 0.07) 0
    ),
    var(--market-surface);
  margin-right: auto;
  transform: rotate(-0.6deg);
}

.message-input {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 12px 11px;
  border-top: 1px dashed var(--market-muted);
  background: var(--market-paper-deep);

  &::before {
    position: absolute;
    top: -8px;
    left: 16px;
    padding: 0 5px;
    color: var(--market-muted);
    font-size: 13px;
    background: var(--market-paper-deep);
    content: "✂";
  }
}

.send-message-button {
  min-width: 72px;
  border-radius: 5px;
  font-family: var(--market-font-display);
}

.emoji-button {
  width: 40px;
  min-width: 40px;
  padding: 0;
}

.el-textarea {
  flex: 1;
}

emoji-picker {
  position: absolute;
  bottom: 60px;
  right: 10px;
  z-index: 1000;
}

@media (max-width: 720px) {
  .chat-room {
    flex-direction: column;
    min-height: 620px;
  }

  .contact-list {
    width: 100%;
    max-height: 190px;
    flex-basis: auto;
    border-right: none;
    border-bottom: 1px dashed var(--market-line);
  }

  .message-content {
    max-width: 82%;
  }

  .message-input {
    flex-wrap: wrap;

    .el-textarea {
      flex-basis: 100%;
    }
  }
}

@media (max-width: 420px) {
  .chat-room {
    min-height: 580px;
  }

  .message-list {
    padding: 8px;
  }

  .message-content {
    max-width: 90%;
    padding: 10px 12px;
  }
}
</style>
