package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.dto.ai.AiShoppingContext;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Request sent by Java to POST /agent/v1/runs. */
@Data
public class AgentRunRequest implements Serializable {
    /** 当前登录用户 ID，由 Java 鉴权后提供。 */
    private Long userId;

    /** 当前 AI 会话 ID。 */
    private Long conversationId;

    /** 用户本轮发送的自然语言消息。 */
    private String message;

    /** 当前会话持续生效的结构化购买条件。 */
    private AiShoppingContext shoppingContext;

    /** Java 保存的较早对话摘要，用于控制上下文长度。 */
    private String memorySummary;

    /** 传给 Agent 的近期成功消息列表，按会话顺序排列。 */
    private List<AgentHistoryMessage> history = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
