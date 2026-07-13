package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.enums.AiToolStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Administrator-facing, redacted Agent tool trace. */
@Data
public class AiAgentTraceVO implements Serializable {
    /** 工具调用轨迹 ID。 */
    private Long id;

    /** 关联一次 AI 请求的全链路请求标识。 */
    private String requestId;

    /** 工具调用所属会话 ID。 */
    private Long conversationId;

    /** 工具调用最终服务的助手消息 ID。 */
    private Long messageId;

    /** 被 Agent 调用的工具名称。 */
    private String toolName;

    /** 允许管理员查看的工具调用参数。 */
    private Map<String, Object> toolArguments = new LinkedHashMap<>();

    /** 已脱敏的工具结果摘要。 */
    private Object toolResultSummary;

    /** 工具调用状态。 */
    private AiToolStatusEnum status;

    /** 工具调用耗时，单位为毫秒。 */
    private Integer latencyMs;

    /** 工具调用失败时的错误说明。 */
    private String errorMessage;

    /** 工具调用轨迹创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
