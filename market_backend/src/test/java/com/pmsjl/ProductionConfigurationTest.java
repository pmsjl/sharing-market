package com.pmsjl;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "DB_URL=jdbc:mysql://db.test.invalid:3306/trade",
        "DB_USERNAME=test-user",
        "DB_PASSWORD=test-password",
        "REDIS_HOST=redis.test.invalid",
        "REDIS_PORT=6380",
        "REDIS_USERNAME=test-redis-user",
        "REDIS_PASSWORD=test-redis-password",
        "REDIS_SSL_ENABLED=true",
        "OSS_ACCESS_KEY=test-access-key",
        "OSS_SECRET_KEY=test-secret-key",
        "OSS_ENDPOINT=https://oss.test.invalid",
        "OSS_BUCKET=test-bucket",
        "OSS_HOST=https://assets.test.invalid",
        "AI_AGENT_BASE_URL=https://agent.test.invalid",
        "AI_AGENT_INTERNAL_TOKEN=test-internal-token",
        "CORS_ALLOWED_ORIGIN_PATTERNS=https://market.test.invalid"
})
class ProductionConfigurationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private RedisConnectionDetails redisConnectionDetails;

    @MockBean
    private OSS ossClient;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void productionProfileBindsDeploymentEnvironmentVariables() {
        assertThat(environment.getProperty("server.port")).isEqualTo("8081");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis.test.invalid");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6380");
        assertThat(environment.getProperty("spring.data.redis.username")).isEqualTo("test-redis-user");
        assertThat(environment.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("true");
        assertThat(redisConnectionDetails.getStandalone().getHost()).isEqualTo("redis.test.invalid");
        assertThat(redisConnectionDetails.getStandalone().getPort()).isEqualTo(6380);
        assertThat(redisConnectionDetails.getUsername()).isEqualTo("test-redis-user");
        assertThat(redisConnectionDetails.getPassword()).isEqualTo("test-redis-password");
        assertThat(environment.getProperty("oss.client.endpoint")).isEqualTo("https://oss.test.invalid");
        assertThat(environment.getProperty("ai.agent.base-url")).isEqualTo("https://agent.test.invalid");
        assertThat(environment.getProperty("app.cors.allowed-origin-patterns"))
                .isEqualTo("https://market.test.invalid");
    }
}
