package com.pmsjl.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RedisConfig {
//    因为用到了jsonRedisSerializer，所以要导入jackson依赖
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory){
        // 创建RedisTemplate对象
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 设置连接工厂
        template.setConnectionFactory(connectionFactory);
        // 创建JSON序列化工具
        GenericJackson2JsonRedisSerializer jsonRedisSerializer =
                new GenericJackson2JsonRedisSerializer();
        // 设置Key的序列化
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        // 设置Value的序列化
        template.setValueSerializer(jsonRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);
        // 返回
        return template;
    }


    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            RedisConnectionDetails connectionDetails,
            RedisProperties redisProperties
    ) {
        return Redisson.create(createRedissonConfig(connectionDetails, redisProperties));
    }

    /**
     * Redisson 和 Spring Data Redis 必须复用同一份 Spring Boot 连接配置。
     * 这样环境变量、Redis URL、TLS、认证信息和数据库编号不会发生漂移。
     */
    static Config createRedissonConfig(
            RedisConnectionDetails connectionDetails,
            RedisProperties redisProperties
    ) {
        RedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("当前业务仅支持 Redis standalone 连接模式");
        }
        if (!StringUtils.hasText(standalone.getHost())) {
            throw new IllegalStateException("Redis host 未配置");
        }

        boolean sslEnabled = redisProperties.getSsl().isEnabled()
                || isRedissUrl(redisProperties.getUrl());
        String scheme = sslEnabled ? "rediss://" : "redis://";
        String host = formatHost(standalone.getHost());

        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(scheme + host + ":" + standalone.getPort())
                .setDatabase(standalone.getDatabase());

        if (StringUtils.hasText(connectionDetails.getUsername())) {
            singleServer.setUsername(connectionDetails.getUsername());
        }
        if (StringUtils.hasText(connectionDetails.getPassword())) {
            singleServer.setPassword(connectionDetails.getPassword());
        }
        if (StringUtils.hasText(redisProperties.getClientName())) {
            singleServer.setClientName(redisProperties.getClientName());
        }
        setTimeoutIfPresent(singleServer, redisProperties.getTimeout(), false);
        setTimeoutIfPresent(singleServer, redisProperties.getConnectTimeout(), true);
        return config;
    }

    private static boolean isRedissUrl(String redisUrl) {
        return StringUtils.hasText(redisUrl)
                && redisUrl.regionMatches(true, 0, "rediss://", 0, "rediss://".length());
    }

    private static String formatHost(String host) {
        if (host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))) {
            return "[" + host + "]";
        }
        return host;
    }

    private static void setTimeoutIfPresent(
            SingleServerConfig singleServer,
            Duration duration,
            boolean connectTimeout
    ) {
        if (duration == null) {
            return;
        }
        long timeoutMillis = duration.toMillis();
        if (timeoutMillis <= 0 || timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalStateException("Redis timeout 必须在 1 到 " + Integer.MAX_VALUE + " 毫秒之间");
        }
        if (connectTimeout) {
            singleServer.setConnectTimeout((int) timeoutMillis);
        } else {
            singleServer.setTimeout((int) timeoutMillis);
        }
    }
}
