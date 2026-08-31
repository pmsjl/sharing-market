package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.UserCommodityFavoritesMapper;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesEditRequest;
import com.pmsjl.model.dto.userCommodityFavorites.UserCommodityFavoritesQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.entity.UserCommodityFavorites;
import com.pmsjl.model.vo.UserCommodityFavoritesVO;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.UserCommodityFavoritesService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.pmsjl.constant.RedisConstant.CACHE_COMMODITY_KEY;

@Service
public class UserCommodityFavoritesServiceImpl
        extends ServiceImpl<UserCommodityFavoritesMapper, UserCommodityFavorites>
        implements UserCommodityFavoritesService {

    @Autowired
    private UserService userService;

    @Autowired
    private CommodityService commodityService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * add 只会在没有收藏记录时触发；已有收藏记录时，前端会转到 edit 做状态切换。
     */
    public Long addUserCommodityFavorites(UserCommodityFavorites userCommodityFavorites, HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        userCommodityFavorites.setUserId(loginUser.getId());
        userCommodityFavorites.setStatus(1);
        validUserCommodityFavorites(userCommodityFavorites, true);
        commodityService.validateCommodityExists(userCommodityFavorites.getCommodityId());
        boolean saved = save(userCommodityFavorites);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "收藏记录已存在或数据库异常");
        changeCommodityFavourNum(userCommodityFavorites.getCommodityId(), 1);
        return userCommodityFavorites.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * edit 处理已有收藏记录的再次点击：已收藏时取消收藏，已取消时恢复收藏。
     */
    // 这里不操作 isDelete，否则会因为唯一约束导致取消后无法再次收藏。
    public Boolean editUserCommodityFavorites(UserCommodityFavoritesEditRequest editRequest,
                                              HttpServletRequest request) {
        Long id = editRequest.getId();
        Integer status = editRequest.getStatus();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!isValidStatus(status), ErrorCode.PARAMS_ERROR, "收藏状态只能为 0 或 1");
        UserCommodityFavorites oldFavorite = getById(id);
        ThrowUtils.throwIf(oldFavorite == null, ErrorCode.NOT_FOUND_ERROR, "收藏记录不存在");
        checkOwnerOrAdmin(oldFavorite, request);
        if (Objects.equals(oldFavorite.getStatus(), status)) {
            return true;
        }
        if (Objects.equals(status, 1)) {
            commodityService.validateCommodityExists(oldFavorite.getCommodityId());
        }
        boolean updated = lambdaUpdate()
                .set(UserCommodityFavorites::getStatus, status)
                .eq(UserCommodityFavorites::getId, id)
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        changeCommodityFavourNum(oldFavorite.getCommodityId(), Objects.equals(status, 1) ? 1 : -1);
        return true;
    }

    @Override
    public Page<UserCommodityFavoritesVO> listMyUserCommodityFavoritesVOByPage(
            UserCommodityFavoritesQueryRequest queryRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        queryRequest.setUserId(loginUser.getId());
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 10;
        }
        Page<UserCommodityFavoritesVO> page = new Page<>(current, pageSize);
        return baseMapper.selectMyFavoritesVOPage(page, queryRequest);
        //这里之所以没有采取之前的思路去分页查询是因为他的VO包含commodity信息，在我们将listpage的信息转换为vo时page里面的total没变
        //但是我们在转换commodity信息赋值给vo的时候会根据是否有commodity进行筛选（原本的total是favorite的数量），
        // 此时会导致records最后得到的vo数量小于total，这是不合理的
        //所以直接用sql语句实现内连接
        //还有一点，我们的MybatisPlusConfig通过分页插件的配置，我们不需要显式的写出分页的limit，而是它会自动检测我们传递的方法是否有Page类
        //然后自动补充sql语句，获取count(*)和添加limit

    }

    @Override
    public void validUserCommodityFavorites(UserCommodityFavorites userCommodityFavorites, boolean add) {
        ThrowUtils.throwIf(userCommodityFavorites == null, ErrorCode.PARAMS_ERROR);
        Long userId = userCommodityFavorites.getUserId();
        Long commodityId = userCommodityFavorites.getCommodityId();
        Integer status = userCommodityFavorites.getStatus();
        if (add) {
            ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "收藏用户 id 非法");
            ThrowUtils.throwIf(commodityId == null || commodityId <= 0, ErrorCode.PARAMS_ERROR, "商品 id 非法");
        }
        ThrowUtils.throwIf(commodityId != null && commodityId <= 0, ErrorCode.PARAMS_ERROR, "商品 id 非法");
        ThrowUtils.throwIf(userId != null && userId <= 0, ErrorCode.PARAMS_ERROR, "收藏用户 id 非法");
        ThrowUtils.throwIf(!isValidStatus(status), ErrorCode.PARAMS_ERROR, "收藏状态只能为 0 或 1");
    }

    private void checkOwnerOrAdmin(UserCommodityFavorites favorite, HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        if (!Objects.equals(loginUser.getId(), favorite.getUserId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    private boolean isValidStatus(Integer status) {
        return Objects.equals(status, 0) || Objects.equals(status, 1);
    }

    private void changeCommodityFavourNum(Long commodityId, int delta) {
        boolean updated;
        if (delta > 0) {
            updated = commodityService.lambdaUpdate()
                    .setSql("favourNum = IFNULL(favourNum, 0) + 1")
                    .eq(Commodity::getId, commodityId)
                    .eq(Commodity::getIsDelete, 0)
                    .update();
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "商品收藏数更新失败");
        } else {
            updated = commodityService.lambdaUpdate()
                    .setSql("favourNum = GREATEST(IFNULL(favourNum, 0) - 1, 0)")
                    .eq(Commodity::getId, commodityId)
                    .eq(Commodity::getIsDelete, 0)
                    .update();
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "商品收藏数更新失败");
        }
        stringRedisTemplate.delete(CACHE_COMMODITY_KEY + commodityId);
    }
}
