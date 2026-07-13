package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI contract enum. Values are serialized by their stable API names.
 */
public enum AiMessageStatusEnum {
    /** 消息已创建，正在等待 Agent 完成处理。 */
    PENDING,

    /** 消息已经成功保存或生成。 */
    SUCCESS,

    /** 助手消息生成失败，可结合错误码和 retryable 处理。 */
    FAILED;

    @JsonValue
    public String getValue() {
        return name();
    }

    @JsonCreator
    public static AiMessageStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
