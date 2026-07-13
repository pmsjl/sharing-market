package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiSuggestedActionTypeEnum {
    /** 跳转到指定商品详情页。 */
    VIEW_COMMODITY,

    /** 使用指定关键词进入商品搜索。 */
    SEARCH_COMMODITY;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiSuggestedActionTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
