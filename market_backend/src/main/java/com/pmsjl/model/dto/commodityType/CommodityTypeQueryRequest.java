package com.pmsjl.model.dto.commodityType;
import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询商品类别表请求
 *
 * @author 
 * @from <a href=""> 
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CommodityTypeQueryRequest extends PageRequest implements Serializable {

    /**
     * 商品分类 ID
     */
    private Long id;

    /**
     * 商品类别名称
     */
    private String typeName;

    private static final long serialVersionUID = 1L;
}