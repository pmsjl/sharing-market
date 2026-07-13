package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiCommoditySortEnum;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Filters accepted by the internal search_commodities tool. */
@Data
public class CommoditySearchToolRequest implements Serializable {
    /** 商品名称或描述的检索关键词。 */
    private String keyword;

    /** 限定检索的商品分类 ID 列表；为空表示不限制分类。 */
    private List<Long> categoryIds = new ArrayList<>();

    /** 商品最低价格，单位为元。 */
    private BigDecimal minPrice;

    /** 商品最高价格，单位为元。 */
    private BigDecimal maxPrice;

    /** 允许的商品成色列表。 */
    private List<String> degrees = new ArrayList<>();

    /** 本次检索需要排除的商品 ID 列表。 */
    private List<Long> excludeCommodityIds = new ArrayList<>();

    /** 结果排序方式，默认按相关度排序。 */
    private AiCommoditySortEnum sortBy = AiCommoditySortEnum.RELEVANCE;

    /** 最多返回的商品数量，默认 10 条。 */
    private Integer limit = 10;

    private static final long serialVersionUID = 1L;
}
