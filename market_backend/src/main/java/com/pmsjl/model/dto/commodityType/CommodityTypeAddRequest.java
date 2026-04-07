package com.pmsjl.model.dto.commodityType;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品类别表请求
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
 */
@Data
public class CommodityTypeAddRequest implements Serializable {



    /**
     * 商品类别名称
     */
    private String typeName;


    private static final long serialVersionUID = 1L;
}