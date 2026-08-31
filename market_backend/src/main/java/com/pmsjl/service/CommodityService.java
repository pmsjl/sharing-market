package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.commodity.BuyCommodityRequest;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.dto.commodityOrder.PayCommodityOrderRequest;
import com.pmsjl.model.entity.Commodity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.CommodityVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

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

    Map<String, Object> createOrderAndTryPay(BuyCommodityRequest buyCommodityRequest, HttpServletRequest request);

    void validCommodity(Commodity commodity);

    Boolean deleteCommodity(Long id, HttpServletRequest request);

    Boolean updateCommodity(Commodity commodity);

    CommodityVO getCommodityVOById(Long id, HttpServletRequest request);

    Page<CommodityVO> listCommodityVOByPage(CommodityQueryRequest commodityQueryRequest);

    Page<CommodityVO> listMyCommodityVOByPage(CommodityQueryRequest commodityQueryRequest, HttpServletRequest request);

    Boolean payPendingOrder(PayCommodityOrderRequest payRequest, HttpServletRequest request);

    void validateCommodityExists(Long commodityId);

    }
