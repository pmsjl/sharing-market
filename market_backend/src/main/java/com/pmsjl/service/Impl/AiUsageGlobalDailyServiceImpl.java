package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.mapper.AiUsageGlobalDailyMapper;
import com.pmsjl.model.entity.AiUsageGlobalDaily;
import com.pmsjl.service.AiUsageGlobalDailyService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;

@Service
public class AiUsageGlobalDailyServiceImpl
        extends ServiceImpl<AiUsageGlobalDailyMapper, AiUsageGlobalDaily>
        implements AiUsageGlobalDailyService {
    @Override
    public void insertUsageGlobalDaily(LocalDate usageDate) {
        baseMapper.insertUsageGlobalDaily(usageDate);
    }

    @Override
    public boolean updateRequestCount(LocalDate usageDate, int limit, Date requestTime) {
        return baseMapper.updateRequestCount(usageDate, limit, requestTime) == 1;
    }

    @Override
    public boolean recordSuccess(LocalDate usageDate, long inputTokens, long outputTokens) {
        return baseMapper.recordSuccess(usageDate, inputTokens, outputTokens) == 1;
    }

    @Override
    public boolean recordFailure(LocalDate usageDate) {
        return baseMapper.recordFailure(usageDate) == 1;
    }

    @Override
    public AiUsageGlobalDaily getUsage(LocalDate usageDate) {
        return getById(usageDate);
    }
}
