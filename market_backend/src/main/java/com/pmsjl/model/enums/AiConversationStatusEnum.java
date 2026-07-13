package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiConversationStatusEnum {
    /** 正常使用并显示在当前会话列表中的会话。 */
    ACTIVE,

    /** 已归档、不再作为活跃会话展示的会话。 */
    ARCHIVED;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiConversationStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
