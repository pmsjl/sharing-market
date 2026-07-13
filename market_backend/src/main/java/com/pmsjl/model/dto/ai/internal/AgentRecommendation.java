package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Commodity recommendation returned by Python before Java hydration. */
@Data
public class AgentRecommendation implements Serializable {
    /** 被推荐的商品 ID。 */
    private Long commodityId;

    /** 商品与用户需求的匹配分，建议取值范围为 0 至 100。 */
    private Integer matchScore;

    /** 推荐该商品的主要理由。 */
    private String reason;

    /** 针对该商品需要额外核验的风险提示。 */
    private String riskTip;

    private static final long serialVersionUID = 1L;
}
