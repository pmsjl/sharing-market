package com.pmsjl.service.Impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.CommodityTypeMapper;
import com.pmsjl.model.dto.commodityType.CommodityTypeEditRequest;
import com.pmsjl.model.dto.commodityType.CommodityTypeQueryRequest;
import com.pmsjl.model.dto.commodityType.CommodityTypeUpdateRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityTypeVO;
import com.pmsjl.service.CommodityTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@Service
public class CommodityTypeServiceImpl extends ServiceImpl<CommodityTypeMapper, CommodityType> implements CommodityTypeService {

    @Override
    public Long addCommodityType(String typeName) {
        CommodityType commodityType=new CommodityType();
        commodityType.setTypeName(typeName);
        commodityType.setCreateTime(DateTime.now());
        commodityType.setUpdateTime(DateTime.now());
        boolean result = save(commodityType);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return commodityType.getId();
    }

    @Override
    public boolean deleteCommodityType(Long id) {
        //TODO 管理员权限判断尚未完成
        CommodityType oldCommodityType = getById(id);
        ThrowUtils.throwIf(oldCommodityType==null, ErrorCode.NOT_FOUND_ERROR);
        //先判断商品类型是否存在，再判断商品类型下是否存在商品，若存在则无法删除
        Long count = Db.lambdaQuery(Commodity.class).eq(Commodity::getCommodityTypeId, id).count();
        if(count>0){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"该商品分类下仍然存在商品，无法删除");
        }
        boolean result = removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;

    }


    @Override
    public CommodityTypeVO getCommodityTypeVO(CommodityType commodityType) {
        return CommodityTypeVO.objToVo(commodityType);
    }

    @Override
    public Page<CommodityType> listCommodityTypeByPage(CommodityTypeQueryRequest commodityTypeQueryRequest) {
        if (commodityTypeQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id =commodityTypeQueryRequest.getId();
        String typeName=commodityTypeQueryRequest.getTypeName();
        int current = commodityTypeQueryRequest.getCurrent();
        int pageSize = commodityTypeQueryRequest.getPageSize();
        String sortField = commodityTypeQueryRequest.getSortField();
        String sortOrder = commodityTypeQueryRequest.getSortOrder();
        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<CommodityType> page = new Page<>(current, pageSize);
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
        Page<CommodityType> commodityTypePage = lambdaQuery().like(StringUtils.isNotBlank(typeName), CommodityType::getTypeName, typeName).
                eq(!ObjectUtils.isEmpty(id),
                        CommodityType::getId, id).page(page);
        return commodityTypePage;

    }

    @Override
    public Page<CommodityTypeVO> listCommodityTypeVOByPage(CommodityTypeQueryRequest request) {
        Page<CommodityType> commodityTypePage = listCommodityTypeByPage(request);
        List<CommodityType> records = commodityTypePage.getRecords();
        List<CommodityTypeVO> list = records.stream().map(CommodityTypeVO::objToVo).toList();
        Page<CommodityTypeVO>page=new Page<>(commodityTypePage.getCurrent(),commodityTypePage.getSize(),commodityTypePage.getTotal());
        page.setRecords(list);
        return page;
    }



    @Override
    public Boolean updateCommodityType(CommodityTypeUpdateRequest commodityTypeUpdateRequest) {
        Long id=commodityTypeUpdateRequest.getId();
        String typeName=commodityTypeUpdateRequest.getTypeName();
        if(id==null||id<0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CommodityType commodityType=new CommodityType();
        BeanUtil.copyProperties(commodityTypeUpdateRequest,commodityType);
        commodityType.setUpdateTime(DateTime.now());
        boolean result = updateById(commodityType);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
        return result;


    }

}
