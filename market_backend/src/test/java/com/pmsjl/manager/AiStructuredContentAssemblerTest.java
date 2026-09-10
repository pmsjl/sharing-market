package com.pmsjl.manager;

import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentCitation;
import com.pmsjl.model.dto.ai.internal.AgentRecommendation;
import com.pmsjl.model.dto.ai.internal.AgentSource;
import com.pmsjl.model.dto.ai.internal.AgentRelatedPostCandidate;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotItem;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.enums.AiIntentEnum;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.PostService;
import com.pmsjl.service.AiPostRagService;
import com.pmsjl.service.Impl.AiPostRagServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStructuredContentAssemblerTest {

    @Mock
    private CommodityService commodityService;
    @Mock
    private PostService postService;
    @Mock
    private AiPostRagService aiPostRagService;

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
                assembler().assemble(output);

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
                assembler().assemble(output);

        assertTrue(result.getRecommendations().isEmpty());
        assertTrue(result.getPurchaseAdvice().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getSearchKeywords().isEmpty());
        verifyNoInteractions(commodityService);
    }

    @Test
    void assembleMapsValidatedGuideSourcesInOrder() {
        AgentOutput output = new AgentOutput();
        AgentSource firstSource = source(
                "GUIDE", "2", "来源 2", "摘录 1", "完整正文 1");
        firstSource.setCitations(List.of(
                citation("GUIDE:2#first", null, "摘录 1", "完整正文 1"),
                citation("GUIDE:2#second", "第二章节", "摘录 2", "第一段\n第二段")
        ));
        output.setSources(List.of(
                firstSource, source("GUIDE", "1", "来源 1", "摘录")));

        AiStructuredContentVO result = assembler().assemble(output);

        assertEquals(List.of("2", "1"),
                result.getSources().stream().map(item -> item.getSourceId()).toList());
        assertEquals("来源 2", result.getSources().get(0).getTitle());
        assertEquals("GUIDE:2", result.getSources().get(0).getDocumentId());
        assertTrue(result.getSources().stream()
                .allMatch(item -> "GUIDE".equals(item.getSourceType())));
        assertTrue(result.getSources().stream()
                .allMatch(item -> item.getTargetPath() == null));
        var citations = result.getSources().get(0).getCitations();
        assertEquals(2, citations.size());
        assertEquals("GUIDE:2#first", citations.get(0).getChunkId());
        assertNull(citations.get(0).getSection());
        assertEquals("摘录 1", citations.get(0).getExcerpt());
        assertEquals("完整正文 1", citations.get(0).getContent());
        assertEquals("GUIDE:2#second", citations.get(1).getChunkId());
        assertEquals("第二章节", citations.get(1).getSection());
        assertEquals("第一段\n第二段", citations.get(1).getContent());
        assertNull(result.getSources().get(0).getContent());
        verifyNoInteractions(commodityService, postService);
    }

    @Test
    void assemblePreservesPythonCodePointLengthBoundaries() {
        AgentOutput output = new AgentOutput();
        String title = "😀".repeat(200);
        String excerpt = "😀".repeat(300);
        String content = "😀".repeat(1200);
        AgentSource source = source("GUIDE", "emoji", title, excerpt, content);
        output.setSources(List.of(source));

        AiStructuredContentVO result = assembler().assemble(output);

        assertEquals(1, result.getSources().size());
        assertEquals(title, result.getSources().get(0).getTitle());
        assertEquals(excerpt, result.getSources().get(0).getCitations().get(0).getExcerpt());
        assertEquals(content, result.getSources().get(0).getCitations().get(0).getContent());
    }

    @Test
    void assembleTreatsNullListsAsEmpty() {
        AgentOutput output = new AgentOutput();
        output.setRecommendations(null);
        output.setSources(null);
        output.setRelatedPostCandidates(null);
        output.setPurchaseAdvice(null);
        output.setWarnings(null);
        output.setSearchKeywords(null);

        AiStructuredContentVO result = assembler().assemble(output);

        assertTrue(result.getRecommendations().isEmpty());
        assertTrue(result.getSources().isEmpty());
        assertTrue(result.getRelatedPosts().isEmpty());
        assertTrue(result.getPurchaseAdvice().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getSearchKeywords().isEmpty());
        verifyNoInteractions(commodityService, postService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-long", "9223372036854775808"})
    void assembleSkipsPostIdsThatCannotBeConvertedToLong(String id) {
        AgentOutput output = new AgentOutput();
        output.setSources(List.of(source("POST", id, "标题", "摘录")));

        assertTrue(assembler().assemble(output).getSources().isEmpty());
        verifyNoInteractions(postService, aiPostRagService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "valid", "missing", "deleted", "blank-title", "blank-content",
            "invalid-tags", "disallowed-topic", "missing-update-time", "stale-version"
    })
    void assembleRechecksLivePostStateForSourcesAndCards(String state) {
        Post post = new Post();
        post.setId(11L);
        post.setTitle("数据库标题");
        post.setContent("正文".repeat(100));
        post.setTags("[\"数码\",\"验货\",\"电脑\",\"手机\",\"教材\",\"校园\"]");
        post.setCreateTime(new Date(500));
        post.setUpdateTime(new Date(1000));
        post.setIsDelete(0);
        switch (state) {
            case "deleted" -> post.setIsDelete(1);
            case "blank-title" -> post.setTitle(" ");
            case "blank-content" -> post.setContent(" ");
            case "invalid-tags" -> post.setTags("not-json");
            case "disallowed-topic" -> post.setTitle("古董");
            case "missing-update-time" -> post.setUpdateTime(null);
            case "stale-version" -> post.setUpdateTime(new Date(2000));
        }
        when(postService.listByIds(anyCollection()))
                .thenReturn("missing".equals(state) ? List.of() : List.of(post));
        AgentSource source = source("POST", "11", "Python 清洗后的标题", "摘录");
        source.setSourceVersion("1000");
        AgentOutput output = new AgentOutput();
        output.setSources(List.of(source));
        output.setRelatedPostCandidates(List.of(relatedCandidate(11L, "1000")));
        AiStructuredContentAssembler assembler = new AiStructuredContentAssembler(
                commodityService, postService, new AiPostRagServiceImpl());

        AiStructuredContentVO result = assembler.assemble(output);

        if ("valid".equals(state)) {
            assertEquals(1, result.getSources().size());
            assertEquals("Python 清洗后的标题", result.getSources().get(0).getTitle());
            assertEquals(1, result.getRelatedPosts().size());
            assertEquals("数据库标题", result.getRelatedPosts().get(0).getTitle());
            assertEquals("正文".repeat(90), result.getRelatedPosts().get(0).getExcerpt());
            assertEquals(List.of("数码", "验货", "电脑", "手机", "教材"),
                    result.getRelatedPosts().get(0).getTags());
        } else {
            assertTrue(result.getSources().isEmpty());
            assertTrue(result.getRelatedPosts().isEmpty());
        }
        verify(postService).listByIds(Set.of(11L));
        verifyNoMoreInteractions(postService);
    }

    @Test
    void assembleRevalidatesPostSourceAndHydratesRelatedPostFromDatabase() {
        AgentOutput output = new AgentOutput();
        output.setIntent(AiIntentEnum.GENERAL_GUIDE);
        output.setSummary("帖子建议");
        AgentSource postSource = source(
                "POST", "11", "Python 提供的标题", "引用摘录", "引用正文"
        );
        postSource.setSourceVersion("1000");
        output.setSources(List.of(postSource));
        output.setRelatedPostCandidates(List.of(
                relatedCandidate(11L, "1000")
        ));

        Post post = new Post();
        post.setId(11L);
        post.setTitle("数据库中的当前标题");
        post.setContent("这是数据库里的当前帖子正文，用于生成相关帖子摘要。");
        post.setTags("[\"数码\",\"验货\"]");
        when(postService.listByIds(anyCollection())).thenReturn(List.of(post));
        when(aiPostRagService.isEligible(post, "1000")).thenReturn(true);
        PostRagSnapshotItem snapshot = new PostRagSnapshotItem();
        snapshot.setSourceVersion("1000");
        snapshot.setTags(List.of("数码", "验货"));
        when(aiPostRagService.toSnapshotItem(post)).thenReturn(snapshot);

        AiStructuredContentVO result = assembler().assemble(output);

        assertEquals(1, result.getSources().size());
        assertEquals("POST", result.getSources().get(0).getSourceType());
        assertEquals("Python 提供的标题", result.getSources().get(0).getTitle());
        assertEquals("/user/post/11", result.getSources().get(0).getTargetPath());
        assertEquals(1, result.getRelatedPosts().size());
        assertEquals(11L, result.getRelatedPosts().get(0).getPostId());
        assertEquals("数据库中的当前标题",
                result.getRelatedPosts().get(0).getTitle());
        assertEquals(List.of("数码", "验货"),
                result.getRelatedPosts().get(0).getTags());
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

    private AiStructuredContentAssembler assembler() {
        return new AiStructuredContentAssembler(
                commodityService,
                postService,
                aiPostRagService
        );
    }

    private static AgentRelatedPostCandidate relatedCandidate(
            Long postId,
            String sourceVersion
    ) {
        AgentRelatedPostCandidate candidate = new AgentRelatedPostCandidate();
        candidate.setPostId(postId);
        candidate.setSourceVersion(sourceVersion);
        return candidate;
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
        return sourceWithDocumentId(
                type,
                id,
                type + ":" + id,
                title,
                excerpt,
                content
        );
    }

    private static AgentSource sourceWithDocumentId(String type,
                                                    String id,
                                                    String documentId,
                                                    String title,
                                                    String excerpt,
                                                    String content) {
        AgentSource source = new AgentSource();
        source.setSourceType(type);
        source.setSourceId(id);
        source.setDocumentId(documentId);
        source.setTitle(title);
        source.setCitations(List.of(citation(
                documentId + "#test",
                "测试章节",
                excerpt,
                content == null ? excerpt : content
        )));
        return source;
    }

    private static AgentCitation citation(String chunkId,
                                           String section,
                                           String excerpt,
                                           String content) {
        AgentCitation citation = new AgentCitation();
        citation.setChunkId(chunkId);
        citation.setSection(section);
        citation.setExcerpt(excerpt);
        citation.setContent(content);
        return citation;
    }
}
