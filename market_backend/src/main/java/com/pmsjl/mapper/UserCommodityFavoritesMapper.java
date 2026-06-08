package com.pmsjl.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesQueryRequest;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.vo.UserCommodityFavoritesVO;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-08
 */
public interface UserCommodityFavoritesMapper extends BaseMapper<UserCommodityFavorites> {

    Page<UserCommodityFavoritesVO> selectMyFavoritesVOPage(Page<UserCommodityFavoritesVO> page,
                                                           @Param("queryRequest") UserCommodityFavoritesQueryRequest queryRequest);
}
