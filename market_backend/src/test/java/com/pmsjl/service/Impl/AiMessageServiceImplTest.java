package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.AiMessageQueryRequest;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.model.enums.AiMessageStatusEnum;
import com.pmsjl.model.vo.AiMessageVO;
import com.pmsjl.model.vo.AiPageVO;
import com.pmsjl.service.AiConversationService;
import com.pmsjl.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

import static com.pmsjl.constant.AiChatConstant.PENDING_TIMEOUT_ERROR_KEY;
import static com.pmsjl.constant.AiChatConstant.PENDING_TIMEOUT_MESSAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMessageServiceImplTest {

    @Mock
    private AiMessageMapper messageMapper;

    @Mock
    private AiConversationService conversationService;

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    private AiMessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        mybatisConfiguration.setMapUnderscoreToCamelCase(false);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(mybatisConfiguration, "AiMessageServiceImplTest"),
                AiMessage.class);
        messageService = new AiMessageServiceImpl();
        ReflectionTestUtils.setField(messageService, "baseMapper", messageMapper);
        ReflectionTestUtils.setField(messageService, "aiConversationService", conversationService);
        ReflectionTestUtils.setField(messageService, "aiConversationMapper", conversationMapper);
        ReflectionTestUtils.setField(messageService, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(messageService, "userService", userService);
        ReflectionTestUtils.setField(messageService, "objectMapper", new ObjectMapper());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listConversationMessagesChecksOwnerAndReturnsPageInAscendingSequence() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setUserId(101L);
        when(conversationService.getById(500L)).thenReturn(conversation);

        Date now = new Date();
        AiMessage assistantMessage = message(2004L, 4, "ASSISTANT", "可以继续验机", now);
        assistantMessage.setStructuredContent("{\"summary\":\"注意电池状态\"}");
        AiMessage userMessage = message(2003L, 3, "USER", "电池还能买吗？", now);

        when(messageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AiMessage> page = invocation.getArgument(0);
            page.setTotal(4);
            page.setRecords(List.of(userMessage, assistantMessage));
            return page;
        });

        AiMessageQueryRequest queryRequest = new AiMessageQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(20);
        queryRequest.setSortField("sequenceNo");
        queryRequest.setSortOrder("asc");
        AiPageVO<AiMessageVO> result = messageService.listConversationMessages(500L, queryRequest, request);

        assertEquals(1, result.getCurrent());
        assertEquals(20, result.getPageSize());
        assertEquals(4, result.getTotal());
        assertEquals(List.of(3, 4), result.getRecords().stream().map(AiMessageVO::getSequenceNo).toList());
        assertEquals(AiMessageRoleEnum.USER, result.getRecords().get(0).getRole());
        assertEquals(AiMessageRoleEnum.ASSISTANT, result.getRecords().get(1).getRole());
        assertEquals(AiMessageStatusEnum.SUCCESS, result.getRecords().get(1).getStatus());
        assertEquals("注意电池状态", result.getRecords().get(1).getStructuredContent().getSummary());

        ArgumentCaptor<Page<AiMessage>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<AiMessage>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals("sequenceNo", pageCaptor.getValue().orders().get(0).getColumn());
        assertTrue(pageCaptor.getValue().orders().get(0).isAsc());
        assertEquals("id", pageCaptor.getValue().orders().get(1).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(1).isAsc());
        Wrapper<AiMessage> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(500L));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(101L));
        assertTrue(sqlSegment.contains("conversationId"));
        assertTrue(sqlSegment.contains("userId"));
    }

    @Test
    void listConversationMessagesRejectsOtherUsersConversationBeforeReadingMessages() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setUserId(202L);
        when(conversationService.getById(500L)).thenReturn(conversation);

        AiMessageQueryRequest queryRequest = new AiMessageQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(20);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageService.listConversationMessages(500L, queryRequest, request));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listConversationMessagesNormalizesPaginationAndRejectsUnsafeSortField() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);
        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setUserId(101L);
        when(conversationService.getById(500L)).thenReturn(conversation);
        when(messageMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        AiMessageQueryRequest queryRequest = new AiMessageQueryRequest();
        queryRequest.setCurrent(0);
        queryRequest.setPageSize(51);
        queryRequest.setSortField("modelName");
        queryRequest.setSortOrder("asc");

        AiPageVO<AiMessageVO> result = messageService.listConversationMessages(500L, queryRequest, request);

        assertEquals(1, result.getCurrent());
        assertEquals(20, result.getPageSize());
        ArgumentCaptor<Page<AiMessage>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(messageMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertEquals("sequenceNo", pageCaptor.getValue().orders().get(0).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(0).isAsc());
        assertEquals("id", pageCaptor.getValue().orders().get(1).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(1).isAsc());
    }

    @Test
    @SuppressWarnings("unchecked")
    void expireStalePendingMessagesUsesConversationLockAndPendingCas() {
        Date expireBefore = new Date(System.currentTimeMillis() - 60_000L);
        AiMessage candidate = new AiMessage();
        candidate.setId(700L);
        candidate.setConversationId(500L);
        candidate.setCreateTime(new Date(expireBefore.getTime() - 1_000L));
        when(messageMapper.selectStalePendingMessages(expireBefore, 100))
                .thenReturn(List.of(candidate));

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        when(conversationMapper.selectByIdForUpdate(500L)).thenReturn(conversation);
        when(messageMapper.markPendingMessageTimedOut(
                eq(700L), eq(expireBefore), eq(PENDING_TIMEOUT_MESSAGE),
                eq(PENDING_TIMEOUT_ERROR_KEY), any(Date.class))).thenReturn(1);
        when(conversationMapper.updateById(conversation)).thenReturn(1);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        int expiredCount = messageService.expireStalePendingMessages(expireBefore, 100);

        assertEquals(1, expiredCount);
        assertEquals(PENDING_TIMEOUT_MESSAGE, conversation.getLastMessagePreview());
        assertNotNull(conversation.getLastMessageTime());
        verify(conversationMapper).selectByIdForUpdate(500L);
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    @SuppressWarnings("unchecked")
    void expireStalePendingMessagesIgnoresCandidateAlreadyCompletedByAgent() {
        Date expireBefore = new Date(System.currentTimeMillis() - 60_000L);
        AiMessage candidate = new AiMessage();
        candidate.setId(700L);
        candidate.setConversationId(500L);
        when(messageMapper.selectStalePendingMessages(expireBefore, 100))
                .thenReturn(List.of(candidate));

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        when(conversationMapper.selectByIdForUpdate(500L)).thenReturn(conversation);
        when(messageMapper.markPendingMessageTimedOut(
                eq(700L), eq(expireBefore), eq(PENDING_TIMEOUT_MESSAGE),
                eq(PENDING_TIMEOUT_ERROR_KEY), any(Date.class))).thenReturn(0);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        int expiredCount = messageService.expireStalePendingMessages(expireBefore, 100);

        assertEquals(0, expiredCount);
        verify(conversationMapper, never()).updateById(any(AiConversation.class));
    }

    private static AiMessage message(Long id, int sequenceNo, String role, String content, Date createTime) {
        AiMessage message = new AiMessage();
        message.setId(id);
        message.setSequenceNo(sequenceNo);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("SUCCESS");
        message.setRetryable(false);
        message.setCreateTime(createTime);
        return message;
    }
}
