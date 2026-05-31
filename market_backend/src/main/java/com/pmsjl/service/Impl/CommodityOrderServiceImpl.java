package com.pmsjl.service.Impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.mapper.CommodityOrderMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.CommodityOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-05-31
 */
@Service
public class CommodityOrderServiceImpl extends ServiceImpl<CommodityOrderMapper, CommodityOrder> implements CommodityOrderService {
    @Autowired
    UserService userService;

    @Override
    public Long addCommodityOrder(CommodityOrder commodityOrder, HttpServletRequest request) {
        Long commodityId = commodityOrder.getCommodityId();
        Integer buyNumber = commodityOrder.getBuyNumber();
        ThrowUtils.throwIf(commodityId==null||commodityId<=0, ErrorCode.PARAMS_ERROR,"商品id非法");
        ThrowUtils.throwIf(buyNumber==null||buyNumber<=0,ErrorCode.PARAMS_ERROR,"订单中商品数量非法");
        User loginUser = userService.getLoginUser(request);
        commodityOrder.setUserId(loginUser.getId());
//        commodityOrder.setCreateTime(LocalDateTime.now());
//        commodityOrder.setUpdateTime(LocalDateTime.now());
        //这里无需手动设置时间，在表格中已经默认设置为当前时间戳，无需再手动赋值
        boolean result = this.save(commodityOrder);
        ThrowUtils.throwIf(result==false,ErrorCode.OPERATION_ERROR);
        return commodityOrder.getId();


    }

    @Override
    @Transactional
    public Boolean deleteCommodityOrder(DeleteRequest deleteRequest, HttpServletRequest request) {
        Long id = deleteRequest.getId();
        if(id==null || id<=0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"商品订单id非法");
        }
        CommodityOrder commodityOrder = getById(id);
        ThrowUtils.throwIf(commodityOrder==null,ErrorCode.NOT_FOUND_ERROR,"订单不存在无法删除");
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        if(!ObjectUtil.equals(userId,commodityOrder.getUserId()) && !userService.isAdmin(request)){
            //注意这里的删除权限除了管理员还可以是订单的创建者进行删除
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = removeById(id);
        ThrowUtils.throwIf(result==false,ErrorCode.OPERATION_ERROR);
        return result;


    }
}
