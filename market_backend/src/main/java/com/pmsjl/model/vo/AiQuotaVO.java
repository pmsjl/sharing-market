package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class AiQuotaVO implements Serializable {
    private int dailyLimit;
    private int usedCount;
    private int remaining;
    private int globalDailyLimit;
    private int globalUsed;
    private int globalRemaining;
    private OffsetDateTime resetAt;

    private static final long serialVersionUID = 1L;
}
