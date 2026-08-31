package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.mapper.AiUsageDailyMapper;
import com.pmsjl.model.entity.AiUsageDaily;
import com.pmsjl.service.AiUsageDailyService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;

@Service
public class AiUsageDailyServiceImpl extends ServiceImpl<AiUsageDailyMapper, AiUsageDaily>
        implements AiUsageDailyService {
    @Override
    public void insertUsageUserDaily(Long userId, LocalDate usageDate) {
        baseMapper.insertUsageUserDaily(IdWorker.getId(),userId, usageDate);
    }

    @Override
    public boolean updateRequestCount(Long userId, LocalDate usageDate, int limit, Date requestTime) {
        return baseMapper.updateRequestCount(userId, usageDate, limit, requestTime) == 1;
    }

    @Override
    public boolean recordSuccess(Long userId, LocalDate usageDate, long inputTokens, long outputTokens) {
        return baseMapper.recordSuccess(userId, usageDate, inputTokens, outputTokens) == 1;
    }

    @Override
    public boolean recordFailure(Long userId, LocalDate usageDate) {
        return baseMapper.recordFailure(userId, usageDate) == 1;
    }

    @Override
    public AiUsageDaily getUsage(Long userId, LocalDate usageDate) {
        return lambdaQuery()
                .eq(AiUsageDaily::getUserId, userId)
                .eq(AiUsageDaily::getUsageDate, usageDate)
                .one();
    }
}
