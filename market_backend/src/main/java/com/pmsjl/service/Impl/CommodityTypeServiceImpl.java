package com.pmsjl.service.Impl;

import static com.pmsjl.constant.RedisConstants.*;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.CommodityTypeMapper;
import com.pmsjl.model.dto.commodityType.CommodityTypeQueryRequest;
import com.pmsjl.model.dto.commodityType.CommodityTypeUpdateRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.vo.CommodityTypeVO;
import com.pmsjl.service.CommodityTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.utils.ThrowUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@Service
public class CommodityTypeServiceImpl extends ServiceImpl<CommodityTypeMapper, CommodityType> implements CommodityTypeService {
    private static final Set<String> ALLOWED_COMMODITY_TYPE_SORT_FIELDS = Set.of(
            "id", "typeName", "createTime", "updateTime"
    );

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Long addCommodityType(String typeName) {
        CommodityType commodityType = new CommodityType();
        commodityType.setTypeName(typeName);
        commodityType.setCreateTime(DateTime.now());
        commodityType.setUpdateTime(DateTime.now());
        boolean result = save(commodityType);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        Long id = commodityType.getId();
        String key = COMMODITY_TYPE_KEY + id;
        String jsonStr = null;
        jsonStr = objectMapper.writeValueAsString(commodityType);
        stringRedisTemplate.opsForValue().set(key, jsonStr, 30, TimeUnit.DAYS);

        return id;
    }

    @Override
    public boolean deleteCommodityType(Long id) {
        CommodityType oldCommodityType = getById(id);
        ThrowUtils.throwIf(oldCommodityType == null, ErrorCode.NOT_FOUND_ERROR);
        //先判断商品类型是否存在，再判断商品类型下是否存在商品，若存在则无法删除
        Long count = Db.lambdaQuery(Commodity.class).eq(Commodity::getCommodityTypeId, id).count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该商品分类下仍然存在商品，无法删除");
        }
        boolean result = removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        String key = COMMODITY_TYPE_KEY + id;
        stringRedisTemplate.delete(key);
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
        Long id = commodityTypeQueryRequest.getId();
        String typeName = commodityTypeQueryRequest.getTypeName();
        int current = commodityTypeQueryRequest.getCurrent();
        int pageSize = commodityTypeQueryRequest.getPageSize();
        String sortField = commodityTypeQueryRequest.getSortField();
        String sortOrder = commodityTypeQueryRequest.getSortOrder();
        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<CommodityType> page = new Page<>(current, pageSize);
        if (sortField != null && !sortField.trim().isEmpty() && ALLOWED_COMMODITY_TYPE_SORT_FIELDS.contains(sortField)) {
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
        Page<CommodityTypeVO> page = new Page<>(commodityTypePage.getCurrent(), commodityTypePage.getSize(), commodityTypePage.getTotal());
        page.setRecords(list);
        return page;
    }


    @Override
    public Boolean updateCommodityType(CommodityTypeUpdateRequest commodityTypeUpdateRequest) {
        Long id = commodityTypeUpdateRequest.getId();
        String typeName = commodityTypeUpdateRequest.getTypeName();
        if (id == null || id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CommodityType commodityType = new CommodityType();
        BeanUtil.copyProperties(commodityTypeUpdateRequest, commodityType);
        commodityType.setUpdateTime(DateTime.now());
        boolean result = updateById(commodityType);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(COMMODITY_TYPE_KEY + id);

        return result;


    }

    @Override
    @SneakyThrows
    public CommodityType getCommodityTypeVOById(Long id) {
        // 查询redis
        String commodityTypeJson = stringRedisTemplate.opsForValue().get(COMMODITY_TYPE_KEY + id);
        // 2. 核心拦截：如果查到了，且是空字符串，说明是防穿透的空壳，直接快速失败！绝对不查库！
        if (commodityTypeJson != null && commodityTypeJson.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "商品类型不存在");
        }
        if (commodityTypeJson == null) {
            CommodityType commodityType = getById(id);
            if (commodityType == null) {
                stringRedisTemplate.opsForValue().set(COMMODITY_TYPE_KEY + id, "", 2, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "商品类型不存在");
            } else {
                String jsonStr = null;
                jsonStr = objectMapper.writeValueAsString(commodityType);
                stringRedisTemplate.opsForValue().set(COMMODITY_TYPE_KEY + id, jsonStr, 30, TimeUnit.DAYS);
                return commodityType;
            }
        } else {
            CommodityType commodityType = null;

            commodityType = objectMapper.readValue(commodityTypeJson, CommodityType.class);

            return commodityType;
        }
    }

}
