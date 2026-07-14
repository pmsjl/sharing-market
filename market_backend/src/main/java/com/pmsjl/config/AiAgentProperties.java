package com.pmsjl.config;

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

    /** Python FastAPI 服务根地址，例如 http://127.0.0.1:8103。 */
    private String baseUrl = "http://127.0.0.1:8103";

    /** Java 与 Python 间的服务身份校验 Token。 */
    private String internalToken;

    /** 建立到 Python 服务连接的最长等待时间（毫秒）。 */
    private long connectTimeoutMs = 3000;

    /** 等待 Python 和模型返回结果的最长时间（毫秒）。 */
    private long readTimeoutMs = 30000;
}
