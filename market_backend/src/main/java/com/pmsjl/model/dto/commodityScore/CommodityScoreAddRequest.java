package com.pmsjl.model.dto.commodityScore;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品评分表请求
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
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
