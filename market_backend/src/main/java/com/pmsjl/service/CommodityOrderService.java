package com.pmsjl.service;

import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.entity.CommodityOrder;
import com.baomidou.mybatisplus.extension.service.IService;
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
}
