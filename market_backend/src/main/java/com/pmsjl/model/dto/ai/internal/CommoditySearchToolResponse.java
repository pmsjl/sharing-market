package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Successful response from the internal commodity search tool. */
@Data
public class CommoditySearchToolResponse implements Serializable {
    /** 本次工具调用的全链路请求标识。 */
    private String requestId;

    /** 符合筛选条件的商品总数。 */
    private Long matchedCount;

    /** 当前返回给 Agent 的商品结果列表。 */
    private List<AiCommodityItem> items = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
