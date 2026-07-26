package com.pmsjl.controller;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.vo.CommoditySearchToolResponse;
import com.pmsjl.service.AiInternalToolService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpRequest;

// TODO Internal AI tool controller placeholder. TODO: implement read-only tools.
///api/internal/ai/tools/commodities/search
@RestController
@RequestMapping("internal/ai/tools")
public class AiInternalToolController {
    @Autowired
    AiInternalToolService aiInternalToolService;
    @PostMapping("/commodities/search")
    public CommoditySearchToolResponse searchCommodities(@RequestBody CommoditySearchToolRequest commoditySearchToolRequest, HttpServletRequest request){
        ThrowUtils.throwIf(commoditySearchToolRequest==null, ErrorCode.PARAMS_ERROR);
        CommoditySearchToolResponse response=aiInternalToolService.searchCommodities(commoditySearchToolRequest,request);
        return response;

    }
}