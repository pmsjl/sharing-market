package com.pmsjl.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodityScore.CommodityScoreAddRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreQueryRequest;
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


    @GetMapping("/averageScore")
    public Result<Double> getAverageScore(@RequestParam Long commodityId) {
        ThrowUtils.throwIf(commodityId==null||commodityId<=0,ErrorCode.PARAMS_ERROR);
        Double score=commodityScoreService.getAverageScoreById(commodityId);
        if(score==null){
            return ResultUtils.success(0.0D);
        }
        if(score<0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //这里没有放在service层的原因就是controller才是统一负责响应result的层
        //不应该返回不同result类型的层放到service层
        return ResultUtils.success(score);
    }
}
