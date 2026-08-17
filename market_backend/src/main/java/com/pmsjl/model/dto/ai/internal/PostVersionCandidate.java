package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

@Data
public class PostVersionCandidate implements Serializable {
    private Long postId;
    private String sourceVersion;
    private static final long serialVersionUID = 1L;
}
