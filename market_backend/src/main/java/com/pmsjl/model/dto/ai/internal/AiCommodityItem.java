package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** Privacy-safe commodity representation exposed to the Python Agent. */
@Data
public class AiCommodityItem implements Serializable {
    /** 商品 ID。 */
    private Long id;

    /** 商品名称。 */
    private String commodityName;

    /** 商品公开描述。 */
    private String commodityDescription;

    /** 商品主图地址。 */
    private String commodityAvatar;

    /** 商品成色描述。 */
    private String degree;

    /** 商品分类 ID。 */
    private Long commodityTypeId;

    /** 商品分类名称。 */
    private String commodityTypeName;

    /** 当前可购买库存数量。 */
    private Integer commodityInventory;

    /** 商品当前售价，单位为元。 */
    private BigDecimal price;

    /** 商品累计浏览次数。 */
    private Integer viewNum;

    /** 商品累计收藏次数。 */
    private Integer favourNum;

    private static final long serialVersionUID = 1L;
}
