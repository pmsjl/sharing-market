package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PostVersionValidationRequest implements Serializable {
    private List<PostVersionCandidate> candidates = new ArrayList<>();
    private static final long serialVersionUID = 1L;
}
