package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiToolStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One tool-call trace returned by Python for Java persistence. */
@Data
public class AgentToolTrace implements Serializable {
    /** Agent 调用的工具名称。 */
    private String toolName;

    /** 调用工具时使用的结构化参数。 */
    private Map<String, Object> toolArguments = new LinkedHashMap<>();

    /** 工具结果的脱敏摘要，不保存不必要的完整业务数据。 */
    private Object toolResultSummary;

    /** 本次工具调用的执行状态。 */
    private AiToolStatusEnum status;

    /** 工具调用耗时，单位为毫秒。 */
    private Integer latencyMs;

    /** 工具调用失败时的错误说明。 */
    private String errorMessage;

    private static final long serialVersionUID = 1L;
}
