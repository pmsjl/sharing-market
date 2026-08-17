package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PostVersionValidationResponse implements Serializable {
    private String requestId;
    private List<PostVersionCandidate> validCandidates = new ArrayList<>();
    private static final long serialVersionUID = 1L;
}
