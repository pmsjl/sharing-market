package com.pmsjl.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.mapper.CommodityMapper;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@Service
public class CommodityServiceImpl extends ServiceImpl<CommodityMapper, Commodity> implements CommodityService {
    @Autowired
    private UserService userService;
    @Autowired
    private CommodityTypeService commodityTypeService;

    @Override
    public Long addCommodity(Commodity commodity, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long id = loginUser.getId();
        commodity.setAdminId(id);
        validCommodity(commodity);
        boolean result = this.save(commodity);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        return commodity.getId();

    }

    public void validCommodity(Commodity commodity) {
        Long adminIdid = commodity.getAdminId();
        String commodityName = commodity.getCommodityName();
        Integer commodityInventory = commodity.getCommodityInventory();
        BigDecimal price = commodity.getPrice();
        if (adminIdid == null || adminIdid < 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "操作用户异常");
        }
        ThrowUtils.throwIf(!StringUtils.isNotBlank(commodityName), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf((commodityInventory == null) || (commodityInventory <= 0), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf((price == null) || (price.compareTo(BigDecimal.ZERO) <= 0), ErrorCode.PARAMS_ERROR);

    }

    @Override
    public Boolean deleteCommodity(Long id) {
        Commodity commodity = getById(id);
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = removeById(id);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        return result;
    }

    @Override
    public Boolean updateCommodity(Commodity commodity) {
        validCommodity(commodity);
        Long id = commodity.getId();
        Commodity oldCommodity = getById(id);
        ThrowUtils.throwIf(oldCommodity == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = updateById(commodity);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        return result;
    }

    @Override
    public CommodityVO getCommodityVOById(Long id) {
        Commodity commodity = getById(id);
        ThrowUtils.throwIf(commodity == null, ErrorCode.NOT_FOUND_ERROR);
        CommodityVO commodityVO = CommodityVO.objToVo(commodity);
        //这里还有两个变量没有赋值需要获取后再赋值
        Long adminId = commodityVO.getAdminId();
        Long commodityTypeId = commodityVO.getCommodityTypeId();
        if (adminId != null) {
            User user = userService.getById(adminId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "操作用户不存在");
            commodityVO.setAdminName(user.getUserName());
        }
        if (commodityTypeId != null) {
            CommodityType commodityType = commodityTypeService.getById(commodityTypeId);
            ThrowUtils.throwIf(commodityType == null, ErrorCode.NOT_FOUND_ERROR, "商品类型不存在");
            commodityVO.setCommodityTypeName(commodityType.getTypeName());
        }
        return commodityVO;

    }

    @Override
    public Page<Commodity> listCommodityByPage(CommodityQueryRequest commodityQueryRequest) {
        ThrowUtils.throwIf(commodityQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = commodityQueryRequest.getId();
        String commodityName = commodityQueryRequest.getCommodityName();
        String commodityDescription = commodityQueryRequest.getCommodityDescription();
        String degree = commodityQueryRequest.getDegree();
        Long commodityTypeId = commodityQueryRequest.getCommodityTypeId();
        Long adminId = commodityQueryRequest.getAdminId();
        Integer isListed = commodityQueryRequest.getIsListed();
        Integer commodityInventory = commodityQueryRequest.getCommodityInventory();


        String sortField = commodityQueryRequest.getSortField();
        String sortOrder = commodityQueryRequest.getSortOrder();
        int current = commodityQueryRequest.getCurrent();
        int pageSize = commodityQueryRequest.getPageSize();
        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<Commodity> page = new Page<>(current, pageSize);
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
        // 模糊查询
        //TODO 貌似前端并没有对所有数据都进行条件查询，有几个变量没有参与查询，至少price都没有，到时候看怎么优化吧
        Page<Commodity> commodityPage = lambdaQuery()
                .like(StringUtils.isNotBlank(commodityName), Commodity::getCommodityName, commodityName)
                .like(StringUtils.isNotBlank(commodityDescription), Commodity::getCommodityDescription, commodityDescription)
                .like(StringUtils.isNotBlank(degree), Commodity::getDegree, degree)
                .eq(ObjectUtils.isNotEmpty(commodityTypeId), Commodity::getCommodityTypeId, commodityTypeId)
                .eq(ObjectUtils.isNotEmpty(id), Commodity::getId, id)
                .eq(ObjectUtils.isNotEmpty(adminId), Commodity::getAdminId, adminId)
                .eq(ObjectUtils.isNotEmpty(commodityInventory), Commodity::getCommodityInventory, commodityInventory)
                .eq(ObjectUtils.isNotEmpty(isListed), Commodity::getIsListed, isListed).page(page);
        return commodityPage;
    }

    @Override
    public Page<CommodityVO> listCommodityVOByPage(CommodityQueryRequest commodityQueryRequest) {
        Page<Commodity> commodityPage = listCommodityByPage(commodityQueryRequest);
        long current = commodityPage.getCurrent();
        long size = commodityPage.getSize();
        long total = commodityPage.getTotal();
        Page<CommodityVO> page = new Page<>(current, size, total);
        List<Commodity> records = commodityPage.getRecords();


        Set<Long> commodityTypeIdList = records.stream().map((commodity) -> {
            Long commodityTypeId = commodity.getCommodityTypeId();
            return commodityTypeId;
        }).collect(Collectors.toSet());
        List<CommodityType> commodityTypes = new ArrayList<>();
        if (!CollUtil.isEmpty(commodityTypeIdList)) {
            commodityTypes = commodityTypeService.listByIds(commodityTypeIdList);
        }
        Map<Long, String> commodityTypeMap = commodityTypes.stream().collect(Collectors.toMap(CommodityType::getId, CommodityType::getTypeName));


        Set<Long> adminIdList = records.stream().map((commodity) -> {
            Long adminId = commodity.getAdminId();
            return adminId;
        }).collect(Collectors.toSet());

        List<User> userList = new ArrayList<>();
        if (!CollUtil.isEmpty(adminIdList)) {
            userList = userService.listByIds(adminIdList);
        }
        Map<Long, String> userMap = userList.stream().collect(Collectors.toMap(User::getId, User::getUserName));


        List<CommodityVO> list = records.stream().map((commodity) -> {
            CommodityVO commodityVO = CommodityVO.objToVo(commodity);
            Long adminId = commodityVO.getAdminId();
            Long commodityTypeId = commodityVO.getCommodityTypeId();
            if (adminId != null) {
                commodityVO.setAdminName(userMap.get(adminId));
            }
            if (commodityTypeId != null) {
                commodityVO.setCommodityTypeName(commodityTypeMap.get(commodityTypeId));
            }
            return commodityVO;
        }).toList();

        page.setRecords(list);
        return page;


    }

    @Override
    public Page<CommodityVO> listMyCommodityVOByPage(CommodityQueryRequest commodityQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        commodityQueryRequest.setAdminId(loginUser.getId());
        Page<CommodityVO> commodityVOPage = listCommodityVOByPage(commodityQueryRequest);
        return commodityVOPage;
    }

    //TODO 关于推荐算法和购买商品还有三个接口尚未实现
}
