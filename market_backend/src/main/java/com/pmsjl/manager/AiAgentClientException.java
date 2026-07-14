package com.pmsjl.manager;

/** Java 调用 Python Agent 失败时携带的稳定错误信息。 */
public class AiAgentClientException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public AiAgentClientException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AiAgentClientException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
