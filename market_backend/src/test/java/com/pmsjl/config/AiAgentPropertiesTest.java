package com.pmsjl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAgentPropertiesTest {

    @Test
    void pendingTimeoutMustLeaveRoomForNetworkAndDatabaseWriteBack() {
        AiAgentProperties valid = properties(3_000L, 30_000L, 60_000L);
        AiAgentProperties invalid = properties(3_000L, 55_000L, 60_000L);

        assertDoesNotThrow(valid::validateTimeoutConfiguration);
        assertThrows(IllegalStateException.class, invalid::validateTimeoutConfiguration);
    }

    private static AiAgentProperties properties(long connectTimeout,
                                                long readTimeout,
                                                long pendingTimeout) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setConnectTimeoutMs(connectTimeout);
        properties.setReadTimeoutMs(readTimeout);
        properties.setPendingTimeoutMs(pendingTimeout);
        return properties;
    }
}
