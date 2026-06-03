package com.pmsjl.model.dto.commodity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author 程序员小白条
 * @date 2025/3/12 14:16
 * @gitee https://gitee.com/falle22222n-leaves/vue_-book-manage-system
 */
@Data
public class BuyCommodityRequest implements Serializable {

    /**
     * 商品 ID
     */
    private Long commodityId;


    /**
     * 购买商品的数量
     */
    private Integer buyNumber;


    /**
     * 订单备注
     */
    private String remark;

    private static final long serialVersionUID = 1L;
}
