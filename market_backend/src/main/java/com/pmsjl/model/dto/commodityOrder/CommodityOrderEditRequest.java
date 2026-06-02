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

//这里删除了paystatus，用户修改界面的前端实际没有支付状态的修改，实际也不应该有

    private static final long serialVersionUID = 1L;
}