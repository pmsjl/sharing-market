package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;

/** Shared public response for first messages and follow-up messages. */
@Data
public class AiChatVO implements Serializable {
    /** 本轮消息处理的全链路请求标识。 */
    private String requestId;

    /** 创建或更新后的会话信息。 */
    private AiConversationVO conversation;

    /** 本轮已经持久化的用户消息。 */
    private AiMessageVO userMessage;

    /** 已保存的助手消息，通常为 PENDING；提交被拒绝时包含 FAILED 和错误信息。 */
    private AiMessageVO assistantMessage;

    private static final long serialVersionUID = 1L;
}
