package com.pmsjl.model.dto.commodityType;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品类别表请求
 *
 * @author 
 * @from <a href=""> 
 */
@Data
public class CommodityTypeAddRequest implements Serializable {

    /**
     * 商品类别名称
     */
    private String typeName;


    private static final long serialVersionUID = 1L;
}