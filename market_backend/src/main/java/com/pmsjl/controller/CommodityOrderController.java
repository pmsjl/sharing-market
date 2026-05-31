package com.pmsjl.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.commodity.CommodityAddRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderAddRequest;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpRequest;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-05-31
 */
@RestController
@RequestMapping("/commodityOrder")
@Slf4j
public class CommodityOrderController {
    @Autowired
    CommodityOrderService commodityOrderService;


    @PostMapping("/add")
    public Result<Long>addCommodityOrder(@RequestBody CommodityOrderAddRequest commodityOrderAddRequest, HttpServletRequest request){
        ThrowUtils.throwIf(commodityOrderAddRequest==null, ErrorCode.PARAMS_ERROR);
        CommodityOrder commodityOrder=new CommodityOrder();
        BeanUtil.copyProperties(commodityOrderAddRequest, commodityOrder);
        Long id =commodityOrderService.addCommodityOrder(commodityOrder,request);
        return ResultUtils.success(id);

    }

    @PostMapping("/delete")
    public Result<Boolean>deleteCommodityOrder(@RequestBody DeleteRequest deleteRequest,HttpServletRequest request){
        ThrowUtils.throwIf(deleteRequest==null,ErrorCode.PARAMS_ERROR);
        Boolean result=commodityOrderService.deleteCommodityOrder(deleteRequest,request);
        return ResultUtils.success(result);
    }

}
