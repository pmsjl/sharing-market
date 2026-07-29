package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.enums.AiIntentEnum;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.service.CommodityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStructuredContentAssemblerTest {

    @Mock
    private CommodityService commodityService;

    @Test
    void assembleHydratesValidCommoditiesAndPreservesRecommendationOrder() {
        AgentOutput output = outputWithRecommendations(
                recommendation(2L, 92),
                recommendation(1L, 88),
                recommendation(3L, 80),
                recommendation(4L, 75)
        );
        when(commodityService.listByIds(anyCollection())).thenReturn(List.of(
                commodity(1L, 1, 2),
                commodity(2L, 1, 1),
                commodity(3L, 0, 5),
                commodity(4L, 1, 0)
        ));

        AiStructuredContentVO result =
                new AiStructuredContentAssembler(commodityService).assemble(output);

        assertEquals(2, result.getRecommendations().size());
        assertEquals(2L, result.getRecommendations().get(0).getCommodity().getId());
        assertEquals(1L, result.getRecommendations().get(1).getCommodity().getId());
        assertEquals(92, result.getRecommendations().get(0).getMatchScore());
        assertEquals("推荐理由 2", result.getRecommendations().get(0).getReason());
        assertEquals(List.of("当面验货"), result.getPurchaseAdvice());
        assertEquals(List.of("库存可能变化"), result.getWarnings());
        assertEquals(List.of("平板"), result.getSearchKeywords());
        assertTrue(result.getSources().isEmpty());
    }

    @Test
    void assembleDoesNotQueryCommodityServiceWithoutRecommendations() {
        AgentOutput output = new AgentOutput();
        output.setIntent(AiIntentEnum.GENERAL_GUIDE);
        output.setSummary("通用建议");

        AiStructuredContentVO result =
                new AiStructuredContentAssembler(commodityService).assemble(output);

        assertTrue(result.getRecommendations().isEmpty());
        assertTrue(result.getPurchaseAdvice().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getSearchKeywords().isEmpty());
        verifyNoInteractions(commodityService);
    }

    private static AgentOutput outputWithRecommendations(
            AgentRecommendation... recommendations) {
        AgentOutput output = new AgentOutput();
        output.setIntent(AiIntentEnum.COMMODITY_RECOMMENDATION);
        output.setSummary("推荐结果");
        output.setRecommendations(List.of(recommendations));
        output.setPurchaseAdvice(List.of("当面验货"));
        output.setWarnings(List.of("库存可能变化"));
        output.setSearchKeywords(List.of("平板"));
        return output;
    }

    private static AgentRecommendation recommendation(Long commodityId,
                                                       int matchScore) {
        AgentRecommendation recommendation = new AgentRecommendation();
        recommendation.setCommodityId(commodityId);
        recommendation.setMatchScore(matchScore);
        recommendation.setReason("推荐理由 " + commodityId);
        recommendation.setRiskTip("风险提示 " + commodityId);
        return recommendation;
    }

    private static Commodity commodity(Long id,
                                       int isListed,
                                       int inventory) {
        Commodity commodity = new Commodity();
        commodity.setId(id);
        commodity.setCommodityName("商品 " + id);
        commodity.setIsListed(isListed);
        commodity.setCommodityInventory(inventory);
        return commodity;
    }
}
