package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiIntentEnum {
    /** 根据用户需求推荐平台内商品。 */
    COMMODITY_RECOMMENDATION,

    /** 提供商品选择、验货或购买决策建议。 */
    PURCHASE_ADVICE,

    /** 识别并说明商品或交易中的潜在风险。 */
    RISK_CHECK,

    /** 无法归入以上类型的通用校园交易咨询。 */
    GENERAL_GUIDE;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiIntentEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
