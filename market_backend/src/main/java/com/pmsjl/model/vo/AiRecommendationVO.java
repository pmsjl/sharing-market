package com.pmsjl.model.vo;

import lombok.Data;

import java.io.Serializable;

/** Recommendation after Java hydrates and validates the commodity. */
@Data
public class AiRecommendationVO implements Serializable {
    /** Java 根据商品 ID 查询并校验后的实时商品信息。 */
    private CommodityVO commodity;

    /** 商品与用户需求的匹配分，建议取值范围为 0 至 100。 */
    private Integer matchScore;

    /** 推荐该商品的主要理由。 */
    private String reason;

    /** 购买该商品前需要额外核验的风险提示。 */
    private String riskTip;

    private static final long serialVersionUID = 1L;
}
