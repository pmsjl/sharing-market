package com.pmsjl.service.Impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityOrder;
import com.pmsjl.mapper.CommodityOrderMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityOrderVO;

import com.pmsjl.service.CommodityOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-05-31
 */
@Service
public class CommodityOrderServiceImpl extends ServiceImpl<CommodityOrderMapper, CommodityOrder> implements CommodityOrderService {
    @Autowired
    UserService userService;
    @Autowired
    CommodityService commodityService;

    @Override
    public Long addCommodityOrder(CommodityOrder commodityOrder, HttpServletRequest request) {
        validCommodityOrder(commodityOrder);
        User loginUser = userService.getLoginUser(request);
        commodityOrder.setUserId(loginUser.getId());
//        commodityOrder.setCreateTime(LocalDateTime.now());
//        commodityOrder.setUpdateTime(LocalDateTime.now());
        //这里无需手动设置时间，在表格中已经默认设置为当前时间戳，无需再手动赋值
        boolean result = this.save(commodityOrder);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        return commodityOrder.getId();


    }

    private static void validCommodityOrder(CommodityOrder commodityOrder) {
        Long commodityId = commodityOrder.getCommodityId();
        Integer buyNumber = commodityOrder.getBuyNumber();
        ThrowUtils.throwIf(commodityId == null || commodityId <= 0, ErrorCode.PARAMS_ERROR, "商品id非法");
        ThrowUtils.throwIf(buyNumber == null || buyNumber <= 0, ErrorCode.PARAMS_ERROR, "订单中商品数量非法");
    }

    @Override
    @Transactional
    public Boolean deleteCommodityOrder(DeleteRequest deleteRequest, HttpServletRequest request) {
        Long id = deleteRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "商品订单id非法");
        }
        CommodityOrder commodityOrder = getById(id);
        ThrowUtils.throwIf(commodityOrder == null, ErrorCode.NOT_FOUND_ERROR, "订单不存在无法删除");
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        if (!ObjectUtil.equals(userId, commodityOrder.getUserId()) && !userService.isAdmin(request)) {
            //注意这里的删除权限除了管理员还可以是订单的创建者进行删除
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = removeById(id);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        return result;


    }

    @Override
    @Transactional
    public Boolean updateCommodityOrder(CommodityOrder commodityOrder) {
        validCommodityOrder(commodityOrder);
        Long id = commodityOrder.getId();
        CommodityOrder oldCommodity = getById(id);
        ThrowUtils.throwIf(oldCommodity == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = this.updateById(commodityOrder);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;

    }

    @Override
    public CommodityOrderVO getCommodityOrderVOById(Long id, HttpServletRequest request) {

        CommodityOrder commodityOrder = getById(id);
        ThrowUtils.throwIf(commodityOrder == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUser(request);
        if (!ObjectUtil.equals(loginUser.getId(), commodityOrder.getUserId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //这里是我添加的权限校验，对于订单查询，查询人应该只能查询到自己的订单，或者管理员也行
        validCommodityOrder(commodityOrder);
        CommodityOrderVO commodityOrderVO = CommodityOrderVO.objToVo(commodityOrder);
        //补充VO特有元素
        Long commodityId = commodityOrderVO.getCommodityId();
        User user = userService.getById(commodityOrderVO.getUserId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "订单创建者不存在，出现异常");
        Commodity commodity = commodityService.getById(commodityId);
        //这里本来想用那个getvo但是他会同步增加浏览量，只是查询订单就没必要了，所以这里还是采取mysql
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR, "订单中商品不存在，出现异常");
        commodityOrderVO.setUserName(user.getUserName());
        commodityOrderVO.setUserPhone(user.getUserPhone());
        commodityOrderVO.setCommodityName(commodity.getCommodityName());
        return commodityOrderVO;

    }

    @Override
    public Page<CommodityOrder> listCommodityOrderByPage(CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        int current = commodityOrderQueryRequest.getCurrent();
        int pageSize = commodityOrderQueryRequest.getPageSize();
        Long id = commodityOrderQueryRequest.getId();
        Long userId = commodityOrderQueryRequest.getUserId();
        Long commodityId = commodityOrderQueryRequest.getCommodityId();
        String remark = commodityOrderQueryRequest.getRemark();
        Integer buyNumber = commodityOrderQueryRequest.getBuyNumber();
        Integer payStatus = commodityOrderQueryRequest.getPayStatus();
        String sortField = commodityOrderQueryRequest.getSortField();
        String sortOrder = commodityOrderQueryRequest.getSortOrder();


        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<CommodityOrder> page = new Page<>(current, pageSize);
        if (sortField != null && !sortField.trim().isEmpty()) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            // 默认按更新时间降序
            page.addOrder(OrderItem.desc("updateTime"));
        }
        Page<CommodityOrder> commodityOrderPage=this.lambdaQuery().like(StringUtils.isNotBlank(remark), CommodityOrder::getRemark, remark)
                .eq(ObjectUtils.isNotEmpty(payStatus), CommodityOrder::getPayStatus, payStatus)
                .eq(ObjectUtils.isNotEmpty(buyNumber), CommodityOrder::getBuyNumber, buyNumber)
                .eq(ObjectUtils.isNotEmpty(commodityId), CommodityOrder::getCommodityId, commodityId)
                .eq(ObjectUtils.isNotEmpty(id), CommodityOrder::getId, id)
                .eq(ObjectUtils.isNotEmpty(userId), CommodityOrder::getUserId, userId).page(page);

        return commodityOrderPage;


    }
}
