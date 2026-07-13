import request from "@/utils/request";

export type AiMessageRole = "USER" | "ASSISTANT";
export type AiMessageStatus = "PENDING" | "SUCCESS" | "FAILED";

export interface AiShoppingContext {
  budgetMin?: number;
  budgetMax?: number;
  usageScene?: string;
  preferenceTags?: string[];
  avoidances?: string[];
}

export interface AiCommodity {
  id: string;
  commodityName: string;
  commodityDescription?: string;
  commodityAvatar?: string;
  degree?: string;
  commodityTypeName?: string;
  commodityInventory?: number;
  price: number;
  viewNum?: number;
  favourNum?: number;
}

export interface AiRecommendation {
  commodity: AiCommodity;
  matchScore?: number;
  reason?: string;
  riskTip?: string;
}

export interface AiSuggestedAction {
  type: "VIEW_COMMODITY" | "SEARCH_COMMODITY";
  label: string;
  commodityId?: string;
  keyword?: string;
}

export interface AiRagSource {
  sourceType: "COMMODITY" | "POST" | "COMMENT" | "NOTICE" | "GUIDE";
  sourceId: string;
  title: string;
  excerpt: string;
  targetPath: string;
}

export interface AiStructuredContent {
  intent?: string;
  summary?: string;
  recommendations?: AiRecommendation[];
  purchaseAdvice?: string[];
  warnings?: string[];
  searchKeywords?: string[];
  suggestedActions?: AiSuggestedAction[];
  sources?: AiRagSource[];
}

export interface AiMessage {
  id: string;
  sequenceNo?: number;
  role: AiMessageRole;
  content: string;
  structuredContent?: AiStructuredContent | null;
  status: AiMessageStatus;
  errorCode?: string;
  retryable?: boolean;
  createTime: string;
}

export interface AiConversation {
  id: string;
  title: string;
  scene: string;
  shoppingContext?: AiShoppingContext | null;
  status: string;
  lastMessagePreview?: string;
  lastMessageTime: string;
  createTime: string;
}

export interface AiChatResponse {
  requestId: string;
  conversation: AiConversation;
  userMessage: AiMessage;
  assistantMessage: AiMessage;
}

export interface AiPage<T> {
  current: number;
  pageSize: number;
  total: number;
  records: T[];
}

export interface AiResult<T> {
  code: number;
  data?: T;
  message?: string;
  hashMap?: Record<string, unknown>;
}

export interface SendAiMessageRequest {
  content: string;
  shoppingContext?: AiShoppingContext;
}

export const createAiConversation = (body: SendAiMessageRequest) =>
  request<unknown, AiResult<AiChatResponse>>({
    url: "/api/ai/conversations",
    method: "POST",
    data: body
  });

export const sendAiConversationMessage = (
  conversationId: string,
  body: SendAiMessageRequest
) =>
  request<unknown, AiResult<AiChatResponse>>({
    url: `/api/ai/conversations/${conversationId}/messages`,
    method: "POST",
    data: body
  });

export const listAiConversations = (current = 1, pageSize = 10) =>
  request<unknown, AiResult<AiPage<AiConversation>>>({
    url: "/api/ai/conversations",
    method: "GET",
    params: { current, pageSize }
  });

export const listAiConversationMessages = (
  conversationId: string,
  current = 1,
  pageSize = 20
) =>
  request<unknown, AiResult<AiPage<AiMessage>>>({
    url: `/api/ai/conversations/${conversationId}/messages`,
    method: "GET",
    params: { current, pageSize }
  });

export const deleteAiConversation = (conversationId: string) =>
  request<unknown, AiResult<boolean>>({
    url: `/api/ai/conversations/${conversationId}`,
    method: "DELETE"
  });
