package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodity.CommodityAddRequest;
import com.pmsjl.model.dto.commodity.CommodityEditRequest;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.dto.commodity.CommodityUpdateRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@RestController
@RequestMapping("/commodity")
public class CommodityController {
    @Autowired
    private CommodityService commodityService;

    /***
     * 添加商品
     * @param commodityAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public Result<Long> addCommodity(@RequestBody CommodityAddRequest commodityAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityAddRequest == null, ErrorCode.PARAMS_ERROR);
        Commodity commodity = new Commodity();
        BeanUtils.copyProperties(commodityAddRequest, commodity);
        Long id = commodityService.addCommodity(commodity, request);
        return ResultUtils.success(id);
    }

    /***
     * //TODO 这里原本采取了自定义isAdmin方法，为什么不用注解我不理解
     * 删除商品,仅管理员可删除
     * @param id
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/delete")
    public Result<Boolean> deleteCommodity(@RequestBody Long id) {
        ThrowUtils.throwIf(id == null || id < 0, ErrorCode.PARAMS_ERROR);
        Boolean result = commodityService.deleteCommodity(id);
        return ResultUtils.success(result);

    }

    /***
     * 管理员可更新商品所有信息
     * @param commodityUpdateRequest
     * @return
     */

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public Result<Boolean> updateCommodity(@RequestBody CommodityUpdateRequest commodityUpdateRequest) {
        if (ObjectUtils.anyNull(commodityUpdateRequest, commodityUpdateRequest.getId()) || commodityUpdateRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Commodity commodity = new Commodity();
        BeanUtils.copyProperties(commodityUpdateRequest, commodity);
        Boolean result = commodityService.updateCommodity(commodity);
        return ResultUtils.success(result);

    }


    /***
     * 用户可更新商品的部分信息
     * @param commodityEditRequest
     * @return
     */
    @PostMapping("/edit")
    public Result<Boolean> editCommodity(@RequestBody CommodityEditRequest commodityEditRequest) {
        if (ObjectUtils.anyNull(commodityEditRequest, commodityEditRequest.getId()) || commodityEditRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Commodity commodity = new Commodity();
        BeanUtils.copyProperties(commodityEditRequest, commodity);
        Boolean result = commodityService.updateCommodity(commodity);
        return ResultUtils.success(result);


    }

    /***
     * 根据id查询商品
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public Result<CommodityVO> getCommodityVOById(@RequestParam("id") Long id) {
        ThrowUtils.throwIf(id == null || id < 0, ErrorCode.PARAMS_ERROR);
        CommodityVO commodityVO = commodityService.getCommodityVOById(id);
        return ResultUtils.success(commodityVO);
    }

    /***
     * 分页查询商品
     * @param commodityQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    public Result<Page<Commodity>> listCommodityByPage(@RequestBody CommodityQueryRequest commodityQueryRequest) {
        Page<Commodity> page = commodityService.listCommodityByPage(commodityQueryRequest);
        return ResultUtils.success(page);
    }




}
