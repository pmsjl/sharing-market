package com.pmsjl.manager;

/** Java 调用 Python Agent 失败时携带的稳定错误信息。 */
public class AiAgentClientException extends RuntimeException {

    /** Agent 内部失败的字符串标识，不是项目通用的整数型 ErrorCode。 */
    private final String agentErrorKey;
    private final boolean retryable;

    public AiAgentClientException(String agentErrorKey, String message, boolean retryable) {
        super(message);
        this.agentErrorKey = agentErrorKey;
        this.retryable = retryable;
    }

    public AiAgentClientException(String agentErrorKey, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.agentErrorKey = agentErrorKey;
        this.retryable = retryable;
    }

    public String getAgentErrorKey() {
        return agentErrorKey;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
