package com.pmsjl.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * A user or assistant message in an AI conversation. Tool calls are stored in
 * the append-only ai_agent_trace table.
 *
 * @TableName ai_message
 */
@TableName(value = "ai_message")
@Data
public class AiMessage implements Serializable {

    /** 消息主键，使用应用侧雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息所属的 AI 会话 ID。 */
    private Long conversationId;

    /** 会话所属用户 ID，用于归属校验和查询隔离。 */
    private Long userId;

    /** 消息在会话内的稳定顺序号。 */
    private Integer sequenceNo;

    /** 消息角色，对应 AiMessageRoleEnum 的持久化值。 */
    private String role;

    /** 用户输入或助手 Markdown 回复的文本内容。 */
    private String content;

    /**
     * Agent 返回的结构化内容 JSON，仅助手消息通常包含该字段。
     */
    private String structuredContent;

    /** 生成助手消息时实际使用的模型名称。 */
    private String modelName;

    /** 消息处理状态，对应 AiMessageStatusEnum 的持久化值。 */
    private String status;

    /** 关联本轮请求的全链路请求标识，用于幂等和审计。 */
    private String requestId;

    /** 本轮模型调用消耗的输入 token 数。 */
    private Integer inputTokens;

    /** 本轮模型调用生成的输出 token 数。 */
    private Integer outputTokens;

    /** 本轮 AI 请求总耗时，单位为毫秒。 */
    private Integer latencyMs;

    /** 生成失败时记录的稳定错误码。 */
    private String errorCode;

    /** 失败消息是否允许用户重试。 */
    private Boolean retryable;

    /** 消息创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 消息最近更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 逻辑删除标记：0 表示正常，1 表示已删除。 */
    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
