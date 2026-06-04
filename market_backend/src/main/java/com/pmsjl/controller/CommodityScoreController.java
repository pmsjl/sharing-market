package com.pmsjl.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.model.dto.commodityScore.CommodityScoreAddRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreEditRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreQueryRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreUpdateRequest;
import com.pmsjl.model.entity.CommodityScore;
import com.pmsjl.model.vo.CommodityScoreVO;
import com.pmsjl.service.CommodityScoreService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-04
 */
@RestController
@RequestMapping("/commodityScore")
public class CommodityScoreController {

    @Autowired
    private CommodityScoreService commodityScoreService;

    @PostMapping("/add")
    public Result<Long> addCommodityScore(@RequestBody CommodityScoreAddRequest commodityScoreAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityScoreAddRequest == null, ErrorCode.PARAMS_ERROR);
        CommodityScore commodityScore = new CommodityScore();
        BeanUtil.copyProperties(commodityScoreAddRequest, commodityScore);
        Long id = commodityScoreService.addCommodityScore(commodityScore, request);
        return ResultUtils.success(id);
    }

    @PostMapping("/delete")
    public Result<Boolean> deleteCommodityScore(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = commodityScoreService.deleteCommodityScore(deleteRequest, request);
        return ResultUtils.success(result);
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public Result<Boolean> updateCommodityScore(@RequestBody CommodityScoreUpdateRequest commodityScoreUpdateRequest) {
        ThrowUtils.throwIf(commodityScoreUpdateRequest == null || commodityScoreUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        CommodityScore commodityScore = new CommodityScore();
        BeanUtil.copyProperties(commodityScoreUpdateRequest, commodityScore);
        Boolean result = commodityScoreService.updateCommodityScore(commodityScore);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public Result<CommodityScoreVO> getCommodityScoreVOById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CommodityScoreVO commodityScoreVO = commodityScoreService.getCommodityScoreVOById(id, request);
        return ResultUtils.success(commodityScoreVO);
    }

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page")
    public Result<Page<CommodityScore>> listCommodityScoreByPage(@RequestBody CommodityScoreQueryRequest commodityScoreQueryRequest) {
        ThrowUtils.throwIf(commodityScoreQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityScore> page = commodityScoreService.listCommodityScoreByPage(commodityScoreQueryRequest);
        return ResultUtils.success(page);
    }

    @PostMapping("/list/page/vo")
    public Result<Page<CommodityScoreVO>> listCommodityScoreVOByPage(@RequestBody CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityScoreQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityScoreVO> page = commodityScoreService.listCommodityScoreVOByPage(commodityScoreQueryRequest, request);
        return ResultUtils.success(page);
    }

    @PostMapping("/my/list/page/vo")
    public Result<Page<CommodityScoreVO>> listMyCommodityScoreVOByPage(@RequestBody CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityScoreQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<CommodityScoreVO> page = commodityScoreService.listMyCommodityScoreVOByPage(commodityScoreQueryRequest, request);
        return ResultUtils.success(page);
    }

    @PostMapping("/edit")
    public Result<Boolean> editCommodityScore(@RequestBody CommodityScoreEditRequest commodityScoreEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityScoreEditRequest == null || commodityScoreEditRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Boolean result = commodityScoreService.editCommodityScore(commodityScoreEditRequest, request);
        return ResultUtils.success(result);
    }
}
