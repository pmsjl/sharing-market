package com.pmsjl.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/** Confidence derived only from the number of distinct valid commodities. */
public enum AiPreferenceConfidenceEnum {
    NONE,
    LOW,
    MEDIUM,
    HIGH;

    @JsonValue
    public String getValue() {
        return name();
    }
}
