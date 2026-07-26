package com.pmsjl.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.model.dto.ai.internal.AiCommodityItem;
import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.enums.AiCommoditySortEnum;
import com.pmsjl.model.vo.CommoditySearchToolResponse;
import com.pmsjl.service.AiInternalToolService;
import com.pmsjl.service.AiMessageService;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.CommodityTypeService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiInternalToolServiceImpl implements AiInternalToolService {
    @Autowired
    AiAgentProperties aiAgentProperties;
    @Autowired
    AiMessageService aiMessageService;
    @Autowired
    CommodityService commodityService;
    @Autowired
    CommodityTypeService commodityTypeService;

    @Override
    public CommoditySearchToolResponse searchCommodities(CommoditySearchToolRequest commoditySearchToolRequest, HttpServletRequest request) {
        String interToken = request.getHeader("X-Internal-Token");
        String requestId = request.getHeader("X-Request-Id");
        validateHeader(request);
        List<Long> categoryIds = commoditySearchToolRequest.getCategoryIds();
        List<Long> excludeCommodityIds = commoditySearchToolRequest.getExcludeCommodityIds();
        List<String> degrees = commoditySearchToolRequest.getDegrees();
        List<String> keywords = normalizeKeywords(
                commoditySearchToolRequest.getKeywords()
        );
        BigDecimal maxPrice = commoditySearchToolRequest.getMaxPrice();
        BigDecimal minPrice = commoditySearchToolRequest.getMinPrice();
        int limit = commoditySearchToolRequest.getLimit() == null
                ? 10
                : commoditySearchToolRequest.getLimit();

        AiCommoditySortEnum sortOrder =
                commoditySearchToolRequest.getSortBy() == null
                        ? AiCommoditySortEnum.RELEVANCE
                        : commoditySearchToolRequest.getSortBy();

        Page<Commodity> page = new Page<>(1, limit);

        addCommodityToolOrder(page, sortOrder);

        Page<Commodity> commodityPage =
                commodityService.lambdaQuery()
                        // Java固定追加的可售条件
                        .eq(Commodity::getIsListed, 1)
                        .gt(Commodity::getCommodityInventory, 0)

                        // 每个关键词匹配名称或描述，关键词之间使用 OR
                        .and(
                                CollUtil.isNotEmpty(keywords),
                                wrapper -> {
                                    String firstKeyword = keywords.get(0);
                                    wrapper.nested(term -> term
                                            .like(
                                                    Commodity::getCommodityName,
                                                    firstKeyword
                                            )
                                            .or()
                                            .like(
                                                    Commodity::getCommodityDescription,
                                                    firstKeyword
                                            )
                                    );

                                    for (int i = 1; i < keywords.size(); i++) {
                                        String keyword = keywords.get(i);
                                        wrapper.or(term -> term
                                                .like(
                                                        Commodity::getCommodityName,
                                                        keyword
                                                )
                                                .or()
                                                .like(
                                                        Commodity::getCommodityDescription,
                                                        keyword
                                                )
                                        );
                                    }
                                }
                        )

                        // 分类筛选
                        .in(
                                CollUtil.isNotEmpty(categoryIds),
                                Commodity::getCommodityTypeId,
                                categoryIds
                        )

                        // 成色筛选
                        .in(
                                CollUtil.isNotEmpty(degrees),
                                Commodity::getDegree,
                                degrees
                        )

                        // 价格范围
                        .ge(
                                minPrice != null,
                                Commodity::getPrice,
                                minPrice
                        )
                        .le(
                                maxPrice != null,
                                Commodity::getPrice,
                                maxPrice
                        )

                        // 排除已经推荐过的商品
                        .notIn(
                                CollUtil.isNotEmpty(excludeCommodityIds),
                                Commodity::getId,
                                excludeCommodityIds
                        )

                        // 执行count和limit查询
                        .page(page);

        List<Commodity> records = commodityPage.getRecords();
        CommoditySearchToolResponse response =
                new CommoditySearchToolResponse();

        response.setRequestId(requestId);
        response.setMatchedCount(commodityPage.getTotal());
        response.setItems(buildAiCommodityItems(records));

        return response;


    }

    private List<AiCommodityItem> buildAiCommodityItems(List<Commodity> records) {
        Set<Long> commodityTypeIds = records.stream()
                .map(Commodity::getCommodityTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> commodityTypeNameMap = commodityTypeIds.isEmpty()
                ? Map.of()
                : commodityTypeService.listByIds(commodityTypeIds).stream()
                .collect(Collectors.toMap(
                        CommodityType::getId,
                        CommodityType::getTypeName
                ));

        List<AiCommodityItem> list = records.stream().map(commodity -> {
            AiCommodityItem item = new AiCommodityItem();
            BeanUtils.copyProperties(commodity, item);
            item.setCommodityTypeName(
                    commodityTypeNameMap.get(commodity.getCommodityTypeId())
            );
            return item;
        }).toList();
        return list;
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (CollUtil.isEmpty(keywords)) {
            return List.of();
        }

        ThrowUtils.throwIf(
                keywords.size() > 5,
                ErrorCode.PARAMS_ERROR,
                "搜索关键词最多 5 个"
        );

        List<String> normalizedKeywords = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();

        ThrowUtils.throwIf(
                normalizedKeywords.stream()
                        .anyMatch(keyword -> keyword.length() > 30),
                ErrorCode.PARAMS_ERROR,
                "单个搜索关键词最多 30 个字符"
        );
        return normalizedKeywords;
    }


    private void addCommodityToolOrder(
            Page<Commodity> page,
            AiCommoditySortEnum sortOrder) {

        switch (sortOrder) {
            case PRICE_ASC ->
                    page.addOrder(OrderItem.asc("price"));

            case PRICE_DESC ->
                    page.addOrder(OrderItem.desc("price"));

            case FAVOUR_DESC -> {
                page.addOrder(OrderItem.desc("favourNum"));
                page.addOrder(OrderItem.desc("createTime"));
            }

            case RELEVANCE -> {
                page.addOrder(OrderItem.desc("favourNum"));
                page.addOrder(OrderItem.desc("viewNum"));
                page.addOrder(OrderItem.desc("createTime"));
            }
        }
    }

    private void validateHeader(HttpServletRequest request) {
        String interToken = request.getHeader("X-Internal-Token");
        String requestId = request.getHeader("X-Request-Id");
        ThrowUtils.throwIf(!aiAgentProperties.getInternalToken().equals(interToken), ErrorCode.PARAMS_ERROR, "内部token不一致");
        boolean result = aiMessageService.lambdaQuery().eq(AiMessage::getRequestId, requestId).exists();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "对话不存在");
    }
}
