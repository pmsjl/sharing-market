package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.model.enums.AiMessageStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** User-visible message in an AI conversation. */
@Data
public class AiMessageVO implements Serializable {
    /** 消息 ID。 */
    private Long id;

    /** 消息在会话内的稳定顺序号。 */
    private Integer sequenceNo;

    /** 消息角色，用于区分用户消息和助手消息。 */
    private AiMessageRoleEnum role;

    /** 用户输入或助手 Markdown 回复的文本内容。 */
    private String content;

    /** 助手消息携带的结构化推荐、建议和来源内容。 */
    private AiStructuredContentVO structuredContent;

    /** 消息处理状态。 */
    private AiMessageStatusEnum status;

    /** 消息生成失败时的稳定错误码。 */
    private String errorCode;

    /** 失败消息是否允许用户重试。 */
    private Boolean retryable;

    /** 消息创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
