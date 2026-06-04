package com.pmsjl.model.dto.commodityScore;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品评分表请求
 *
 * @author 
 * @from <a href=""> 
 */
@Data
public class CommodityScoreAddRequest implements Serializable {


    /**
     * 商品 ID
     */
    private Long commodityId;



    /**
     * 评分（0-5，星级评分）
     */
    private Integer score;




    private static final long serialVersionUID = 1L;
}
