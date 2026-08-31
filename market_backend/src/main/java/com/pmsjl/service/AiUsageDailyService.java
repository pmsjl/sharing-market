package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.entity.AiUsageDaily;

import java.time.LocalDate;
import java.util.Date;

public interface AiUsageDailyService extends IService<AiUsageDaily> {
    void insertUsageUserDaily(Long userId, LocalDate usageDate);

    boolean updateRequestCount(Long userId, LocalDate usageDate, int limit, Date requestTime);

    boolean recordSuccess(Long userId, LocalDate usageDate, long inputTokens, long outputTokens);

    boolean recordFailure(Long userId, LocalDate usageDate);

    AiUsageDaily getUsage(Long userId, LocalDate usageDate);
}
