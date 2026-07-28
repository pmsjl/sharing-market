package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** Compact commodity representation returned by the search_commodities tool. */
@Data
public class AiCommoditySearchItem implements Serializable {
    /** 商品 ID。 */
    private Long id;

    /** 商品名称。 */
    private String commodityName;

    /** 商品公开描述。 */
    private String commodityDescription;

    /** 商品成色描述。 */
    private String degree;

    /** 商品分类名称。 */
    private String commodityTypeName;

    /** 当前可购买库存数量。 */
    private Integer commodityInventory;

    /** 商品当前售价，单位为元。 */
    private BigDecimal price;

    private static final long serialVersionUID = 1L;
}
