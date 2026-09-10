package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentCitation;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.dto.ai.internal.AgentSource;
import com.pmsjl.model.dto.ai.internal.AgentRelatedPostCandidate;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotItem;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.vo.AiRelatedPostVO;
import com.pmsjl.model.vo.AiRecommendationVO;
import com.pmsjl.model.vo.AiRagCitationVO;
import com.pmsjl.model.vo.AiRagSourceVO;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.AiPostRagService;
import com.pmsjl.service.PostService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 Python 已校验的内部结果映射为展示结构，并复核实时业务状态。
 */
@Component
public class AiStructuredContentAssembler {

    private final CommodityService commodityService;
    private final PostService postService;
    private final AiPostRagService aiPostRagService;

    public AiStructuredContentAssembler(
            CommodityService commodityService,
            PostService postService,
            AiPostRagService aiPostRagService
    ) {
        this.commodityService = commodityService;
        this.postService = postService;
        this.aiPostRagService = aiPostRagService;
    }

    /**
     * 使用数据库中的实时商品记录替换模型返回的商品 ID。
     */
    public AiStructuredContentVO assemble(AgentOutput output) {
        Map<Long, Commodity> validCommodities = loadValidCommodities(output);
        Map<Long, Post> postsById = loadReferencedPosts(output);

        AiStructuredContentVO result = new AiStructuredContentVO();
        result.setIntent(output.getIntent());
        result.setSummary(output.getSummary());
        result.setPurchaseAdvice(safeList(output.getPurchaseAdvice()));
        result.setWarnings(safeList(output.getWarnings()));
        result.setSearchKeywords(safeList(output.getSearchKeywords()));
        result.setRecommendations(buildRecommendations(output, validCommodities));
        result.setSources(buildSources(output, postsById));
        result.setRelatedPosts(buildRelatedPosts(output, postsById));
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
     * 来源及引用已由 Python 组装和校验；Java 仅复核 POST 的实时可用性与版本。
     */
    private List<AiRagSourceVO> buildSources(
            AgentOutput output,
            Map<Long, Post> postsById
    ) {
        List<AiRagSourceVO> results = new ArrayList<>();
        for (AgentSource source : safeList(output.getSources())) {
            AiRagSourceVO item = new AiRagSourceVO();
            if ("POST".equals(source.getSourceType())) {
                Long postId = parsePostId(source.getSourceId());
                if (postId == null
                        || !aiPostRagService.isEligible(
                            postsById.get(postId), source.getSourceVersion())) {
                    continue;
                }
                item.setTargetPath("/user/post/" + postId);
            } else {
                item.setTargetPath(null);
            }
            item.setSourceType(source.getSourceType());
            item.setSourceId(source.getSourceId());
            item.setDocumentId(source.getDocumentId());
            item.setTitle(source.getTitle());
            item.setCitations(buildCitations(source.getCitations()));
            results.add(item);
        }
        return results;
    }

    private Map<Long, Post> loadReferencedPosts(AgentOutput output) {
        Set<Long> postIds = new LinkedHashSet<>();
        for (AgentSource source : safeList(output.getSources())) {
            if ("POST".equals(source.getSourceType())) {
                Long postId = parsePostId(source.getSourceId());
                if (postId != null) {
                    postIds.add(postId);
                }
            }
        }
        for (AgentRelatedPostCandidate candidate
                : safeList(output.getRelatedPostCandidates())) {
            postIds.add(candidate.getPostId());
        }
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postService.listByIds(postIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Post::getId,
                        post -> post
                ));
    }

    private List<AiRelatedPostVO> buildRelatedPosts(
            AgentOutput output,
            Map<Long, Post> postsById
    ) {
        List<AiRelatedPostVO> results = new ArrayList<>();
        for (AgentRelatedPostCandidate candidate
                : safeList(output.getRelatedPostCandidates())) {
            Post post = postsById.get(candidate.getPostId());
            PostRagSnapshotItem snapshot =
                    aiPostRagService.toSnapshotItem(post);
            if (snapshot == null
                    || !snapshot.getSourceVersion().equals(
                        candidate.getSourceVersion()
                    )) {
                continue;
            }

            AiRelatedPostVO item = new AiRelatedPostVO();
            item.setPostId(post.getId());
            item.setTitle(post.getTitle());
            item.setExcerpt(cleanPostExcerpt(post.getContent(), 180));
            item.setTags(snapshot.getTags().stream().limit(5).toList());
            results.add(item);
        }
        return results;
    }

    private String cleanPostExcerpt(String content, int maxLength) {
        String normalized = StringUtils.normalizeSpace(content);
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private Long parsePostId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<AiRagCitationVO> buildCitations(List<AgentCitation> values) {
        List<AiRagCitationVO> results = new ArrayList<>();
        for (AgentCitation citation : safeList(values)) {
            AiRagCitationVO item = new AiRagCitationVO();
            item.setChunkId(citation.getChunkId());
            item.setSection(citation.getSection());
            item.setExcerpt(citation.getExcerpt());
            item.setContent(citation.getContent());
            results.add(item);
        }
        return results;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
