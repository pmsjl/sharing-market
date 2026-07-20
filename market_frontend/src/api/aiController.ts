import request from "@/utils/request";

export type AiMessageRoleEnum = "USER" | "ASSISTANT";
export type AiMessageStatusEnum = "PENDING" | "SUCCESS" | "FAILED";

export interface AiShoppingContext {
  budgetMin?: number;
  budgetMax?: number;
  usageScene?: string;
  preferenceTags?: string[];
  avoidances?: string[];
}

export interface CommodityVO {
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

export interface AiRecommendationVO {
  commodity: CommodityVO;
  matchScore?: number;
  reason?: string;
  riskTip?: string;
}

export interface AiSuggestedActionVO {
  type: "VIEW_COMMODITY" | "SEARCH_COMMODITY";
  label: string;
  commodityId?: string;
  keyword?: string;
}

export interface AiRagSourceVO {
  sourceType: "COMMODITY" | "POST" | "COMMENT" | "NOTICE" | "GUIDE";
  sourceId: string;
  title: string;
  excerpt: string;
  targetPath: string;
}

export interface AiStructuredContentVO {
  intent?: string;
  summary?: string;
  recommendations?: AiRecommendationVO[];
  purchaseAdvice?: string[];
  warnings?: string[];
  searchKeywords?: string[];
  suggestedActions?: AiSuggestedActionVO[];
  sources?: AiRagSourceVO[];
}

export interface AiMessageVO {
  id: string;
  sequenceNo?: number;
  role: AiMessageRoleEnum;
  content: string;
  structuredContent?: AiStructuredContentVO | null;
  status: AiMessageStatusEnum;
  /** Agent 失败的字符串标识，不是公开 Result.code。 */
  agentErrorKey?: string;
  retryable?: boolean;
  createTime: string;
}

export interface AiConversationVO {
  id: string;
  title: string;
  scene: string;
  shoppingContext?: AiShoppingContext | null;
  status: string;
  lastMessagePreview?: string;
  lastMessageTime: string;
  createTime: string;
}

export interface AiChatVO {
  requestId: string;
  conversation: AiConversationVO;
  userMessage: AiMessageVO;
  assistantMessage: AiMessageVO;
}

export interface AiPageVO<T> {
  current: number;
  pageSize: number;
  total: number;
  records: T[];
}

export interface Result<T> {
  code: number;
  data?: T;
  message?: string;
  hashMap?: Record<string, unknown>;
}

export interface AiChatMessageRequest {
  content: string;
  shoppingContext?: AiShoppingContext;
}

export const createAiConversation = (body: AiChatMessageRequest) =>
  request<unknown, Result<AiChatVO>>({
    url: "/api/ai/conversations",
    method: "POST",
    data: body
  });

export const sendAiConversationMessage = (
  conversationId: string,
  body: AiChatMessageRequest
) =>
  request<unknown, Result<AiChatVO>>({
    url: `/api/ai/conversations/${conversationId}/messages`,
    method: "POST",
    data: body
  });

export const listAiConversations = (
  current = 1,
  pageSize = 10,
  sortField = "lastMessageTime",
  sortOrder = "desc"
) =>
  request<unknown, Result<AiPageVO<AiConversationVO>>>({
    url: "/api/ai/conversations",
    method: "GET",
    params: { current, pageSize, sortField, sortOrder }
  });

export const listAiConversationMessages = (
  conversationId: string,
  current = 1,
  pageSize = 20,
  sortField = "sequenceNo",
  sortOrder = "desc"
) =>
  request<unknown, Result<AiPageVO<AiMessageVO>>>({
    url: `/api/ai/conversations/${conversationId}/messages`,
    method: "GET",
    params: { current, pageSize, sortField, sortOrder }
  });

export const deleteAiConversation = (conversationId: string) =>
  request<unknown, Result<boolean>>({
    url: `/api/ai/conversations/${conversationId}`,
    method: "DELETE"
  });
