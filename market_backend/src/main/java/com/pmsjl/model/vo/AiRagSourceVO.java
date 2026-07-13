package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;

/** Java-verified RAG source shown to the user in phase two. */
@Data
public class AiRagSourceVO implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** 来源在 Java 业务系统中的记录 ID。 */
    private Long sourceId;

    /** 展示给用户的来源标题。 */
    private String title;

    /** 支撑回答的简短内容摘录。 */
    private String excerpt;

    /** Java 校验后生成的站内跳转路径。 */
    private String targetPath;

    private static final long serialVersionUID = 1L;
}
