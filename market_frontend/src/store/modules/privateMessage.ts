import { computed, h, ref } from "vue";
import { defineStore } from "pinia";
import { ElNotification } from "element-plus";
import {
  listPrivateConversations,
  PrivateConversation
} from "@/api/privateConversation";
import {
  loadMessageReadState,
  newerMessageId
} from "@/utils/privateMessageState";

export default defineStore("privateMessage", () => {
  const conversations = ref<PrivateConversation[]>([]);
  const opened = ref(false);
  const activeId = ref("");
  const loading = ref(false);
  const error = ref("");
  const owner = ref("");
  const readState = ref(loadMessageReadState(""));
  const drafts = ref<Record<string, string>>({});
  const sendingContacts = ref<Record<string, boolean>>({});
  const sessionVersion = ref(0);
  let generation = 0;
  let inFlight: Promise<void> | undefined;
  let observed: Record<string, string> = {};
  let hydrated = false;
  let notification: ReturnType<typeof ElNotification> | undefined;
  const activeConversation = computed(() =>
    conversations.value.find((item) => item.contactUserId === activeId.value)
  );
  const isUnread = (item: PrivateConversation) =>
    newerMessageId(
      item.lastReceivedMessageId,
      readState.value.read[item.contactUserId]
    );
  const hasUnread = computed(() => conversations.value.some(isUnread));

  function persist() {
    try {
      // Merge another tab's newer reading position before writing our snapshot.
      const stored = loadMessageReadState(owner.value);
      for (const [contact, id] of Object.entries(stored.read)) {
        if (newerMessageId(id, readState.value.read[contact]))
          readState.value.read[contact] = id;
      }
      localStorage.setItem(
        `market:private-read:${owner.value}`,
        JSON.stringify(readState.value)
      );
    } catch {
      // The current session still works when browser storage is disabled/full.
    }
  }
  function markRead(contactId: string, messageId?: string) {
    if (!messageId || !owner.value) return;
    if (newerMessageId(messageId, readState.value.read[contactId])) {
      readState.value.read[contactId] = messageId;
      persist();
    }
  }
  function openContact(contact?: {
    id?: string;
    userName?: string;
    userAvatar?: string;
  }) {
    if (contact?.id && String(contact.id) !== owner.value) {
      const id = String(contact.id);
      if (!conversations.value.some((item) => item.contactUserId === id)) {
        conversations.value.unshift({
          contactUserId: id,
          userName: contact.userName || "对方用户",
          userAvatar: contact.userAvatar
        });
      }
      activeId.value = id;
    }
    opened.value = true;
    void refresh();
  }
  function reset(userId = "") {
    generation++;
    sessionVersion.value++;
    notification?.close();
    notification = undefined;
    inFlight = undefined;
    owner.value = userId;
    conversations.value = [];
    activeId.value = "";
    opened.value = false;
    drafts.value = {};
    sendingContacts.value = {};
    observed = {};
    hydrated = false;
    loading.value = false;
    error.value = "";
    readState.value = loadMessageReadState(userId);
  }
  async function refresh() {
    if (!owner.value) return;
    if (inFlight) return inFlight;
    const revision = generation;
    loading.value = true;
    const task = async () => {
      try {
        // Fetch every page so an old conversation never vanishes from local read tracking.
        const rows: PrivateConversation[] = [];
        let current = 1;
        let total = 0;
        do {
          const page = await listPrivateConversations(current++);
          if (generation !== revision) return;
          rows.push(...page.records);
          total = page.total;
          if (!page.records.length) break;
        } while (rows.length < total);
        const items = [
          ...new Map(
            rows.map((item) => [
              String(item.contactUserId),
              {
                ...item,
                contactUserId: String(item.contactUserId),
                lastMessageId: item.lastMessageId
                  ? String(item.lastMessageId)
                  : undefined,
                lastReceivedMessageId: item.lastReceivedMessageId
                  ? String(item.lastReceivedMessageId)
                  : undefined
              }
            ])
          ).values()
        ];
        if (!readState.value.initialized) {
          for (const item of items) {
            if (item.lastReceivedMessageId)
              readState.value.read[item.contactUserId] =
                item.lastReceivedMessageId;
          }
          readState.value.initialized = true;
          persist();
        }
        const incoming = hydrated
          ? items.filter(
              (item) =>
                newerMessageId(
                  item.lastReceivedMessageId,
                  observed[item.contactUserId]
                ) &&
                isUnread(item) &&
                !(opened.value && activeId.value === item.contactUserId)
            )
          : [];
        observed = Object.fromEntries(
          items.map((item) => [
            item.contactUserId,
            item.lastReceivedMessageId || ""
          ])
        );
        // Keep unsent contacts until the session ends, including the selected draft.
        const temporary = conversations.value.filter(
          (item) =>
            !item.lastMessageId &&
            !items.some((row) => row.contactUserId === item.contactUserId)
        );
        conversations.value = [...items, ...temporary];
        hydrated = true;
        error.value = "";
        if (incoming.length && document.visibilityState === "visible") {
          notification?.close();
          const first = incoming[0];
          notification = ElNotification({
            title: "校园来信",
            message: h(
              "button",
              { type: "button", class: "campus-message-notification-action" },
              incoming.length > 1
                ? `${first.userName}等 ${incoming.length} 位同学发来新消息`
                : `${first.userName}发来一条消息，点击查看`
            ),
            duration: 4000,
            customClass: "campus-message-notification",
            onClick: () => {
              openContact({
                id: first.contactUserId,
                userName: first.userName
              });
              notification?.close();
            }
          });
        }
      } catch {
        if (generation === revision) error.value = "消息暂未同步，将自动重试";
      } finally {
        if (generation === revision) {
          loading.value = false;
          inFlight = undefined;
        }
      }
    };
    inFlight = task();
    return inFlight;
  }
  function syncReadStorage() {
    const incoming = loadMessageReadState(owner.value);
    for (const [contact, id] of Object.entries(incoming.read)) {
      if (newerMessageId(id, readState.value.read[contact]))
        readState.value.read[contact] = id;
    }
  }
  return {
    conversations,
    opened,
    activeId,
    activeConversation,
    loading,
    error,
    owner,
    drafts,
    sendingContacts,
    sessionVersion,
    hasUnread,
    isUnread,
    markRead,
    openContact,
    refresh,
    reset,
    syncReadStorage
  };
});
