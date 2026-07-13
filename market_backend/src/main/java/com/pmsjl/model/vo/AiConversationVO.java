package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.enums.AiConversationSceneEnum;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** AI conversation item returned to the current user. */
@Data
public class AiConversationVO implements Serializable {
    /** 会话 ID。 */
    private Long id;

    /** 会话标题。 */
    private String title;

    /** 会话业务场景。 */
    private AiConversationSceneEnum scene;

    /** 当前会话持续生效的购买条件。 */
    private AiShoppingContext shoppingContext;

    /** 当前会话状态。 */
    private AiConversationStatusEnum status;

    /** 会话列表中展示的最后一条消息摘要。 */
    private String lastMessagePreview;

    /** 最后一条消息产生时间，用于会话列表排序。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastMessageTime;

    /** 会话创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
