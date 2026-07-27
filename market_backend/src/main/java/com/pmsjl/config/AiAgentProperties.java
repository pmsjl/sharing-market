package com.pmsjl.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 调用本地 Python Agent 时使用的连接配置。
 *
 * <p>内部 Token 只从环境变量或未提交的本地配置读取，不能写入代码。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {

    private static final long PENDING_WRITE_BACK_MARGIN_MS = 5_000L;

    /** Python FastAPI 服务根地址，例如 http://127.0.0.1:8103。 */
    private String baseUrl;

    /** Java 与 Python 间的服务身份校验 Token。 */
    private String internalToken;

    /** 建立到 Python 服务连接的最长等待时间（毫秒）。 */
    private long connectTimeoutMs;

    /** 等待 Python 和模型返回结果的最长时间（毫秒）。 */
    private long readTimeoutMs;

    /** 助手消息允许保持 PENDING 的最长时间（毫秒）。 */
    private long pendingTimeoutMs;

    @PostConstruct
    public void validateTimeoutConfiguration() {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0 || pendingTimeoutMs <= 0) {
            throw new IllegalStateException("AI Agent 超时配置必须为正整数");
        }
        long minimumPendingTimeout;
        try {
            minimumPendingTimeout = Math.addExact(
                    Math.addExact(connectTimeoutMs, readTimeoutMs),
                    PENDING_WRITE_BACK_MARGIN_MS
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("AI Agent 超时配置超出有效范围", exception);
        }
        if (pendingTimeoutMs <= minimumPendingTimeout) {
            throw new IllegalStateException(
                    "AI_AGENT_PENDING_TIMEOUT_MS 必须大于连接超时、读取超时与 5 秒回写余量之和");
        }
    }
}
