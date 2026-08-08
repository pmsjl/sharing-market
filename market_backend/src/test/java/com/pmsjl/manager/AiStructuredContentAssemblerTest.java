package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.dto.ai.internal.AgentSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void assembleAcceptsOnlyBoundedUniqueGuideSources() {
        AgentOutput output = new AgentOutput();
        output.setIntent(AiIntentEnum.GENERAL_GUIDE);
        output.setSummary("指南回答");
        output.setSources(List.of(
                source("POST", "POST:1", "帖子", "不受支持"),
                source("GUIDE", "", "空 ID", "无效"),
                source("GUIDE", "x".repeat(151), "过长 ID", "无效"),
                source("GUIDE", "GUIDE:long-title", "题".repeat(201), "无效"),
                source("GUIDE", "GUIDE:long-excerpt", "过长摘录", "摘".repeat(301)),
                source("GUIDE", "GUIDE:blank-content", "空正文", "有效摘录", " "),
                source("GUIDE", "GUIDE:long-content", "过长正文", "有效摘录", "正".repeat(1201)),
                source("GUIDE", "GUIDE:1", "来源 1", "摘录 1", "完整正文 1"),
                source("GUIDE", "GUIDE:1", "重复来源", "重复摘录"),
                source("GUIDE", "GUIDE:2", "来源 2", "摘录 2"),
                source("GUIDE", "GUIDE:3", "来源 3", "摘录 3"),
                source("GUIDE", "GUIDE:4", "来源 4", "摘录 4"),
                source("GUIDE", "GUIDE:5", "来源 5", "摘录 5"),
                source("GUIDE", "GUIDE:6", "来源 6", "摘录 6")
        ));

        AiStructuredContentVO result =
                new AiStructuredContentAssembler(commodityService).assemble(output);

        assertEquals(5, result.getSources().size());
        assertEquals(
                List.of("GUIDE:1", "GUIDE:2", "GUIDE:3", "GUIDE:4", "GUIDE:5"),
                result.getSources().stream().map(item -> item.getSourceId()).toList()
        );
        assertTrue(result.getSources().stream()
                .allMatch(item -> "GUIDE".equals(item.getSourceType())));
        assertTrue(result.getSources().stream()
                .allMatch(item -> item.getTargetPath() == null));
        assertEquals("完整正文 1", result.getSources().get(0).getContent());
        assertNull(result.getSources().get(1).getContent());
        assertNull(result.getSources().get(0).getTargetPath());
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

    private static AgentSource source(String type,
                                      String id,
                                      String title,
                                      String excerpt) {
        return source(type, id, title, excerpt, null);
    }

    private static AgentSource source(String type,
                                      String id,
                                      String title,
                                      String excerpt,
                                      String content) {
        AgentSource source = new AgentSource();
        source.setSourceType(type);
        source.setSourceId(id);
        source.setTitle(title);
        source.setExcerpt(excerpt);
        source.setContent(content);
        return source;
    }
}
