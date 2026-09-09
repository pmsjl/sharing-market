import request from "@/utils/request";

export type PrivateConversation = {
  contactUserId: string;
  userName: string;
  userAvatar?: string;
  lastMessageId?: string;
  lastMessageContent?: string;
  lastMessageTime?: string;
  lastReceivedMessageId?: string;
};

export type ConversationPage = {
  records: PrivateConversation[];
  total: number;
};

export async function listPrivateConversations(current = 1) {
  const result = (await request.post(
    "/api/privateMessage/my/conversation/list/page/vo",
    { current, pageSize: 100 },
    { silent: true } as Record<string, unknown>
  )) as unknown as { code: number; data: ConversationPage; message?: string };
  if (result.code !== 200 || !result.data) {
    throw new Error(result.message || "暂时无法获取会话");
  }
  return result.data;
}
