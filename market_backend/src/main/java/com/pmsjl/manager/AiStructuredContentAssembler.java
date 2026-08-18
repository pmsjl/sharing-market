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
 * 将 Python Agent 返回的内部结构转换为可安全保存并返回前端的结构化内容。
 */
@Component
public class AiStructuredContentAssembler {

    private static final int MAX_SOURCE_COUNT = 8;
    private static final int MAX_SOURCE_ID_LENGTH = 150;
    private static final int MAX_DOCUMENT_ID_LENGTH = 150;
    private static final int MAX_SOURCE_TITLE_LENGTH = 200;
    private static final int MAX_CITATION_COUNT = 2;
    private static final int MAX_CHUNK_ID_LENGTH = 200;
    private static final int MAX_SECTION_LENGTH = 200;
    private static final int MAX_CITATION_EXCERPT_LENGTH = 300;
    private static final int MAX_CITATION_CONTENT_LENGTH = 1200;

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
     * GUIDE 已由 Python 依据本轮检索结果校验；Java 只执行严格的展示白名单。
     */
    private List<AiRagSourceVO> buildSources(
            AgentOutput output,
            Map<Long, Post> postsById
    ) {
        List<AiRagSourceVO> results = new ArrayList<>();
        Set<String> documentIds = new LinkedHashSet<>();
        for (AgentSource source : safeList(output.getSources())) {
            if (source == null
                    || (!"GUIDE".equals(source.getSourceType())
                    && !"POST".equals(source.getSourceType()))) {
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
            if (citations.isEmpty()
                    || citations.stream().anyMatch(
                    citation -> !citation.getChunkId().startsWith(
                            source.getDocumentId() + "#"
                    )
            )
                    || !documentIds.add(source.getDocumentId())) {
                continue;
            }

            AiRagSourceVO item = new AiRagSourceVO();
            if ("POST".equals(source.getSourceType())) {
                Long postId = parsePositiveLong(source.getSourceId());
                Post post = postId == null ? null : postsById.get(postId);
                if (postId == null
                        || !source.getDocumentId().equals("POST:" + postId)
                        || !aiPostRagService.isEligible(
                            post,
                            source.getSourceVersion()
                        )) {
                    documentIds.remove(source.getDocumentId());
                    continue;
                }
                item.setSourceType("POST");
                item.setSourceId(Long.toString(postId));
                item.setDocumentId("POST:" + postId);
                item.setTitle(post.getTitle());
                item.setTargetPath("/user/post/" + postId);
            } else {
                item.setSourceType("GUIDE");
                item.setSourceId(source.getSourceId());
                item.setDocumentId(source.getDocumentId());
                item.setTitle(source.getTitle());
                item.setTargetPath(null);
            }
            item.setCitations(citations);
            results.add(item);
            if (results.size() >= MAX_SOURCE_COUNT) {
                break;
            }
        }
        return results;
    }

    private Map<Long, Post> loadReferencedPosts(AgentOutput output) {
        Set<Long> postIds = new LinkedHashSet<>();
        for (AgentSource source : safeList(output.getSources())) {
            if (source != null && "POST".equals(source.getSourceType())) {
                Long postId = parsePositiveLong(source.getSourceId());
                if (postId != null) {
                    postIds.add(postId);
                }
            }
        }
        for (AgentRelatedPostCandidate candidate
                : safeList(output.getRelatedPostCandidates())) {
            if (candidate != null
                    && candidate.getPostId() != null
                    && candidate.getPostId() > 0) {
                postIds.add(candidate.getPostId());
            }
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
        Set<Long> seenPostIds = new LinkedHashSet<>();
        for (AgentRelatedPostCandidate candidate
                : safeList(output.getRelatedPostCandidates())) {
            if (candidate == null
                    || candidate.getPostId() == null
                    || candidate.getPostId() <= 0
                    || !seenPostIds.add(candidate.getPostId())) {
                continue;
            }
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
            if (results.size() >= 3) {
                break;
            }
        }
        return results;
    }

    private String cleanPostExcerpt(String content, int maxLength) {
        String normalized = StringUtils.normalizeSpace(content);
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
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
