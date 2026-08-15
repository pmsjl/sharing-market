package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentCitation;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.dto.ai.internal.AgentSource;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.vo.AiRecommendationVO;
import com.pmsjl.model.vo.AiRagCitationVO;
import com.pmsjl.model.vo.AiRagSourceVO;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import org.apache.commons.lang3.StringUtils;
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

    private static final int MAX_SOURCE_COUNT = 5;
    private static final int MAX_SOURCE_ID_LENGTH = 150;
    private static final int MAX_DOCUMENT_ID_LENGTH = 150;
    private static final int MAX_SOURCE_TITLE_LENGTH = 200;
    private static final int MAX_CITATION_COUNT = 5;
    private static final int MAX_CHUNK_ID_LENGTH = 200;
    private static final int MAX_SECTION_LENGTH = 200;
    private static final int MAX_CITATION_EXCERPT_LENGTH = 300;
    private static final int MAX_CITATION_CONTENT_LENGTH = 1200;

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
        result.setSources(buildSources(output));
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

    /**
     * GUIDE 已由 Python 依据本轮检索结果校验；Java 只执行严格的展示白名单。
     */
    private List<AiRagSourceVO> buildSources(AgentOutput output) {
        List<AiRagSourceVO> results = new ArrayList<>();
        Set<String> documentIds = new LinkedHashSet<>();
        for (AgentSource source : safeList(output.getSources())) {
            if (source == null || !"GUIDE".equals(source.getSourceType())) {
                continue;
            }
            if (StringUtils.isAnyBlank(
                    source.getSourceId(), source.getDocumentId(), source.getTitle())) {
                continue;
            }
            if (source.getSourceId().length() > MAX_SOURCE_ID_LENGTH
                    || source.getDocumentId().length() > MAX_DOCUMENT_ID_LENGTH
                    || source.getTitle().length() > MAX_SOURCE_TITLE_LENGTH
                    || documentIds.contains(source.getDocumentId())) {
                continue;
            }

            List<AiRagCitationVO> citations = buildCitations(source.getCitations());
            if (citations.isEmpty() || !documentIds.add(source.getDocumentId())) {
                continue;
            }

            AiRagSourceVO item = new AiRagSourceVO();
            item.setSourceType("GUIDE");
            item.setSourceId(source.getSourceId());
            item.setDocumentId(source.getDocumentId());
            item.setTitle(source.getTitle());
            item.setCitations(citations);
            // 首版没有 GUIDE 详情页，禁止伪造站内跳转地址。
            item.setTargetPath(null);
            results.add(item);
            if (results.size() >= MAX_SOURCE_COUNT) {
                break;
            }
        }
        return results;
    }

    private List<AiRagCitationVO> buildCitations(List<AgentCitation> values) {
        List<AiRagCitationVO> results = new ArrayList<>();
        Set<String> chunkIds = new LinkedHashSet<>();
        for (AgentCitation citation : safeList(values)) {
            if (citation == null || StringUtils.isAnyBlank(
                    citation.getChunkId(), citation.getExcerpt(), citation.getContent())) {
                continue;
            }
            String section = StringUtils.trimToNull(citation.getSection());
            if (citation.getChunkId().length() > MAX_CHUNK_ID_LENGTH
                    || (section != null && section.length() > MAX_SECTION_LENGTH)
                    || citation.getExcerpt().length() > MAX_CITATION_EXCERPT_LENGTH
                    || citation.getContent().length() > MAX_CITATION_CONTENT_LENGTH
                    || !chunkIds.add(citation.getChunkId())) {
                continue;
            }

            AiRagCitationVO item = new AiRagCitationVO();
            item.setChunkId(citation.getChunkId());
            item.setSection(section);
            item.setExcerpt(citation.getExcerpt());
            item.setContent(citation.getContent());
            results.add(item);
            if (results.size() >= MAX_CITATION_COUNT) {
                break;
            }
        }
        return results;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
