package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiToolStatusEnum {
    /** 工具调用已经开始但尚未完成。 */
    PENDING,

    /** 工具调用成功完成。 */
    SUCCESS,

    /** 工具调用执行失败。 */
    FAILED;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiToolStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
