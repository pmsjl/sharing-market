package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiPreferenceSignalEnum {
    /** 用户收藏商品产生的偏好信号。 */
    FAVOUR,

    /** 用户购买商品产生的偏好信号。 */
    PURCHASE,

    /** 用户给商品较高评分产生的偏好信号。 */
    HIGH_SCORE;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiPreferenceSignalEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
