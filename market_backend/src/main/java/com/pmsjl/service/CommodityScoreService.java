package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.entity.CommodityScore;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreEditRequest;
import com.pmsjl.model.dto.commodityScore.CommodityScoreQueryRequest;
import com.pmsjl.model.vo.CommodityScoreVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-04
 */
public interface CommodityScoreService extends IService<CommodityScore> {

    Long addCommodityScore(CommodityScore commodityScore, HttpServletRequest request);

    Boolean deleteCommodityScore(DeleteRequest deleteRequest, HttpServletRequest request);

    Boolean updateCommodityScore(CommodityScore commodityScore);

    CommodityScoreVO getCommodityScoreVOById(Long id, HttpServletRequest request);

    Page<CommodityScore> listCommodityScoreByPage(CommodityScoreQueryRequest commodityScoreQueryRequest);

    Page<CommodityScoreVO> listCommodityScoreVOByPage(CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request);

    Page<CommodityScoreVO> listMyCommodityScoreVOByPage(CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request);

    Boolean editCommodityScore(CommodityScoreEditRequest commodityScoreEditRequest, HttpServletRequest request);

    void validCommodityScore(CommodityScore commodityScore, boolean add);
}
