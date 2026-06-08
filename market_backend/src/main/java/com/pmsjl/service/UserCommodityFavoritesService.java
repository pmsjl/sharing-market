package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesEditRequest;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesQueryRequest;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.pmsjl.model.vo.UserCommodityFavoritesVO;
import jakarta.servlet.http.HttpServletRequest;

public interface UserCommodityFavoritesService extends IService<UserCommodityFavorites> {

    Long addUserCommodityFavorites(UserCommodityFavorites userCommodityFavorites, HttpServletRequest request);

    Boolean editUserCommodityFavorites(UserCommodityFavoritesEditRequest editRequest, HttpServletRequest request);

    Page<UserCommodityFavoritesVO> listMyUserCommodityFavoritesVOByPage(UserCommodityFavoritesQueryRequest queryRequest,
                                                                        HttpServletRequest request);

    void validUserCommodityFavorites(UserCommodityFavorites userCommodityFavorites, boolean add);
}
