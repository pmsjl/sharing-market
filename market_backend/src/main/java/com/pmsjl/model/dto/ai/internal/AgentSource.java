package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** RAG source candidate returned by Python; Java must verify it. */
@Data
public class AgentSource implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** 来源在 Java 业务系统中的记录 ID。 */
    private Long sourceId;

    /** 适合展示给用户的来源标题。 */
    private String title;

    /** 支撑回答的简短内容摘录。 */
    private String excerpt;

    private static final long serialVersionUID = 1L;
}
