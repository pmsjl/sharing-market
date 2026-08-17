package com.pmsjl.service;

import com.pmsjl.model.dto.ai.internal.PostRagSnapshotResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AiRagSnapshotService {

    PostRagSnapshotResponse listPostSnapshots(
            Long afterId,
            Integer limit,
            HttpServletRequest request
    );
}
