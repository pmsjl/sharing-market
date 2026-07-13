package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiConversationSceneEnum {
    /** 校园二手商品购买咨询场景。 */
    SHOPPING_GUIDE;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiConversationSceneEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
