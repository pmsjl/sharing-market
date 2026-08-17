package com.pmsjl.service.Impl;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.AiInternalToolException;
import com.pmsjl.model.dto.ai.internal.PostRagSnapshotResponse;
import com.pmsjl.model.entity.Post;
import com.pmsjl.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRagSnapshotServiceImplTest {

    @Mock
    private PostService postService;

    @Mock
    private HttpServletRequest request;

    private AiRagSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRagSnapshotServiceImpl();
        AiAgentProperties properties = new AiAgentProperties();
        properties.setInternalToken("internal-token");
        service.aiAgentProperties = properties;
        service.postService = postService;
        service.aiPostRagService = new AiPostRagServiceImpl();
    }

    @Test
    void exportsOnlyEligibleRowsAndAdvancesByLastScannedId() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(postService.listRagSnapshotCandidates(10L, 3)).thenReturn(List.of(
                post(11L, "宿舍显示器验货", "[\"数码\"]", 1000L),
                post(12L, "古董交易经验", "[\"经验\"]", 2000L),
                post(13L, "下一页帖子", "[\"校园\"]", 3000L)
        ));

        PostRagSnapshotResponse response =
                service.listPostSnapshots(10L, 2, request);

        assertTrue(response.isHasMore());
        assertEquals(12L, response.getNextAfterId());
        assertEquals(1, response.getItems().size());
        assertEquals(11L, response.getItems().get(0).getId());
        assertEquals("1000", response.getItems().get(0).getSourceVersion());
        assertEquals(List.of("数码"), response.getItems().get(0).getTags());
    }

    @Test
    void rejectsExactDisallowedTagAndMalformedTags() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        Post deleted = post(24L, "已删除帖子", "[\"校园\"]", 4000L);
        deleted.setIsDelete(1);
        when(postService.listRagSnapshotCandidates(20L, 5)).thenReturn(List.of(
                post(21L, "普通标题", "[\"成人用品\"]", 1000L),
                post(22L, "普通标题", "not-json", 2000L),
                post(23L, "普通标题", "[\"避坑\",\"避坑\"]", 3000L),
                deleted
        ));

        PostRagSnapshotResponse response =
                service.listPostSnapshots(20L, 4, request);

        assertFalse(response.isHasMore());
        assertEquals(24L, response.getNextAfterId());
        assertEquals(1, response.getItems().size());
        assertEquals(List.of("避坑"), response.getItems().get(0).getTags());
    }

    @Test
    void rejectsInvalidTokenBeforeQueryingPosts() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("wrong-token");

        AiInternalToolException exception = assertThrows(
                AiInternalToolException.class,
                () -> service.listPostSnapshots(0L, 200, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verifyNoInteractions(postService);
    }

    @Test
    void validatesCursorAndPageSizeAfterAuthentication() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");

        AiInternalToolException cursorException = assertThrows(
                AiInternalToolException.class,
                () -> service.listPostSnapshots(-1L, 200, request)
        );
        AiInternalToolException limitException = assertThrows(
                AiInternalToolException.class,
                () -> service.listPostSnapshots(0L, 201, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, cursorException.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, limitException.getStatus());
        verifyNoInteractions(postService);
    }

    private Post post(
            Long id,
            String title,
            String tags,
            long updateMillis
    ) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(title);
        post.setContent("这是一篇符合导出要求的校园二手交易经验帖。");
        post.setTags(tags);
        post.setCreateTime(new Date(updateMillis - 100));
        post.setUpdateTime(new Date(updateMillis));
        return post;
    }
}
