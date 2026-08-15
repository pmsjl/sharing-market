package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;

/** A validated chunk-level citation displayed under one RAG source. */
@Data
public class AiRagCitationVO implements Serializable {
    private String chunkId;
    private String section;
    private String excerpt;
    private String content;

    private static final long serialVersionUID = 1L;
}
