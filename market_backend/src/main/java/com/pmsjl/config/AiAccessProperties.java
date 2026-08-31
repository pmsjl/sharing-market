package com.pmsjl.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Data
@Component
@ConfigurationProperties(prefix = "ai.access")
public class AiAccessProperties {
    private int userDailyLimit = 10;
    private int globalDailyLimit = 100;
    private String timezone = "Asia/Shanghai";
    private ZoneId zoneId;

    @PostConstruct
    public void validate() {
        if (userDailyLimit <= 0 || globalDailyLimit <= 0) {
            throw new IllegalStateException("AI 日配额必须为正整数");
        }
        if (globalDailyLimit < userDailyLimit) {
            throw new IllegalStateException("AI 平台日配额不能小于用户日配额");
        }
        try {
            zoneId = ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI_QUOTA_TIMEZONE 不是有效时区", exception);
        }
    }
}
