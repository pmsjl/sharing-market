package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiMessageRoleEnum {
    /** 当前平台用户发送的消息。 */
    USER,

    /** AI Agent 生成的回复消息。 */
    ASSISTANT;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiMessageRoleEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
