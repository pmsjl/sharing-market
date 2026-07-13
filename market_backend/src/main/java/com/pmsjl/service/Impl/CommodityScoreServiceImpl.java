package com.pmsjl.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityScore;
import com.pmsjl.mapper.CommodityScoreMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodityScore.CommodityScoreQueryRequest;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CommodityScoreVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.CommodityScoreService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-04
 */
@Service
public class CommodityScoreServiceImpl extends ServiceImpl<CommodityScoreMapper, CommodityScore> implements CommodityScoreService {
    private static final Set<String> ALLOWED_COMMODITY_SCORE_SORT_FIELDS = Set.of(
            "id", "commodityId", "userId", "score", "createTime", "updateTime"
    );

    @Autowired
    private UserService userService;
    @Autowired
    private CommodityScoreMapper commodityScoreMapper;
    @Autowired
    private CommodityService commodityService;

    @Override
    public Long addCommodityScore(CommodityScore commodityScore, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        commodityScore.setUserId(loginUser.getId());
        validCommodityScore(commodityScore, true);
        // 3. 校验商品是否存在
        Long commodityId = commodityScore.getCommodityId();
        Commodity commodity = commodityService.getById(commodityId);
        ThrowUtils.throwIf(
                commodity == null || Objects.equals(commodity.getIsDelete(), 1),
                ErrorCode.NOT_FOUND_ERROR,
                "商品不存在"
        );
        // 4. 校验商品是否上架
        ThrowUtils.throwIf(
                !Objects.equals(commodity.getIsListed(), 1),
                ErrorCode.PARAMS_ERROR,
                "商品未上架，不能评价"
        );

        // 5. 不允许评价自己的商品
        ThrowUtils.throwIf(
                ObjectUtil.equals(commodity.getAdminId(), loginUser.getId()),
                ErrorCode.PARAMS_ERROR,
                "不能评价自己的商品"
        );
        //同时注意表建立了联合约束，建立了索引都是限制了(commodityId,userId)这一组不会重复

        boolean result = save(commodityScore);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return commodityScore.getId();
    }

    private Page<CommodityScore> listCommodityScoreByPage(CommodityScoreQueryRequest commodityScoreQueryRequest) {
        int current = commodityScoreQueryRequest.getCurrent();
        int pageSize = commodityScoreQueryRequest.getPageSize();
        Long id = commodityScoreQueryRequest.getId();
        Long commodityId = commodityScoreQueryRequest.getCommodityId();
        Long userId = commodityScoreQueryRequest.getUserId();
        Integer score = commodityScoreQueryRequest.getScore();
        String sortField = commodityScoreQueryRequest.getSortField();
        String sortOrder = commodityScoreQueryRequest.getSortOrder();

        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<CommodityScore> page = new Page<>(current, pageSize);
        if (sortField != null && !sortField.trim().isEmpty() && ALLOWED_COMMODITY_SCORE_SORT_FIELDS.contains(sortField)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            page.addOrder(OrderItem.desc("updateTime"));
        }
        return lambdaQuery()
                .eq(ObjectUtils.isNotEmpty(id), CommodityScore::getId, id)
                .eq(ObjectUtils.isNotEmpty(commodityId), CommodityScore::getCommodityId, commodityId)
                .eq(ObjectUtils.isNotEmpty(userId), CommodityScore::getUserId, userId)
                .eq(ObjectUtils.isNotEmpty(score), CommodityScore::getScore, score)
                .page(page);
    }

    @Override
    public Page<CommodityScoreVO> listCommodityScoreVOByPage(CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request) {
        Page<CommodityScore> commodityScorePage = listCommodityScoreByPage(commodityScoreQueryRequest);
        List<CommodityScore> records = commodityScorePage.getRecords();
        Page<CommodityScoreVO> page = new Page<>(commodityScorePage.getCurrent(), commodityScorePage.getSize(), commodityScorePage.getTotal());
        if (records == null || records.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }
        List<CommodityScoreVO> commodityScoreVOList = records.stream().map(this::getCommodityScoreVO).toList();
        Set<Long> userIdSet = commodityScoreVOList.stream()
                .map(CommodityScoreVO::getUserId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
        if (!userIdSet.isEmpty()) {
            Map<Long, User> userIdUserMap = userService.listByIds(userIdSet).stream()
                    .collect(Collectors.toMap(User::getId, user -> user));
            commodityScoreVOList.forEach(commodityScoreVO -> {
                User user = userIdUserMap.get(commodityScoreVO.getUserId());
                if (user != null) {
                    UserVO userVO = new UserVO();
                    BeanUtil.copyProperties(user, userVO);
                    commodityScoreVO.setUserVO(userVO);
                }
            });
        }
        page.setRecords(commodityScoreVOList);
        return page;
    }

    @Override
    public Page<CommodityScoreVO> listMyCommodityScoreVOByPage(CommodityScoreQueryRequest commodityScoreQueryRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        commodityScoreQueryRequest.setUserId(loginUser.getId());
        return listCommodityScoreVOByPage(commodityScoreQueryRequest, request);
    }


    @Override
    public void validCommodityScore(CommodityScore commodityScore, boolean add) {
        ThrowUtils.throwIf(commodityScore == null, ErrorCode.PARAMS_ERROR);
        Long commodityId = commodityScore.getCommodityId();
        Long userId = commodityScore.getUserId();
        Integer score = commodityScore.getScore();
        if (add) {
            ThrowUtils.throwIf(commodityId == null, ErrorCode.PARAMS_ERROR, "商品id不能为空");
            ThrowUtils.throwIf(userId==null,ErrorCode.PARAMS_ERROR,"评价者id不能为空");
        }
        ThrowUtils.throwIf(score == null, ErrorCode.PARAMS_ERROR, "评分不能为空");
        ThrowUtils.throwIf((score < 1 || score > 5), ErrorCode.PARAMS_ERROR, "评分范围应为1到5");
        ThrowUtils.throwIf(commodityId != null && commodityId <= 0, ErrorCode.PARAMS_ERROR, "商品id非法");
        ThrowUtils.throwIf(userId != null && userId <= 0, ErrorCode.PARAMS_ERROR, "用户id非法");

    }

    @Override
    public Double getAverageScoreById(Long commodityId) {
        return commodityScoreMapper.getAverageScoreById(commodityId);
    }

    private CommodityScoreVO getCommodityScoreVO(CommodityScore commodityScore) {
        return CommodityScoreVO.objToVo(commodityScore);
    }
}
