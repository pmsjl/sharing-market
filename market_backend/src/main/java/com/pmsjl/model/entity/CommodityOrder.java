package com.pmsjl.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author pmsjl
 * @since 2026-05-31
 */
@Data
@TableName("commodity_order")
public class CommodityOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    @TableField("userId")
    private Long userId;

    /**
     * 商品 ID
     */
    @TableField("commodityId")
    private Long commodityId;

    /**
     * 订单备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 购买数量
     */
    @TableField("buyNumber")
    private Integer buyNumber;

    /**
     * 订单总支付金额
     */
    @TableField("paymentAmount")
    private BigDecimal paymentAmount;

    /**
     * 0-未支付 1-已支付
     */
    @TableField("payStatus")
    private Byte payStatus;

    /**
     * 创建时间
     */
    @TableField("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableField("isDelete")
    private Byte isDelete;
}
