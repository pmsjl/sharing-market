package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodityType.*;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.vo.CommodityTypeVO;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
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
@RequestMapping("/commodityType")
public class CommodityTypeController {
    @Autowired
    private CommodityTypeService commodityTypeService;

    /***
     * 添加商品类型
     * @param commodityTypeAddRequest
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/add")
    public Result<Long> addCommodityType(@RequestBody CommodityTypeAddRequest commodityTypeAddRequest) {
        if (commodityTypeAddRequest == null || commodityTypeAddRequest.getTypeName() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String typeName = commodityTypeAddRequest.getTypeName();
        Long id = commodityTypeService.addCommodityType(typeName);
        return ResultUtils.success(id);
    }

    /***
     * 删除商品类型
     * @param commodityTypeDeleteRequest
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/delete")
    public Result<Boolean> deleteCommodityType(@RequestBody CommodityTypeDeleteRequest commodityTypeDeleteRequest) {
        if (commodityTypeDeleteRequest == null || commodityTypeDeleteRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = commodityTypeDeleteRequest.getId();
        boolean result = commodityTypeService.deleteCommodityType(id);
        return ResultUtils.success(result);
    }

    /***
     * 获取指定id的商品类型
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public Result<CommodityTypeVO> getCommodityTypeVOById(Long id) {
        if (id == null || id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CommodityType commodityType= commodityTypeService.getCommodityTypeVOById(id);
        return ResultUtils.success(commodityTypeService.getCommodityTypeVO(commodityType));

    }

    /***
     * 分页查询商品类型
     * @param commodityTypeQueryRequest
     * @return
     */
    /***
     * 分页查询部分的商品类型信息
     * @param commodityTypeQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public Result<Page<CommodityTypeVO>>listCommodityTypeVOByPage(@RequestBody CommodityTypeQueryRequest commodityTypeQueryRequest) {
        Page<CommodityTypeVO> page = commodityTypeService.listCommodityTypeVOByPage(commodityTypeQueryRequest);
        return ResultUtils.success(page);
    }


    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean>updateCommodityType(@RequestBody CommodityTypeUpdateRequest commodityTypeUpdateRequest){
        ThrowUtils.throwIf(commodityTypeUpdateRequest==null,ErrorCode.PARAMS_ERROR);
        Boolean result=commodityTypeService.updateCommodityType(commodityTypeUpdateRequest);
        return ResultUtils.success(result);


    }


}
