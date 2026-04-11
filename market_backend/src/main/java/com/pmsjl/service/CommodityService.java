package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.CommodityVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
public interface CommodityService extends IService<Commodity> {

    Long addCommodity(Commodity commodity, HttpServletRequest request);
    void validCommodity(Commodity commodity);

    Boolean deleteCommodity(Long id);

    Boolean updateCommodity(Commodity commodity);

    CommodityVO getCommodityVOById(Long id);

    Page<Commodity> listCommodityByPage(CommodityQueryRequest commodityQueryRequest);
}
