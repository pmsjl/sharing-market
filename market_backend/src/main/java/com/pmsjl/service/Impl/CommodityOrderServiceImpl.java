package com.pmsjl.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderEditRequest;
import com.pmsjl.model.dto.commodityOrder.CommodityOrderQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.mapper.CommodityOrderMapper;
import com.pmsjl.model.entity.CommodityOrder;
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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public Page<CommodityOrderVO> listCommodityOrderVOByPage(CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        Page<CommodityOrder> commodityOrderPage = this.listCommodityOrderByPage(commodityOrderQueryRequest, request);
        List<CommodityOrder> records = commodityOrderPage.getRecords();

        long current = commodityOrderPage.getCurrent();
        long pageSize = commodityOrderPage.getSize();
        long total = commodityOrderPage.getTotal();
        Page<CommodityOrderVO>page=new Page<>(current,pageSize,total);
        if (records == null || records.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }
        List<CommodityOrderVO> commodityOrderVOList = records.stream().map(CommodityOrderVO::objToVo).toList();
        // 关联查询用户信息
        Set<Long> userIdSet = commodityOrderVOList.stream()
                .map(CommodityOrderVO::getUserId)
                .collect(Collectors.toSet());
        // 关联查询商品信息
        Set<Long> commodityIdSet = commodityOrderVOList.stream()
                .map(CommodityOrderVO::getCommodityId)
                .collect(Collectors.toSet());
        // 批量查询用户信息
        Map<Long, User> userIdUserMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        // 批量查询商品信息
        Map<Long, String> commodityIdMap = commodityService.listByIds(commodityIdSet).stream()
                .collect(Collectors.toMap(Commodity::getId, Commodity::getCommodityName));
        // 填充用户信息到 VO 对象
        commodityOrderVOList.forEach(commodityOrderVO -> {
            User user = userIdUserMap.get(commodityOrderVO.getUserId());
            if (user != null) {
                commodityOrderVO.setUserName(user.getUserName());
                commodityOrderVO.setUserPhone(user.getUserPhone());
            }
        });
        // 填充商品信息到 VO 对象
        commodityOrderVOList.forEach(commodityOrderVO -> {
            String commodityName = commodityIdMap.get(commodityOrderVO.getCommodityId());
            if (commodityName != null) {
                commodityOrderVO.setCommodityName(commodityName);
            }
        });
        page.setRecords(commodityOrderVOList);
        return page;


    }

    @Override
    public Page<CommodityOrderVO> listMyCommodityOrderVOByPage(CommodityOrderQueryRequest commodityOrderQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        commodityOrderQueryRequest.setUserId(loginUser.getId());
        Page<CommodityOrderVO> commodityOrderVOPage = this.listCommodityOrderVOByPage(commodityOrderQueryRequest, request);
        return commodityOrderVOPage;


    }

    @Override
    public Boolean editCommodityOrder(CommodityOrderEditRequest commodityOrderEditRequest, HttpServletRequest request) {
        Long id = commodityOrderEditRequest.getId();
        CommodityOrder oldCommodityOrder = getById(id);
        User loginUser = userService.getLoginUser(request);
        if (!ObjectUtil.equals(loginUser.getId(), oldCommodityOrder.getUserId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //这里在原有基础上加上了权限校验，就是理论上只用前端根本不会有问题，但是怕的是有人直接通过接口访问，那么如果id不一致就不能修改了
        if(oldCommodityOrder==null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        CommodityOrder commodityOrder=new CommodityOrder();
        BeanUtil.copyProperties(commodityOrderEditRequest,commodityOrder);
        boolean result = updateById(commodityOrder);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;

    }

    @Override
    public List<Map<String, Object>> getCommodityOrderHeatmapData(CommodityOrderQueryRequest queryRequest) {
        List<CommodityOrder> orderList = lambdaQuery().eq(queryRequest.getUserId() != null, CommodityOrder::getUserId, queryRequest.getUserId()).
                eq(queryRequest.getPayStatus() != null, CommodityOrder::getPayStatus, queryRequest.getPayStatus()).list();

        // 处理查询结果，生成日期和订单数量的列表
        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 统计每个日期的订单数量
        Map<String, Integer> dateCountMap = new HashMap<>();
        for (CommodityOrder order : orderList) {
            if (order.getCreateTime() == null) {
                continue;
            }
            String dateStr = dateFormat.format(order.getCreateTime());
            dateCountMap.put(dateStr, dateCountMap.getOrDefault(dateStr, 0) + 1);
        }

        // 将统计结果转换为前端需要的格式
        for (Map.Entry<String, Integer> entry : dateCountMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", entry.getKey());
            item.put("value", entry.getValue());
            result.add(item);
        }
        return result;
    }
}
