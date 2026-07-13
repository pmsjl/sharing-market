package com.pmsjl.model.dto.ai;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional structured shopping constraints persisted with an AI conversation.
 */
@Data
public class AiShoppingContext implements Serializable {

    /** 可接受的最低预算，单位为元。 */
    private BigDecimal budgetMin;

    /** 可接受的最高预算，单位为元。 */
    private BigDecimal budgetMax;

    /** 商品的主要使用场景，例如学习、通勤或宿舍使用。 */
    private String usageScene;

    /** 用户偏好的品牌、功能、成色等标签。 */
    private List<String> preferenceTags = new ArrayList<>();

    /** 用户明确希望避开的商品特征或风险项。 */
    private List<String> avoidances = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
