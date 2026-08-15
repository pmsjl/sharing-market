package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** RAG source candidate returned by Python; Java must verify it. */
@Data
public class AgentSource implements Serializable {
    /** 来源业务类型，例如商品、帖子或平台规则文档。 */
    private String sourceType;

    /** 原始业务来源 ID，不包含 sourceType 前缀。 */
    private String sourceId;

    /** RAG 索引文档 ID，固定由 sourceType 与 sourceId 定位。 */
    private String documentId;

    /** 适合展示给用户的来源标题。 */
    private String title;

    /** 本轮回答在该文档中实际使用的 chunk 级引用。 */
    private List<AgentCitation> citations = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
