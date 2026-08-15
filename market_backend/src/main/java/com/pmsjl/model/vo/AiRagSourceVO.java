package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Java-verified RAG source shown to the user in phase two. */
@Data
public class AiRagSourceVO implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** 原始业务来源 ID，不包含 sourceType 前缀。 */
    private String sourceId;

    /** RAG 索引文档 ID。 */
    private String documentId;

    /** 展示给用户的来源标题。 */
    private String title;

    /** 兼容升级前已保存的历史消息；新消息使用 citations。 */
    @Deprecated
    private String excerpt;

    /** 兼容升级前已保存的历史消息；新消息使用 citations。 */
    @Deprecated
    private String content;

    /** 本轮回答在该文档中实际使用的 chunk 级引用。 */
    private List<AiRagCitationVO> citations = new ArrayList<>();

    /** Java 校验后生成的站内跳转路径。 */
    private String targetPath;

    private static final long serialVersionUID = 1L;
}
