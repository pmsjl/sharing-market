package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Successful response from the get_commodity_detail tool. */
@Data
public class CommodityDetailToolResponse implements Serializable {
    /** 本次工具调用的全链路请求标识。 */
    private String requestId;

    /** 已按 AI 数据边界裁剪的商品详情。 */
    private AiCommodityItem item;

    /** 允许 Agent 使用的卖家展示名称。 */
    private String sellerName;

    /** 商品发布时间，使用约定的日期时间字符串格式。 */
    private String createTime;

    private static final long serialVersionUID = 1L;
}
