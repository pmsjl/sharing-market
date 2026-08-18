package com.pmsjl.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.AiInternalToolException;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.internal.AiCommoditySearchItem;
import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.dto.ai.internal.PostVersionCandidate;
import com.pmsjl.model.dto.ai.internal.PostVersionValidationRequest;
import com.pmsjl.model.dto.ai.internal.PostVersionValidationResponse;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityType;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.enums.AiCommoditySortEnum;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.model.vo.CommoditySearchToolResponse;
import com.pmsjl.service.*;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
    @Autowired
    AiUserPreferenceService aiUserPreferenceService;
    @Autowired
    PostService postService;
    @Autowired
    AiPostRagService aiPostRagService;

    @Override
    public CommoditySearchToolResponse searchCommodities(CommoditySearchToolRequest commoditySearchToolRequest, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        validateInternalRequest(request);
        List<Long> categoryIds = commoditySearchToolRequest.getCategoryIds();
        List<Long> excludeCommodityIds = commoditySearchToolRequest.getExcludeCommodityIds();
        List<String> degrees = commoditySearchToolRequest.getDegrees();
        List<String> keywords = normalizeKeywords(
                commoditySearchToolRequest.getKeywords()
        );
        BigDecimal maxPrice = commoditySearchToolRequest.getMaxPrice();
        BigDecimal minPrice = commoditySearchToolRequest.getMinPrice();
        Integer limit = commoditySearchToolRequest.getLimit();
        if (limit == null || limit < 1 || limit > 40) {
            throw new AiInternalToolException(
                    HttpStatus.BAD_REQUEST,
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "limit 为必填且必须在 1 到 40 之间",
                    false
            );
        }

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
                                    wrapper.nested(lambdaQueryWrapper -> {
                                        lambdaQueryWrapper.like(Commodity::getCommodityName, firstKeyword)
                                                .or().like(Commodity::getCommodityDescription, firstKeyword);
                                    });
                                    for (int i = 1; i < keywords.size(); i++) {
                                        String keyword = keywords.get(i);
                                        wrapper.or(lambdaQueryWrapper -> {
                                            lambdaQueryWrapper.like(Commodity::getCommodityName, keyword)
                                                    .or().like(Commodity::getCommodityDescription, keyword);
                                        });
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
        response.setItems(buildAiCommoditySearchItems(records));

        return response;


    }

    @Override
    public UserPreferenceToolResponse getMyPreferenceSignals(
            Long userId,
            HttpServletRequest request
    ) {
        if (userId == null || userId <= 0) {
            throw new AiInternalToolException(
                    HttpStatus.BAD_REQUEST,
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "userId 不合法",
                    false
            );
        }

        AiMessage requestMessage = validateInternalRequest(request);
        if (!userId.equals(requestMessage.getUserId())) {
            throw new AiInternalToolException(
                    HttpStatus.FORBIDDEN,
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "requestId 不属于当前用户",
                    false
            );
        }

        return aiUserPreferenceService.buildPreferenceProfile(
                request.getHeader("X-Request-Id"),
                userId
        );
    }

    @Override
    public PostVersionValidationResponse validatePostVersions(
            PostVersionValidationRequest validationRequest,
            HttpServletRequest request
    ) {
        validateInternalRequest(request);
        if (validationRequest == null
                || validationRequest.getCandidates() == null
                || validationRequest.getCandidates().size() > 10) {
            throw invalidArguments("Post 版本校验候选最多 10 项");
        }

        Map<Long, PostVersionCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (PostVersionCandidate candidate : validationRequest.getCandidates()) {
            if (candidate == null
                    || candidate.getPostId() == null
                    || candidate.getPostId() <= 0
                    || StringUtils.isBlank(candidate.getSourceVersion())
                    || !candidate.getSourceVersion().matches("[1-9]\\d*")) {
                throw invalidArguments("Post 版本校验候选不合法");
            }
            uniqueCandidates.putIfAbsent(candidate.getPostId(), candidate);
        }

        Map<Long, Post> postsById = uniqueCandidates.isEmpty()
                ? Map.of()
                : postService.listByIds(uniqueCandidates.keySet()).stream()
                .collect(Collectors.toMap(Post::getId, post -> post));
        List<PostVersionCandidate> validCandidates = new ArrayList<>();
        for (PostVersionCandidate candidate : uniqueCandidates.values()) {
            Post post = postsById.get(candidate.getPostId());
            if (aiPostRagService.isEligible(post, candidate.getSourceVersion())) {
                validCandidates.add(candidate);
            }
        }

        PostVersionValidationResponse response =
                new PostVersionValidationResponse();
        response.setRequestId(request.getHeader("X-Request-Id"));
        response.setValidCandidates(validCandidates);
        return response;
    }

    private List<AiCommoditySearchItem> buildAiCommoditySearchItems(List<Commodity> records) {
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

        List<AiCommoditySearchItem> list = records.stream().map(commodity -> {
            AiCommoditySearchItem item = new AiCommoditySearchItem();
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
            case PRICE_ASC -> page.addOrder(OrderItem.asc("price"));

            case PRICE_DESC -> page.addOrder(OrderItem.desc("price"));

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

    private AiMessage validateInternalRequest(HttpServletRequest request) {
        String internalToken = request.getHeader("X-Internal-Token");
        String requestId = request.getHeader("X-Request-Id");

        if (aiAgentProperties.getInternalToken() == null
                || !aiAgentProperties.getInternalToken()
                .equals(internalToken)) {
            throw new AiInternalToolException(
                    HttpStatus.UNAUTHORIZED,
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "内部 Token 校验失败",
                    false
            );
        }
        if (StringUtils.isBlank(requestId)) {
            throw new AiInternalToolException(
                    HttpStatus.BAD_REQUEST,
                    "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                    "X-Request-Id 不能为空",
                    false
            );
        }

        AiMessage requestMessage = aiMessageService.lambdaQuery()
                .eq(AiMessage::getRequestId, requestId)
                .eq(
                        AiMessage::getRole,
                        AiMessageRoleEnum.USER.getValue()
                ).one();
        if (requestMessage == null) {
            throw new AiInternalToolException(
                    HttpStatus.FORBIDDEN,
                    "AI_JAVA_TOOL_UNAUTHORIZED",
                    "requestId 不属于有效 Agent 请求",
                    false
            );
        }
        return requestMessage;
    }

    private AiInternalToolException invalidArguments(String message) {
        return new AiInternalToolException(
                HttpStatus.BAD_REQUEST,
                "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                message,
                false
        );
    }
}
