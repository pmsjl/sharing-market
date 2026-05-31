package com.pmsjl.model.dto.commodityOrder;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑商品订单表请求
 *
 * @author 
 * @from <a href=""> 
 */
@Data
public class CommodityOrderEditRequest implements Serializable {
    /**
     * 订单 ID
     */
    private Long id;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 0-未支付 1-已支付
     */
    private Integer payStatus;

    private static final long serialVersionUID = 1L;
}