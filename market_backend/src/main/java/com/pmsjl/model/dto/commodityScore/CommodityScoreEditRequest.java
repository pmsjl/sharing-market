package com.pmsjl.model.dto.commodityScore;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑商品评分表请求
 *
 * @author 
 * @from <a href=""> 
 */
@Data
public class CommodityScoreEditRequest implements Serializable {

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