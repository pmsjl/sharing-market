package com.pmsjl.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 
 * @TableName commodity_order
 */
@TableName(value ="commodity_order")
@Data
public class CommodityOrder implements Serializable {
    /**
     * 订单 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 商品 ID
     */
    private Long commodityId;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 购买数量
     */
    private Integer buyNumber;

    /**
     * 订单总支付金额
     */
    private BigDecimal paymentAmount;

    /**
     * 0-未支付 1-已支付 2-已过期
     */
    private Integer payStatus;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;
    //加上这个@TableLogic之后它之后的mybatisplus查询语句都会自动带上isDelete=0，无需手动添加，
    // 但是自定义sql语句还是需要手动添加的

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
