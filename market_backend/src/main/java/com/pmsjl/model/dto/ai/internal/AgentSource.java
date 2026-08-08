package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** RAG source candidate returned by Python; Java must verify it. */
@Data
public class AgentSource implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** GUIDE 等受控来源可使用非数字 ID。 */
    private String sourceId;

    /** 适合展示给用户的来源标题。 */
    private String title;

    /** 支撑回答的简短内容摘录。 */
    private String excerpt;

    /** 回答实际引用的完整知识片段；历史消息可以为空。 */
    private String content;

    private static final long serialVersionUID = 1L;
}
