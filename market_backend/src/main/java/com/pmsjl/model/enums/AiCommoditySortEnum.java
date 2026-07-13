package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiCommoditySortEnum {
    /** 按商品与查询条件的相关度排序。 */
    RELEVANCE,

    /** 按商品价格从低到高排序。 */
    PRICE_ASC,

    /** 按商品价格从高到低排序。 */
    PRICE_DESC,

    /** 按商品收藏数从高到低排序。 */
    FAVOUR_DESC;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiCommoditySortEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
