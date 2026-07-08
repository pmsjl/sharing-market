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
            <span>{{ contact.userName || "对方用户" }}</span>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </div>

    <div class="chat-area">
      <div v-if="activeContactUser" class="chat-header">
        <el-avatar :size="34" :src="activeContactUser.userAvatar" />
        <div>
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
          type="primary"
          :disabled="!activeContact"
          @click="sendMessage"
        >
          发送
        </el-button>
        <el-button :disabled="!activeContact" @click="toggleEmojiPicker">
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

<style scoped>
.chat-room {
  display: flex;
  min-height: 560px;
  overflow: hidden;
  border: 1px solid var(--market-line);
  border-radius: 8px;
  background-color: #fffdf8;
}

.contact-list {
  width: 200px;
  border-right: 1px solid #e4e7ed;
  background-color: #fff;
}

.contact-title {
  padding: 14px 16px;
  color: var(--market-ink);
  font-size: 14px;
  font-weight: 900;
  border-bottom: 1px solid #e4e7ed;
}

.contact-list .el-menu-item {
  display: flex;
  gap: 8px;
  align-items: center;
  background-color: #f8f8f8;
  margin: 4px 0;
  border-radius: 4px;
  transition: background-color 0.3s ease;
}

.contact-avatar {
  flex: 0 0 auto;
}

.contact-list .el-menu-item.is-active,
.contact-list .el-menu-item:hover {
  background-color: #e6f7ff;
  color: #1890ff;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 62px;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.chat-header div {
  display: grid;
  gap: 2px;
}

.chat-header span {
  color: var(--market-muted);
  font-size: 12px;
}

.message-list {
  flex: 1;
  padding: 10px;
  overflow-y: auto;
  background-color: #fff;
}

.message-item {
  margin-bottom: 10px;
}

.message-content {
  max-width: 60%;
  padding: 10px;
  border-radius: 5px;
}

.message-content.sent {
  background-color: #95ec69;
  margin-left: auto;
}

.message-content.received {
  background-color: #f0f0f0;
  margin-right: auto;
}

.message-input {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-top: 1px solid #e4e7ed;
  background-color: #fff;
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
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }
}
</style>
