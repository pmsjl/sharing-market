package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Python 按已校验检索结果生成的 Post 候选。 */
@Data
public class AgentRelatedPostCandidate implements Serializable {
    private Long postId;
    private String sourceVersion;
    private static final long serialVersionUID = 1L;
}
