package com.pmsjl.model.dto.campusCoin;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CampusCoinGrantRequest implements Serializable {
    private Long userId;
    private BigDecimal amount;
    private String reason;

    private static final long serialVersionUID = 1L;
}
