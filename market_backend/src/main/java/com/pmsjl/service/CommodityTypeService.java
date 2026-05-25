package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.commodityType.CommodityTypeEditRequest;
import com.pmsjl.model.dto.commodityType.CommodityTypeQueryRequest;
import com.pmsjl.model.dto.commodityType.CommodityTypeUpdateRequest;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.vo.CommodityTypeVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
public interface CommodityTypeService extends IService<CommodityType> {

    Long addCommodityType(String typeName);

    boolean deleteCommodityType(Long id);


    CommodityTypeVO getCommodityTypeVO(CommodityType commodityType);

    Page<CommodityType> listCommodityTypeByPage(CommodityTypeQueryRequest commodityTypeQueryRequest);

    Page<CommodityTypeVO> listCommodityTypeVOByPage(CommodityTypeQueryRequest commodityTypeQueryRequest);

    Boolean updateCommodityType(CommodityTypeUpdateRequest commodityTypeUpdateRequest);

    CommodityType getCommodityTypeVOById(Long id);
}
