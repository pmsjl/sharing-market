package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiMessageRoleEnum;
import lombok.Data;

import java.io.Serializable;

/** Recent successful message passed to the Python Agent. */
@Data
public class AgentHistoryMessage implements Serializable {
    /** 历史消息角色，用于区分用户输入和助手回复。 */
    private AiMessageRoleEnum role;

    /** 历史消息的纯文本内容。 */
    private String content;

    private static final long serialVersionUID = 1L;
}
