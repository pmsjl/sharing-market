package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class CampusCoinTransactionVO implements Serializable {
    private Long id;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String transactionType;
    private String remark;
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
