package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.AiConversationQueryRequest;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.AiConversationSceneEnum;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import com.pmsjl.model.vo.AiConversationVO;
import com.pmsjl.model.vo.AiPageVO;
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

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceImplTest {

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private UserService userService;

    @Mock
    private AiMessageMapper messageMapper;

    @Mock
    private HttpServletRequest request;

    private AiConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        mybatisConfiguration.setMapUnderscoreToCamelCase(false);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(mybatisConfiguration, "AiConversationServiceImplTest"),
                AiConversation.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(mybatisConfiguration, "AiConversationServiceImplTest-AiMessage"),
                AiMessage.class);
        conversationService = new AiConversationServiceImpl();
        ReflectionTestUtils.setField(conversationService, "baseMapper", conversationMapper);
        ReflectionTestUtils.setField(conversationService, "userService", userService);
        ReflectionTestUtils.setField(conversationService, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(conversationService, "aiMessageMapper", messageMapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listMyConversationsUsesLoginUserAndBuildsContractPage() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        Date now = new Date();
        AiConversation conversation = new AiConversation();
        conversation.setId(1002L);
        conversation.setUserId(101L);
        conversation.setTitle("二手教材值不值得买");
        conversation.setScene("SHOPPING_GUIDE");
        conversation.setShoppingContext("{\"budgetMax\":80,\"preferenceTags\":[\"有笔记\"],\"avoidances\":[]}");
        conversation.setStatus("ACTIVE");
        conversation.setLastMessagePreview("建议先核对版次");
        conversation.setLastMessageTime(now);
        conversation.setCreateTime(now);

        when(conversationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AiConversation> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(conversation));
            return page;
        });

        AiConversationQueryRequest queryRequest = new AiConversationQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        queryRequest.setSortField("createTime");
        queryRequest.setSortOrder("asc");
        AiPageVO<AiConversationVO> result = conversationService.listMyConversations(queryRequest, request);

        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getPageSize());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        AiConversationVO record = result.getRecords().get(0);
        assertEquals(1002L, record.getId());
        assertEquals(AiConversationSceneEnum.SHOPPING_GUIDE, record.getScene());
        assertEquals(AiConversationStatusEnum.ACTIVE, record.getStatus());
        assertEquals(80, record.getShoppingContext().getBudgetMax().intValue());
        assertEquals(List.of("有笔记"), record.getShoppingContext().getPreferenceTags());

        ArgumentCaptor<Page<AiConversation>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<AiConversation>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(conversationMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertEquals("createTime", pageCaptor.getValue().orders().get(0).getColumn());
        assertTrue(pageCaptor.getValue().orders().get(0).isAsc());
        assertEquals("id", pageCaptor.getValue().orders().get(1).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(1).isAsc());
        Wrapper<AiConversation> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(101L));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue("ACTIVE"));
        assertTrue(sqlSegment.contains("userId"));
        assertTrue(sqlSegment.contains("status"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listMyConversationsNormalizesInvalidPaginationAndUsesDefaultSort() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);
        when(conversationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        AiConversationQueryRequest queryRequest = new AiConversationQueryRequest();
        queryRequest.setCurrent(0);
        queryRequest.setPageSize(21);
        queryRequest.setSortField("memorySummary");
        queryRequest.setSortOrder("asc");

        AiPageVO<AiConversationVO> result = conversationService.listMyConversations(queryRequest, request);

        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getPageSize());
        ArgumentCaptor<Page<AiConversation>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(conversationMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertEquals("lastMessageTime", pageCaptor.getValue().orders().get(0).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(0).isAsc());
        assertEquals("id", pageCaptor.getValue().orders().get(1).getColumn());
        assertFalse(pageCaptor.getValue().orders().get(1).isAsc());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listMyConversationsSupportsArchivedStatus() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);
        when(conversationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        AiConversationQueryRequest queryRequest = new AiConversationQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        queryRequest.setStatus(AiConversationStatusEnum.ARCHIVED);

        conversationService.listMyConversations(queryRequest, request);

        ArgumentCaptor<Wrapper<AiConversation>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(conversationMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("status"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("ARCHIVED"));
        assertFalse(wrapper.getParamNameValuePairs().containsValue("ACTIVE"));
    }

    @Test
    void archiveConversationChangesOwnedActiveConversationOnly() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setUserId(101L);
        conversation.setStatus(AiConversationStatusEnum.ACTIVE.getValue());
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(conversation);
        when(messageMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(conversationMapper.updateById(conversation)).thenReturn(1);

        assertTrue(conversationService.archiveConversation(500L, request));
        assertEquals(AiConversationStatusEnum.ARCHIVED.getValue(), conversation.getStatus());
        verify(conversationMapper).updateById(conversation);
    }

    @Test
    void archiveConversationIsIdempotent() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setStatus(AiConversationStatusEnum.ARCHIVED.getValue());
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(conversation);

        assertTrue(conversationService.archiveConversation(500L, request));
        verify(messageMapper, never()).selectCount(any());
    }

    @Test
    void archiveConversationRejectsPendingAssistantMessage() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setStatus(AiConversationStatusEnum.ACTIVE.getValue());
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(conversation);
        when(messageMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.archiveConversation(500L, request));

        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(), exception.getCode());
        assertEquals(AiConversationStatusEnum.ACTIVE.getValue(), conversation.getStatus());
    }

    @Test
    void restoreConversationChangesArchivedConversationAndIsIdempotentAfterward() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setStatus(AiConversationStatusEnum.ARCHIVED.getValue());
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(conversation);
        when(conversationMapper.updateById(conversation)).thenReturn(1);

        assertTrue(conversationService.restoreConversation(500L, request));
        assertEquals(AiConversationStatusEnum.ACTIVE.getValue(), conversation.getStatus());
        assertTrue(conversationService.restoreConversation(500L, request));
        verify(conversationMapper, times(2)).selectOwnedByIdForUpdate(500L, 101L);
        verify(conversationMapper, times(1)).updateById(conversation);
        verifyNoInteractions(messageMapper);
    }

    @Test
    void archiveAndRestoreHideMissingDeletedAndOtherUsersConversations() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(null);

        BusinessException archiveException = assertThrows(BusinessException.class,
                () -> conversationService.archiveConversation(500L, request));
        BusinessException restoreException = assertThrows(BusinessException.class,
                () -> conversationService.restoreConversation(500L, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), archiveException.getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), restoreException.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deleteConversationLogicallyDeletesOwnedMessagesAndConversation() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);

        AiConversation conversation = new AiConversation();
        conversation.setId(500L);
        conversation.setUserId(101L);
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(conversation);
        when(messageMapper.delete(any(Wrapper.class))).thenReturn(4);
        when(conversationMapper.deleteById(500L)).thenReturn(1);

        assertTrue(conversationService.deleteConversation(500L, request));

        ArgumentCaptor<Wrapper<AiMessage>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper).delete(wrapperCaptor.capture());
        Wrapper<AiMessage> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("conversationId"));
        assertTrue(sqlSegment.contains("userId"));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(500L));
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().containsValue(101L));
        verify(conversationMapper).deleteById(500L);
    }

    @Test
    void deleteConversationHidesMissingDeletedAndOtherUsersConversations() {
        User loginUser = new User();
        loginUser.setId(101L);
        when(userService.getLoginUser()).thenReturn(loginUser);
        when(conversationMapper.selectOwnedByIdForUpdate(500L, 101L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> conversationService.deleteConversation(500L, request));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(messageMapper);
        verify(conversationMapper, never()).deleteById(500L);
    }
}
