package com.pmsjl.service.Impl;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.config.AiAccessProperties;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.ai.AiUsageDate;
import com.pmsjl.model.dto.ai.internal.AgentUsage;
import com.pmsjl.service.AiUsageDailyService;
import com.pmsjl.service.AiUsageGlobalDailyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAccessServiceImplTest {
    @Mock
    private AiUsageDailyService usageDailyService;
    @Mock
    private AiUsageGlobalDailyService globalDailyService;
    private AiAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        AiAccessProperties access = new AiAccessProperties();
        access.setUserDailyLimit(10);
        access.setGlobalDailyLimit(100);
        access.setTimezone("Asia/Shanghai");
        access.validate();
        service = new AiAccessServiceImpl(usageDailyService, globalDailyService, access);
    }

    @Test
    void reservesBothUserAndPlatformQuota() {
        when(usageDailyService.updateRequestCount(eq(7L), any(LocalDate.class), eq(10), any(Date.class)))
                .thenReturn(true);
        when(globalDailyService.updateRequestCount(any(LocalDate.class), eq(100), any(Date.class)))
                .thenReturn(true);

        AiUsageDate reservation = service.reserveRequest(7L);

        assertNotNull(reservation.usageDate());
        verify(usageDailyService).insertUsageUserDaily(7L, reservation.usageDate());
        verify(globalDailyService).insertUsageGlobalDaily(reservation.usageDate());
    }

    @Test
    void recordsSuccessForUserAndPlatformWithTokenUsage() {
        LocalDate usageDate = LocalDate.of(2026, 8, 30);
        AgentUsage usage = new AgentUsage();
        usage.setInputTokens(120);
        usage.setOutputTokens(45);
        when(usageDailyService.recordSuccess(7L, usageDate, 120L, 45L)).thenReturn(true);
        when(globalDailyService.recordSuccess(usageDate, 120L, 45L)).thenReturn(true);

        service.recordSuccess(7L, new AiUsageDate(usageDate), usage);

        verify(usageDailyService).recordSuccess(7L, usageDate, 120L, 45L);
        verify(globalDailyService).recordSuccess(usageDate, 120L, 45L);
    }

    @Test
    void recordsFailureForUserAndPlatform() {
        LocalDate usageDate = LocalDate.of(2026, 8, 30);
        when(usageDailyService.recordFailure(7L, usageDate)).thenReturn(true);
        when(globalDailyService.recordFailure(usageDate)).thenReturn(true);

        service.recordFailure(7L, new AiUsageDate(usageDate));

        verify(usageDailyService).recordFailure(7L, usageDate);
        verify(globalDailyService).recordFailure(usageDate);
    }

    @Test
    void rejectsWhenUserDailyQuotaIsExhausted() {
        when(usageDailyService.updateRequestCount(eq(7L), any(LocalDate.class), eq(10), any(Date.class)))
                .thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserveRequest(7L));

        assertEquals(ErrorCode.AI_USER_DAILY_QUOTA_EXCEEDED.getCode(), exception.getCode());
        verifyNoInteractions(globalDailyService);
    }

    @Test
    void rejectsWhenPlatformDailyQuotaIsExhausted() {
        when(usageDailyService.updateRequestCount(eq(7L), any(LocalDate.class), eq(10), any(Date.class)))
                .thenReturn(true);
        when(globalDailyService.updateRequestCount(any(LocalDate.class), eq(100), any(Date.class)))
                .thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reserveRequest(7L));

        assertEquals(ErrorCode.AI_GLOBAL_DAILY_QUOTA_EXCEEDED.getCode(), exception.getCode());
    }
}
