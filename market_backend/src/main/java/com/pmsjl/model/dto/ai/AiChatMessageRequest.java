package com.pmsjl.model.dto.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * Shared request body for creating a conversation and sending a follow-up.
 */
@Data
public class AiChatMessageRequest implements Serializable {

    /** 用户本次发送的自然语言消息。 */
    private String content;

    /** 可选的购买条件；传入时用于创建或更新当前会话的购买偏好。 */
    private AiShoppingContext shoppingContext;

    private static final long serialVersionUID = 1L;
}
