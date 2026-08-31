package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.entity.AiUsageGlobalDaily;

import java.time.LocalDate;
import java.util.Date;

public interface AiUsageGlobalDailyService extends IService<AiUsageGlobalDaily> {
    void insertUsageGlobalDaily(LocalDate usageDate);

    boolean updateRequestCount(LocalDate usageDate, int limit, Date requestTime);

    boolean recordSuccess(LocalDate usageDate, long inputTokens, long outputTokens);

    boolean recordFailure(LocalDate usageDate);

    AiUsageGlobalDaily getUsage(LocalDate usageDate);
}
