package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CampusCoinWalletVO implements Serializable {
    private BigDecimal balance;

    private static final long serialVersionUID = 1L;
}
