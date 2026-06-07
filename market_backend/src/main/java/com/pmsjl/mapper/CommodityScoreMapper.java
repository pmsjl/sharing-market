package com.pmsjl.mapper;

import com.pmsjl.model.entity.CommodityScore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-04
 */
public interface CommodityScoreMapper extends BaseMapper<CommodityScore> {

    double getAverageScoreById(Long commodityId);
}
