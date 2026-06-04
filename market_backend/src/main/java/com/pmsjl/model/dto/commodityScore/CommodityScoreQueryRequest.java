package com.pmsjl.model.dto.commodityScore;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询商品评分表请求
 *
 * @author 
 * @from <a href=""> 
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CommodityScoreQueryRequest extends PageRequest implements Serializable {

    /**
     * 商品评分 ID
     */
    private Long id;

    /**
     * 商品 ID
     */
    private Long commodityId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 评分（0-5，星级评分）
     */
    private Integer score;

    private static final long serialVersionUID = 1L;
}