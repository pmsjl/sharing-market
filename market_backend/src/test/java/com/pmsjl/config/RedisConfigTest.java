package com.pmsjl.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    @Test
    void redissonUsesTheSameStandaloneSettingsAsSpringDataRedis() {
        RedisConnectionDetails connectionDetails = mock(RedisConnectionDetails.class);
        when(connectionDetails.getStandalone())
                .thenReturn(RedisConnectionDetails.Standalone.of("redis.internal", 6380, 4));
        when(connectionDetails.getUsername()).thenReturn("app-user");
        when(connectionDetails.getPassword()).thenReturn("secret");

        RedisProperties properties = new RedisProperties();
        properties.getSsl().setEnabled(true);
        properties.setTimeout(Duration.ofSeconds(7));
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setClientName("market-backend");

        Config config = RedisConfig.createRedissonConfig(connectionDetails, properties);
        SingleServerConfig singleServer = config.useSingleServer();

        assertThat(singleServer.getAddress()).isEqualTo("rediss://redis.internal:6380");
        assertThat(singleServer.getDatabase()).isEqualTo(4);
        assertThat(singleServer.getUsername()).isEqualTo("app-user");
        assertThat(singleServer.getPassword()).isEqualTo("secret");
        assertThat(singleServer.getTimeout()).isEqualTo(7_000);
        assertThat(singleServer.getConnectTimeout()).isEqualTo(3_000);
        assertThat(singleServer.getClientName()).isEqualTo("market-backend");
    }

    @Test
    void redissUrlAlsoEnablesTlsForRedisson() {
        RedisConnectionDetails connectionDetails = mock(RedisConnectionDetails.class);
        when(connectionDetails.getStandalone())
                .thenReturn(RedisConnectionDetails.Standalone.of("2001:db8::8", 6379, 0));

        RedisProperties properties = new RedisProperties();
        properties.setUrl("rediss://2001:db8::8:6379");

        Config config = RedisConfig.createRedissonConfig(connectionDetails, properties);

        assertThat(config.useSingleServer().getAddress()).isEqualTo("rediss://[2001:db8::8]:6379");
    }
}
