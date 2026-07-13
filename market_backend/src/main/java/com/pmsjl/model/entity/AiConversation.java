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
 * AI conversation owned by a platform user.
 *
 * @TableName ai_conversation
 */
@TableName(value = "ai_conversation")
@Data
public class AiConversation implements Serializable {

    /** 会话主键，使用应用侧雪花 ID。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话所属的平台用户 ID。 */
    private Long userId;

    /** 会话标题，可由首条消息生成或后续更新。 */
    private String title;

    /** 会话业务场景，对应 AiConversationSceneEnum 的持久化值。 */
    private String scene;

    /**
     * 当前会话持续生效的购买条件 JSON，包括预算、使用场景、偏好和避雷项。
     */
    private String shoppingContext;

    /** 较早消息压缩后的模型记忆摘要，用于控制上下文长度。 */
    private String memorySummary;

    /** 会话状态，对应 AiConversationStatusEnum 的持久化值。 */
    private String status;

    /** 会话列表中展示的最后一条消息摘要。 */
    private String lastMessagePreview;

    /** 会话最后产生消息的时间，用于会话列表排序。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastMessageTime;

    /** 会话创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 会话最近更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 逻辑删除标记：0 表示正常，1 表示已删除。 */
    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
