package com.pmsjl.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderAddRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderEditRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderQueryRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderUpdateRequest;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityOrderVO;
import com.pmsjl.service.CommodityOrderService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 前端控制器
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
    @Autowired
    UserService userService;


    @PostMapping("/add")
    public Result<Long> addCommodityOrder(@RequestBody CommodityOrderAddRequest commodityOrderAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityOrderAddRequest == null, ErrorCode.PARAMS_ERROR);
        CommodityOrder commodityOrder = new CommodityOrder();
        BeanUtil.copyProperties(commodityOrderAddRequest, commodityOrder);
        Long id = commodityOrderService.addCommodityOrder(commodityOrder, request);
        return ResultUtils.success(id);

    }

    @PostMapping("/delete")
    public Result<Boolean> deleteCommodityOrder(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = commodityOrderService.deleteCommodityOrder(deleteRequest, request);
        return ResultUtils.success(result);
    }


    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public Result<Boolean> updateCommodityOrder(@RequestBody CommodityOrderUpdateRequest commodityOrderUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityOrderUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        CommodityOrder commodityOrder = new CommodityOrder();
        BeanUtil.copyProperties(commodityOrderUpdateRequest, commodityOrder);
        Boolean result = commodityOrderService.updateCommodityOrder(commodityOrder);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public Result<CommodityOrderVO> getCommodityOrderVOById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CommodityOrderVO commodityOrderVO = commodityOrderService.getCommodityOrderVOById(id, request);
        return ResultUtils.success(commodityOrderVO);
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page")
    public Result<Page<CommodityOrder>> listCommodityOrderByPage(@RequestBody CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityOrderQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityOrder> page = commodityOrderService.listCommodityOrderByPage(commodityOrderQueryRequest, request);
        return ResultUtils.success(page);

    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page/vo")
    public Result<Page<CommodityOrderVO>> listCommodityOrderVOByPage(@RequestBody CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityOrderQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityOrderVO> page = commodityOrderService.listCommodityOrderVOByPage(commodityOrderQueryRequest, request);
        return ResultUtils.success(page);
    }

    /***
     * 普通用户只能查询自己的订单
     * @param commodityOrderQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public Result<Page<CommodityOrderVO>> listMyCommodityOrderVOByPage(@RequestBody CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityOrderQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityOrderVO>page=commodityOrderService.listMyCommodityOrderVOByPage(commodityOrderQueryRequest,request);
        return ResultUtils.success(page);

    }

    @PostMapping("/edit")
    public Result<Boolean>editCommodityOrder(@RequestBody CommodityOrderEditRequest commodityOrderEditRequest,HttpServletRequest request){
        ThrowUtils.throwIf(commodityOrderEditRequest==null,ErrorCode.PARAMS_ERROR);
        Boolean result=commodityOrderService.editCommodityOrder(commodityOrderEditRequest,request);
        return ResultUtils.success(result);
    }


    @GetMapping("/getCommodityOrderHeatmapData")
    public Result<List<Map<String, Object>>> getCommodityOrderHeatmapData(
            @RequestParam Integer payStatus,
            HttpServletRequest request) {
        //这里在原有基础上删除了userid，前后端都进行了修改，防止直接访问传入别人的id
        User loginUser = userService.getLoginUser(request);
        // 构建查询条件
        CommodityOrderQueryRequest queryRequest = new CommodityOrderQueryRequest();
        queryRequest.setUserId(loginUser.getId());
        queryRequest.setPayStatus(payStatus);

        // 查询符合条件的订单
        List<Map<String, Object>>list=commodityOrderService.getCommodityOrderHeatmapData(queryRequest);
        return ResultUtils.success(list);
    }
}
