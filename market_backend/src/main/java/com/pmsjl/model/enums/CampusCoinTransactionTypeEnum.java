package com.pmsjl.model.enums;

import lombok.Getter;

@Getter
public enum CampusCoinTransactionTypeEnum {
    OPENING_BALANCE("OPENING_BALANCE"),
    REGISTER_GRANT("REGISTER_GRANT"),
    ADMIN_GRANT("ADMIN_GRANT"),
    PURCHASE("PURCHASE");

    private final String value;

    CampusCoinTransactionTypeEnum(String value) {
        this.value = value;
    }
}
