package com.pmsjl.model.dto.commodity;


import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CommodityEditRequest implements Serializable {


    /**
     * 商品 ID
     */
    private Long id;

    /**
     * 商品名称
     */
    private String commodityName;

    /**
     * 商品简介
     */
    private String commodityDescription;

    /**
     * 商品封面图
     */
    private String commodityAvatar;

    /**
     * 商品新旧程度（例如 9成新）
     */
    private String degree;

    /**
     * 商品分类 ID
     */
    private Long commodityTypeId;

    /**
     * 管理员 ID （某人创建该商品）
     */
    private Long adminId;

    /**
     * 是否上架（默认0未上架，1已上架）
     */
    private Integer isListed;

    /**
     * 商品数量（默认0）
     */
    private Integer commodityInventory;

    /**
     * 商品价格
     */
    private BigDecimal price;

//这里相较于管理员的更新删除了收藏量和浏览量的更新，因为不准备大改前端，所以尽可能在不变前端的基础上，直接不接受这两个变量

    private static final long serialVersionUID = 1L;
}