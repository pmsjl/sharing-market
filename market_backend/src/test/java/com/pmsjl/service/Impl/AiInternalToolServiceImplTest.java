package com.pmsjl.service.Impl;

import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.AiInternalToolException;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.internal.CommoditySearchToolRequest;
import com.pmsjl.model.dto.ai.internal.UserPreferenceToolResponse;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.service.AiUserPreferenceService;
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

@ExtendWith(MockitoExtension.class)
class AiInternalToolServiceImplTest {

    @Mock
    private AiMessageMapper aiMessageMapper;
    @Mock
    private AiUserPreferenceService preferenceService;
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

    private AiMessage userMessage(Long userId) {
        AiMessage message = new AiMessage();
        message.setUserId(userId);
        message.setRole(AiMessageRoleEnum.USER.getValue());
        message.setRequestId("request-1");
        return message;
    }
}
