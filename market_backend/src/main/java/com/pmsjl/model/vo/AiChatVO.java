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

    /** 本轮生成的助手消息，失败时包含失败状态和错误信息。 */
    private AiMessageVO assistantMessage;

    private static final long serialVersionUID = 1L;
}
