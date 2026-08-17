package com.pmsjl.service.Impl;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.AiInternalToolException;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.dto.ai.internal.PostVersionCandidate;
import com.pmsjl.model.dto.ai.internal.PostVersionValidationRequest;
import com.pmsjl.model.dto.ai.internal.PostVersionValidationResponse;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.service.AiUserPreferenceService;
import com.pmsjl.service.AiPostRagService;
import com.pmsjl.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AiInternalToolServiceImplTest {

    @Mock
    private AiMessageMapper aiMessageMapper;
    @Mock
    private AiUserPreferenceService preferenceService;
    @Mock
    private PostService postService;
    @Mock
    private AiPostRagService aiPostRagService;
    @Mock
    private HttpServletRequest request;

    private AiInternalToolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiInternalToolServiceImpl();
        AiAgentProperties properties = new AiAgentProperties();
        properties.setInternalToken("internal-token");
        service.aiAgentProperties = properties;
        service.aiMessageMapper = aiMessageMapper;
        service.aiUserPreferenceService = preferenceService;
        service.postService = postService;
        service.aiPostRagService = aiPostRagService;
    }

    @Test
    void bindsPreferenceRequestToMessageOwner() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");
        when(aiMessageMapper.selectOne(any()))
                .thenReturn(userMessage(7L));
        UserPreferenceToolResponse expected =
                new UserPreferenceToolResponse();
        when(preferenceService.buildPreferenceProfile("request-1", 7L))
                .thenReturn(expected);

        UserPreferenceToolResponse result =
                service.getMyPreferenceSignals(7L, request);

        assertSame(expected, result);
        verify(preferenceService)
                .buildPreferenceProfile("request-1", 7L);
    }

    @Test
    void rejectsPathUserThatDoesNotOwnRequest() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");
        when(aiMessageMapper.selectOne(any()))
                .thenReturn(userMessage(8L));

        AiInternalToolException exception = assertThrows(
                AiInternalToolException.class,
                () -> service.getMyPreferenceSignals(7L, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(preferenceService, never())
                .buildPreferenceProfile(any(), any());
    }

    @Test
    void rejectsInvalidInternalTokenBeforeReadingMessage() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("wrong-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");

        AiInternalToolException exception = assertThrows(
                AiInternalToolException.class,
                () -> service.getMyPreferenceSignals(7L, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(aiMessageMapper, never()).selectOne(any());
    }

    @Test
    void rejectsMissingSearchLimit() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");
        when(aiMessageMapper.selectOne(any()))
                .thenReturn(userMessage(7L));
        CommoditySearchToolRequest toolRequest =
                new CommoditySearchToolRequest();

        AiInternalToolException exception = assertThrows(
                AiInternalToolException.class,
                () -> service.searchCommodities(toolRequest, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                exception.getAgentErrorKey()
        );
    }

    @Test
    void rejectsSearchLimitAboveForty() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");
        when(aiMessageMapper.selectOne(any()))
                .thenReturn(userMessage(7L));
        CommoditySearchToolRequest toolRequest =
                new CommoditySearchToolRequest();
        toolRequest.setLimit(41);

        AiInternalToolException exception = assertThrows(
                AiInternalToolException.class,
                () -> service.searchCommodities(toolRequest, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(
                "AI_JAVA_TOOL_ARGUMENTS_INVALID",
                exception.getAgentErrorKey()
        );
    }

    @Test
    void validatesPostVersionsAgainstCurrentEligibleRows() {
        when(request.getHeader("X-Internal-Token"))
                .thenReturn("internal-token");
        when(request.getHeader("X-Request-Id"))
                .thenReturn("request-1");
        when(aiMessageMapper.selectOne(any()))
                .thenReturn(userMessage(7L));
        Post current = new Post();
        current.setId(11L);
        Post stale = new Post();
        stale.setId(12L);
        when(postService.listByIds(any())).thenReturn(List.of(current, stale));
        when(aiPostRagService.isEligible(current, "1000")).thenReturn(true);
        when(aiPostRagService.isEligible(stale, "2000")).thenReturn(false);
        PostVersionValidationRequest validationRequest =
                new PostVersionValidationRequest();
        validationRequest.setCandidates(List.of(
                candidate(11L, "1000"),
                candidate(12L, "2000"),
                candidate(11L, "1000")
        ));

        PostVersionValidationResponse response =
                service.validatePostVersions(validationRequest, request);

        assertEquals("request-1", response.getRequestId());
        assertEquals(1, response.getValidCandidates().size());
        assertEquals(11L, response.getValidCandidates().get(0).getPostId());
    }

    private PostVersionCandidate candidate(Long postId, String version) {
        PostVersionCandidate candidate = new PostVersionCandidate();
        candidate.setPostId(postId);
        candidate.setSourceVersion(version);
        return candidate;
    }

    private AiMessage userMessage(Long userId) {
        AiMessage message = new AiMessage();
        message.setUserId(userId);
        message.setRole(AiMessageRoleEnum.USER.getValue());
        message.setRequestId("request-1");
        return message;
    }
}
