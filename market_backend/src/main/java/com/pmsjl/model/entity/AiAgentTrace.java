package com.pmsjl.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Audit record for one Agent tool call.
 *
 * @TableName ai_agent_trace
 */
@TableName(value = "ai_agent_trace")
@Data
public class AiAgentTrace implements Serializable {

    /** 工具调用轨迹主键，使用应用侧雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联一次 AI 请求的全链路请求标识。 */
    private String requestId;

    /** 工具调用所属的会话 ID。 */
    private Long conversationId;

    /** 工具调用最终服务的助手消息 ID。 */
    private Long messageId;

    /** 被 Agent 调用的工具名称。 */
    private String toolName;

    /**
     * 发送给工具的原始 JSON 参数。
     */
    private String toolArguments;

    /**
     * 工具结果的脱敏 JSON 或文本摘要。
     */
    private String toolResultSummary;

    /** 工具调用状态，对应 AiToolStatusEnum 的持久化值。 */
    private String status;

    /** 工具调用耗时，单位为毫秒。 */
    private Integer latencyMs;

    /** 工具调用失败时记录的错误说明。 */
    private String errorMessage;

    /** 轨迹记录创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
