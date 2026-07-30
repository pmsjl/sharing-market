package com.pmsjl.service;

import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.vo.CommoditySearchToolResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AiInternalToolService {
    CommoditySearchToolResponse searchCommodities(CommoditySearchToolRequest commoditySearchToolRequest, HttpServletRequest request);

    UserPreferenceToolResponse getMyPreferenceSignals(
            Long userId,
            HttpServletRequest request
    );
}
