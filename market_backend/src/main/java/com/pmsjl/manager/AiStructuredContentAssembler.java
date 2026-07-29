package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.vo.AiRecommendationVO;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 Python Agent 返回的内部结构转换为可安全保存并返回前端的结构化内容。
 */
@Component
public class AiStructuredContentAssembler {

    private final CommodityService commodityService;

    public AiStructuredContentAssembler(CommodityService commodityService) {
        this.commodityService = commodityService;
    }

    /**
     * 使用数据库中的实时商品记录替换模型返回的商品 ID。
     */
    public AiStructuredContentVO assemble(AgentOutput output) {
        Map<Long, Commodity> validCommodities = loadValidCommodities(output);

        AiStructuredContentVO result = new AiStructuredContentVO();
        result.setIntent(output.getIntent());
        result.setSummary(output.getSummary());
        result.setPurchaseAdvice(safeList(output.getPurchaseAdvice()));
        result.setWarnings(safeList(output.getWarnings()));
        result.setSearchKeywords(safeList(output.getSearchKeywords()));
        result.setRecommendations(buildRecommendations(output, validCommodities));
        return result;
    }

    /**
     * 只保留保存回复时仍然上架且库存大于零的商品。
     */
    private Map<Long, Commodity> loadValidCommodities(AgentOutput output) {
        Set<Long> commodityIds = new LinkedHashSet<>();
        for (AgentRecommendation recommendation : safeList(output.getRecommendations())) {
            if (recommendation.getCommodityId() != null) {
                commodityIds.add(recommendation.getCommodityId());
            }
        }

        Map<Long, Commodity> validCommodities = new HashMap<>();
        if (commodityIds.isEmpty()) {
            return validCommodities;
        }

        for (Commodity commodity : commodityService.listByIds(commodityIds)) {
            boolean listed = Integer.valueOf(1).equals(commodity.getIsListed());
            boolean inStock = commodity.getCommodityInventory() != null
                    && commodity.getCommodityInventory() > 0;
            if (listed && inStock) {
                validCommodities.put(commodity.getId(), commodity);
            }
        }
        return validCommodities;
    }

    /**
     * 按模型给出的推荐顺序生成前端商品卡片，并丢弃已经失效的商品。
     */
    private List<AiRecommendationVO> buildRecommendations(
            AgentOutput output,
            Map<Long, Commodity> validCommodities) {
        List<AiRecommendationVO> results = new ArrayList<>();
        for (AgentRecommendation recommendation : safeList(output.getRecommendations())) {
            Commodity commodity = validCommodities.get(recommendation.getCommodityId());
            if (commodity == null) {
                continue;
            }

            AiRecommendationVO item = new AiRecommendationVO();
            item.setCommodity(CommodityVO.objToVo(commodity));
            item.setMatchScore(recommendation.getMatchScore());
            item.setReason(recommendation.getReason());
            item.setRiskTip(recommendation.getRiskTip());
            results.add(item);
        }
        return results;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
