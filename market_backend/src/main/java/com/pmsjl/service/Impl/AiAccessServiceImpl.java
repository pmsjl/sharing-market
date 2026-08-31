package com.pmsjl.service.Impl;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.config.AiAccessProperties;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.ai.AiUsageDate;
import com.pmsjl.model.dto.ai.internal.AgentUsage;
import com.pmsjl.model.entity.AiUsageDaily;
import com.pmsjl.model.entity.AiUsageGlobalDaily;
import com.pmsjl.model.vo.AiQuotaVO;
import com.pmsjl.service.AiAccessService;
import com.pmsjl.service.AiUsageDailyService;
import com.pmsjl.service.AiUsageGlobalDailyService;
import com.pmsjl.utils.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AiAccessServiceImpl implements AiAccessService {
    private final AiUsageDailyService usageDailyService;
    private final AiUsageGlobalDailyService globalDailyService;
    private final AiAccessProperties accessProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AiUsageDate reserveRequest(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 非法");
        Date now = new Date();
        LocalDate usageDate = LocalDate.now(accessProperties.getZoneId());
        usageDailyService.insertUsageUserDaily(userId, usageDate);
        if (!usageDailyService.updateRequestCount(
                userId, usageDate, accessProperties.getUserDailyLimit(), now)) {
            throw new BusinessException(ErrorCode.AI_USER_DAILY_QUOTA_EXCEEDED,
                    "你今日的 AI 咨询额度已用完，将于明日恢复");
        }

        globalDailyService.insertUsageGlobalDaily(usageDate);
        if (!globalDailyService.updateRequestCount(
                usageDate, accessProperties.getGlobalDailyLimit(), now)) {
            throw new BusinessException(ErrorCode.AI_GLOBAL_DAILY_QUOTA_EXCEEDED,
                    "今日平台 AI 体验额度已用完，将于明日恢复");
        }
        return new AiUsageDate(usageDate);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(Long userId, AiUsageDate aiUsageDate, AgentUsage usage) {
        validateReservation(userId, aiUsageDate);
        long inputTokens = usage == null || usage.getInputTokens() == null ? 0L : usage.getInputTokens();
        long outputTokens = usage == null || usage.getOutputTokens() == null ? 0L : usage.getOutputTokens();
        ThrowUtils.throwIf(!usageDailyService.recordSuccess(
                        userId, aiUsageDate.usageDate(), inputTokens, outputTokens),
                ErrorCode.OPERATION_ERROR, "更新用户 AI 用量失败");
        ThrowUtils.throwIf(!globalDailyService.recordSuccess(
                        aiUsageDate.usageDate(), inputTokens, outputTokens),
                ErrorCode.OPERATION_ERROR, "更新平台 AI 用量失败");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailure(Long userId, AiUsageDate aiUsageDate) {
        validateReservation(userId, aiUsageDate);
        ThrowUtils.throwIf(!usageDailyService.recordFailure(userId, aiUsageDate.usageDate()),
                ErrorCode.OPERATION_ERROR, "更新用户 AI 失败次数失败");
        ThrowUtils.throwIf(!globalDailyService.recordFailure(aiUsageDate.usageDate()),
                ErrorCode.OPERATION_ERROR, "更新平台 AI 失败次数失败");
    }

    @Override
    public AiQuotaVO getMyQuota(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 非法");
        LocalDate usageDate = LocalDate.now(accessProperties.getZoneId());
        AiUsageDaily userUsage = usageDailyService.getUsage(userId, usageDate);
        AiUsageGlobalDaily globalUsage = globalDailyService.getUsage(usageDate);
        int usedCount = userUsage == null || userUsage.getRequestCount() == null
                ? 0 : userUsage.getRequestCount();
        int globalUsed = globalUsage == null || globalUsage.getRequestCount() == null
                ? 0 : globalUsage.getRequestCount();

        ZonedDateTime nextDay = usageDate.plusDays(1)
                .atStartOfDay(accessProperties.getZoneId());
        AiQuotaVO quota = new AiQuotaVO();
        quota.setDailyLimit(accessProperties.getUserDailyLimit());
        quota.setUsedCount(usedCount);
        quota.setRemaining(Math.max(0, accessProperties.getUserDailyLimit() - usedCount));
        quota.setGlobalDailyLimit(accessProperties.getGlobalDailyLimit());
        quota.setGlobalUsed(globalUsed);
        quota.setGlobalRemaining(Math.max(0, accessProperties.getGlobalDailyLimit() - globalUsed));
        quota.setResetAt(OffsetDateTime.from(nextDay));
        return quota;
    }

    private void validateReservation(Long userId, AiUsageDate aiUsageDate) {
        ThrowUtils.throwIf(userId == null || userId <= 0 || aiUsageDate == null
                        || aiUsageDate.usageDate() == null,
                ErrorCode.PARAMS_ERROR, "AI 用量预占信息无效");
    }
}
