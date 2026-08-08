package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;

/** Java-verified RAG source shown to the user in phase two. */
@Data
public class AiRagSourceVO implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** GUIDE 等受控来源可使用非数字 ID。 */
    private String sourceId;

    /** 展示给用户的来源标题。 */
    private String title;

    /** 支撑回答的简短内容摘录。 */
    private String excerpt;

    /** 回答实际引用的完整知识片段；历史消息可以为空。 */
    private String content;

    /** Java 校验后生成的站内跳转路径。 */
    private String targetPath;

    private static final long serialVersionUID = 1L;
}
