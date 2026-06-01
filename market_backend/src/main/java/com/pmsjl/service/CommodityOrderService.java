package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderQueryRequest;
import com.pmsjl.model.entity.CommodityOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.CommodityOrderVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-05-31
 */
public interface CommodityOrderService extends IService<CommodityOrder> {

    Long addCommodityOrder(CommodityOrder commodityOrder, HttpServletRequest request);

    Boolean deleteCommodityOrder(DeleteRequest deleteRequest, HttpServletRequest request);

    Boolean updateCommodityOrder(CommodityOrder commodityOrder);

    CommodityOrderVO getCommodityOrderVOById(Long id, HttpServletRequest request);

    Page<CommodityOrder> listCommodityOrderByPage(CommodityOrderQueryRequest commodityOrderService, HttpServletRequest request);

}
