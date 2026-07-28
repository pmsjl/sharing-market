package com.pmsjl.model.vo;

import com.pmsjl.model.dto.ai.internal.AiCommoditySearchItem;
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
    private List<AiCommoditySearchItem> items = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
