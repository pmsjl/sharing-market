package com.pmsjl.service;

import com.pmsjl.model.dto.ai.AiUsageDate;
import com.pmsjl.model.dto.ai.internal.AgentUsage;
import com.pmsjl.model.vo.AiQuotaVO;

public interface AiAccessService {
    AiUsageDate reserveRequest(Long userId);

    void recordSuccess(Long userId, AiUsageDate reservation, AgentUsage usage);

    void recordFailure(Long userId, AiUsageDate reservation);

    AiQuotaVO getMyQuota(Long userId);
}
