package com.pmsjl.model.dto.ai;

import com.pmsjl.common.PageRequest;
import com.pmsjl.model.enums.AiConversationSceneEnum;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Pagination filters for the current user's AI conversations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiConversationQueryRequest extends PageRequest implements Serializable {

    /** 会话业务场景筛选条件。 */
    private AiConversationSceneEnum scene;

    /** 会话状态筛选条件。 */
    private AiConversationStatusEnum status;

    private static final long serialVersionUID = 1L;
}
