package com.pmsjl.cycle;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.service.AiMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPendingMessageExpireJobTest {

    @Mock
    private AiMessageService aiMessageService;

    @Test
    void cycleJobUsesConfiguredTimeoutAndBoundedBatch() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setPendingTimeoutMs(60_000L);
        AiPendingMessageExpireJob job = new AiPendingMessageExpireJob(aiMessageService, properties);
        long beforeCall = System.currentTimeMillis();
        when(aiMessageService.expireStalePendingMessages(
                org.mockito.ArgumentMatchers.any(Date.class),
                org.mockito.ArgumentMatchers.eq(100))).thenReturn(2);

        job.expireStalePendingMessages();

        ArgumentCaptor<Date> expireBeforeCaptor = ArgumentCaptor.forClass(Date.class);
        verify(aiMessageService).expireStalePendingMessages(
                expireBeforeCaptor.capture(), org.mockito.ArgumentMatchers.eq(100));
        long expectedExpireTime = beforeCall - properties.getPendingTimeoutMs();
        assertTrue(expireBeforeCaptor.getValue().getTime() >= expectedExpireTime);
        assertTrue(expireBeforeCaptor.getValue().getTime()
                <= System.currentTimeMillis() - properties.getPendingTimeoutMs());
    }
}
