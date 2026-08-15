package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Chunk-level citation derived by Python from a validated RAG result. */
@Data
public class AgentCitation implements Serializable {
    /** 本轮实际使用的知识 chunk ID。 */
    private String chunkId;

    /** chunk 所属章节；没有明确章节时为空。 */
    private String section;

    /** 支撑回答的简短内容摘录。 */
    private String excerpt;

    /** 回答实际引用的完整知识片段。 */
    private String content;

    private static final long serialVersionUID = 1L;
}
