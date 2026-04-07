package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.entity.CommodityType;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

@Data
public class CommodityTypeVO implements Serializable {

    /**
     * 商品分类 ID
     */
    private Long id;

    /**
     * 商品类别名称
     */
    private String typeName;

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


    private static final long serialVersionUID = 1L;

    /**
     * 封装类转对象
     *
     * @param commodityTypeVO
     * @return
     */
    public static CommodityType voToObj(CommodityTypeVO commodityTypeVO) {
        if (commodityTypeVO == null) {
            return null;
        }
        CommodityType commodityType = new CommodityType();
        BeanUtils.copyProperties(commodityTypeVO, commodityType);
        return commodityType;
    }

    /**
     * 对象转封装类
     *
     * @param commodityType
     * @return
     */
    public static CommodityTypeVO objToVo(CommodityType commodityType) {
        if (commodityType == null) {
            return null;
        }
        CommodityTypeVO commodityTypeVO = new CommodityTypeVO();
        BeanUtils.copyProperties(commodityType, commodityTypeVO);
        return commodityTypeVO;
    }
}
