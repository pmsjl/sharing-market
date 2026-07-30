package com.pmsjl.service;

import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;

public interface AiUserPreferenceService {

    UserPreferenceToolResponse buildPreferenceProfile(
            String requestId,
            Long userId
    );
}
