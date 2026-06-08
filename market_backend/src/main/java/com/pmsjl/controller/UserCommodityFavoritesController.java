package com.pmsjl.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesAddRequest;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesEditRequest;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesQueryRequest;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.pmsjl.model.vo.UserCommodityFavoritesVO;
import com.pmsjl.service.UserCommodityFavoritesService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/userCommodityFavorites")
//注意这里相对于源码大幅删减实际没有调用过的接口，实际真正调用的只有三个
public class UserCommodityFavoritesController {

    @Autowired
    private UserCommodityFavoritesService userCommodityFavoritesService;

    @PostMapping("/add")
    public Result<Long> addUserCommodityFavorites(@RequestBody UserCommodityFavoritesAddRequest addRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        UserCommodityFavorites userCommodityFavorites = new UserCommodityFavorites();
        BeanUtil.copyProperties(addRequest, userCommodityFavorites);
        Long id = userCommodityFavoritesService.addUserCommodityFavorites(userCommodityFavorites, request);
        return ResultUtils.success(id);
    }

    @PostMapping("/edit")
    public Result<Boolean> editUserCommodityFavorites(@RequestBody UserCommodityFavoritesEditRequest editRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(editRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result = userCommodityFavoritesService.editUserCommodityFavorites(editRequest, request);
        return ResultUtils.success(result);
    }

    @PostMapping("/my/list/page/vo")
    public Result<Page<UserCommodityFavoritesVO>> listMyUserCommodityFavoritesVOByPage(
            @RequestBody UserCommodityFavoritesQueryRequest queryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<UserCommodityFavoritesVO> page =
                userCommodityFavoritesService.listMyUserCommodityFavoritesVOByPage(queryRequest, request);
        return ResultUtils.success(page);
    }
}
