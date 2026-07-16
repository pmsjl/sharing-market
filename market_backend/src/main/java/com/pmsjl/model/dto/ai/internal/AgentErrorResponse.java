package com.pmsjl.model.dto.ai.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/** Standard internal error returned by Java or Python AI services. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentErrorResponse implements Serializable {
    /** 本次 Agent 调用的全链路请求标识。 */
    private String requestId;

    /** Agent 内部失败的稳定字符串标识，例如 AI_MODEL_TIMEOUT。 */
    private String agentErrorKey;

    /** 面向调用方的错误说明，不应包含敏感内部信息。 */
    private String message;

    /** 当前错误是否适合使用相同请求重新尝试。 */
    private Boolean retryable;

    private static final long serialVersionUID = 1L;
}
