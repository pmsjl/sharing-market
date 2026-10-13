package com.pmsjl.model.dto.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
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

    /** 续聊必填：本条用户消息预期占用的序号；首轮创建不使用。 */
    @JsonDeserialize(using = ExpectedUserSequenceNoDeserializer.class)
    private Integer expectedUserSequenceNo;

    private static final long serialVersionUID = 1L;

    /** 禁止将小数截断为合法序号；非法值交给续聊校验，首轮仍不要求该字段。 */
    public static class ExpectedUserSequenceNoDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode value = parser.readValueAsTree();
            return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
        }
    }
}
