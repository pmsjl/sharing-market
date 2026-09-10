package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.User;
import com.pmsjl.utils.PersistenceTime;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.pmsjl.manager.*;
import com.pmsjl.service.*;
import com.pmsjl.model.dto.ai.*;
import com.pmsjl.model.dto.ai.internal.*;
import com.pmsjl.model.vo.*;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiPersistenceTest {
    private SqlSession session;
    private AiMessageMapper messages;
    private AiConversationMapper conversations;
    private AiChatServiceImpl chat;
    private AiMessageServiceImpl continuation;
    private final SqlRecorder recorder = new SqlRecorder();

    @BeforeEach
    void setup() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ai_message (id BIGINT PRIMARY KEY, conversationId BIGINT, userId BIGINT, sequenceNo INT, role VARCHAR(20), content VARCHAR(4096), structuredContent VARCHAR(4096), modelName VARCHAR(128), status VARCHAR(20), requestId VARCHAR(64), inputTokens INT, outputTokens INT, latencyMs INT, agentErrorKey VARCHAR(64), retryable BOOLEAN, createTime TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP, updateTime TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP, isDelete INT)");
            statement.execute("CREATE TABLE ai_conversation (id BIGINT PRIMARY KEY, userId BIGINT, title VARCHAR(255), scene VARCHAR(32), shoppingContext VARCHAR(4096), memorySummary VARCHAR(4096), status VARCHAR(20), lastMessagePreview VARCHAR(255), lastMessageTime TIMESTAMP(0), createTime TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP, updateTime TIMESTAMP(0) DEFAULT CURRENT_TIMESTAMP, isDelete INT)");
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(false);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addInterceptor(recorder);
        config.addMapper(AiMessageMapper.class);
        config.addMapper(AiConversationMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        messages = session.getMapper(AiMessageMapper.class);
        conversations = session.getMapper(AiConversationMapper.class);
        chat = new AiChatServiceImpl();
        ReflectionTestUtils.setField(chat, "aiMessageMapper", messages);
        ReflectionTestUtils.setField(chat, "aiConversationMapper", conversations);
        ReflectionTestUtils.setField(chat, "objectMapper", new ObjectMapper());
        continuation = new AiMessageServiceImpl();
        ReflectionTestUtils.setField(continuation, "baseMapper", messages);
        var conversationService = new AiConversationServiceImpl();
        ReflectionTestUtils.setField(conversationService, "baseMapper", conversations);
        ReflectionTestUtils.setField(continuation, "aiConversationService", conversationService);
    }

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            session.getConnection().createStatement().execute("DROP ALL OBJECTS");
            session.close();
        }
    }

    @Test
    void initialRecordsReturnPersistedTimesWithoutReadback() {
        User user = new User();
        user.setId(1L);
        AiConversation conversation = ReflectionTestUtils.invokeMethod(chat, "getAiConversation",
                user, "hello", null, PersistenceTime.now());
        AiMessage userMessage = ReflectionTestUtils.invokeMethod(chat, "getUserMessage",
                conversation, user, "hello", "request-1");
        AiMessage assistant = ReflectionTestUtils.invokeMethod(chat, "getAssistantMessage",
                conversation, user, "request-1");
        assertEquals(3, recorder.sql.size());
        assertTrue(recorder.sql.stream().allMatch(sql -> sql.startsWith("INSERT")));
        assertEquals(conversation.getCreateTime(), conversations.selectById(conversation.getId()).getCreateTime());
        assertStoredTimes(userMessage);
        assertStoredTimes(assistant);
    }

    @Test
    void continuationOnlyReadsSequenceAndPreservesCreationTimes() {
        AiMessage previous = new AiMessage();
        previous.setId(10L);
        previous.setConversationId(20L);
        previous.setSequenceNo(2);
        previous.setIsDelete(0);
        messages.insert(previous);
        recorder.sql.clear();
        AiMessage user = ReflectionTestUtils.invokeMethod(continuation, "getUserMessage",
                20L, 1L, "next", null, "request-2");
        AiMessage assistant = ReflectionTestUtils.invokeMethod(continuation, "getAssistantMessage",
                20L, 1L, "next", null, "request-2", user.getSequenceNo());
        assertEquals(3, recorder.sql.size());
        assertEquals(1, recorder.sql.stream().filter(sql -> sql.startsWith("SELECT")).count());
        assertEquals(3, user.getSequenceNo());
        assertEquals(4, assistant.getSequenceNo());
        assertStoredTimes(user);
        assertStoredTimes(assistant);
    }

    @Test
    void pendingUpdateWritesTimestampAndRejectsLateReply() {
        AiConversation conversation = new AiConversation();
        conversation.setId(20L);
        User user = new User();
        user.setId(1L);
        AiMessage assistant = ReflectionTestUtils.invokeMethod(chat, "getAssistantMessage",
                conversation, user, "request-3");
        Date created = assistant.getCreateTime();
        Date updated = new Date(created.getTime() + 5000);
        assistant.setContent("answer");
        assistant.setStatus("SUCCESS");
        assistant.setUpdateTime(updated);
        recorder.sql.clear();
        assertEquals(1, messages.updatePendingAssistantMessage(assistant));
        assertEquals(1, recorder.sql.size());
        AiMessage stored = messages.selectById(assistant.getId());
        assertEquals(created, stored.getCreateTime());
        assertEquals(updated, stored.getUpdateTime());
        assistant.setContent("late reply");
        assertEquals(0, messages.updatePendingAssistantMessage(assistant));
        assertEquals("answer", messages.selectById(assistant.getId()).getContent());
    }

    @Test
    void lateInitialSuccessCannotResurrectFailedAssistantOrUpdateConversation() throws Exception {
        User user = new User();
        user.setId(1L);
        AiConversation conversation = ReflectionTestUtils.invokeMethod(chat, "getAiConversation",
                user, "hello", null, PersistenceTime.now());
        AiMessage userMessage = ReflectionTestUtils.invokeMethod(chat, "getUserMessage",
                conversation, user, "hello", "request-late");
        AiMessage assistant = ReflectionTestUtils.invokeMethod(chat, "getAssistantMessage",
                conversation, user, "request-late");
        assistant.setStatus("FAILED");
        assistant.setContent("已超时");
        assistant.setAgentErrorKey("PENDING_TIMEOUT");
        assistant.setRetryable(true);
        assistant.setUpdateTime(PersistenceTime.now());
        assertEquals(1, messages.updateById(assistant));
        AiConversation before = conversations.selectById(conversation.getId());

        var access = mock(AiAccessService.class);
        var traces = mock(AiAgentTraceService.class);
        var assembler = mock(AiStructuredContentAssembler.class);
        when(assembler.assemble(any())).thenReturn(new AiStructuredContentVO());
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        ReflectionTestUtils.setField(chat, "aiAccessService", access);
        ReflectionTestUtils.setField(chat, "aiAgentTraceService", traces);
        ReflectionTestUtils.setField(chat, "aiStructuredContentAssembler", assembler);
        ReflectionTestUtils.setField(chat, "transactionTemplate", transactions);

        AgentRunResponse response = new AgentRunResponse();
        response.setAnswer("late answer");
        response.setOutput(new AgentOutput());

        Class<?> pendingType = Class.forName(
                "com.pmsjl.service.Impl.AiChatServiceImpl$PendingChat");
        var constructor = pendingType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object pendingChat = constructor.newInstance(
                "request-late", user, null, conversation, userMessage, assistant,
                new AiUsageDate(LocalDate.now()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(chat, "persistAgentSuccess",
                        pendingChat, response));
        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(), exception.getCode());
        assertEquals("已超时", messages.selectById(assistant.getId()).getContent());
        assertEquals("FAILED", messages.selectById(assistant.getId()).getStatus());
        AiConversation after = conversations.selectById(conversation.getId());
        assertEquals(before.getLastMessagePreview(), after.getLastMessagePreview());
        assertEquals(before.getLastMessageTime(), after.getLastMessageTime());
        verifyNoInteractions(access, traces);
    }

    @Test
    void mergedConversationUpdateClearsConditionsWithOneStatement() {
        AiConversation conversation = new AiConversation();
        conversation.setId(20L);
        conversation.setShoppingContext("old conditions");
        conversation.setIsDelete(0);
        conversations.insert(conversation);
        conversation.setShoppingContext(null);
        Date now = PersistenceTime.now();
        recorder.sql.clear();
        ReflectionTestUtils.invokeMethod(continuation, "updateAiConversation", conversation, now);
        assertEquals(1, recorder.sql.size());
        assertTrue(recorder.sql.get(0).startsWith("UPDATE"));
        AiConversation stored = conversations.selectById(20L);
        assertNull(stored.getShoppingContext());
        assertEquals(conversation.getLastMessagePreview(), stored.getLastMessagePreview());
        assertEquals(now, stored.getLastMessageTime());
        assertEquals(now, stored.getUpdateTime());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void completeChatReturnsStoredFieldsWithReducedQueries(boolean existingConversation, boolean failure) {
        var userService = mock(UserService.class);
        var client = mock(AiAgentClient.class);
        var access = mock(AiAccessService.class);
        var trace = mock(AiAgentTraceService.class);
        var assembler = mock(AiStructuredContentAssembler.class);
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        User user = new User();
        user.setId(1L);
        when(userService.getLoginUser()).thenReturn(user);
        for (Object service : List.of(chat, continuation)) {
            ReflectionTestUtils.setField(service, "userService", userService);
            ReflectionTestUtils.setField(service, "aiAgentClient", client);
            ReflectionTestUtils.setField(service, "aiAccessService", access);
            ReflectionTestUtils.setField(service, "aiAgentTraceService", trace);
            ReflectionTestUtils.setField(service, "aiStructuredContentAssembler", assembler);
            ReflectionTestUtils.setField(service, "transactionTemplate", transactions);
            ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        }
        ReflectionTestUtils.setField(continuation, "aiChatService", chat);
        ReflectionTestUtils.setField(continuation, "aiConversationMapper", conversations);
        if (failure) {
            when(client.runAgent(anyString(), any())).thenThrow(
                    new AiAgentClientException("AI_MODEL_TIMEOUT", "timeout", true));
        } else {
            AgentRunResponse response = new AgentRunResponse();
            response.setAnswer(" answer ");
            response.setOutput(new AgentOutput());
            when(client.runAgent(anyString(), any())).thenReturn(response);
            when(assembler.assemble(any())).thenReturn(new AiStructuredContentVO());
        }
        AiChatMessageRequest request = new AiChatMessageRequest();
        request.setContent("hello");
        AiChatVO result;
        if (existingConversation) {
            AiConversation conversation = ReflectionTestUtils.invokeMethod(chat, "getAiConversation",
                    user, "previous", null, PersistenceTime.now());
            ReflectionTestUtils.invokeMethod(chat, "getUserMessage", conversation, user, "previous", "previous-request");
            AiMessage previous = ReflectionTestUtils.invokeMethod(chat, "getAssistantMessage",
                    conversation, user, "previous-request");
            previous.setStatus("SUCCESS");
            messages.updateById(previous);
            recorder.sql.clear();
            result = continuation.sendMessage(conversation.getId(), request, null);
        } else {
            recorder.sql.clear();
            result = chat.createConversation(request, null);
        }
        assertEquals(existingConversation ? 5 : 1,
                recorder.sql.stream().filter(sql -> sql.startsWith("SELECT")).count());
        assertEquals(existingConversation ? 3 : 2,
                recorder.sql.stream().filter(sql -> sql.startsWith("UPDATE")).count());
        AiMessage stored = messages.selectById(result.getAssistantMessage().getId());
        assertEquals(failure ? "FAILED" : "SUCCESS", stored.getStatus());
        assertEquals(stored.getContent(), result.getAssistantMessage().getContent());
        assertEquals(stored.getCreateTime(), result.getAssistantMessage().getCreateTime());
        assertEquals(stored.getAgentErrorKey(), result.getAssistantMessage().getAgentErrorKey());
        assertEquals(stored.getRetryable(), result.getAssistantMessage().getRetryable());
        assertEquals(messages.selectById(result.getUserMessage().getId()).getCreateTime(),
                result.getUserMessage().getCreateTime());
        AiConversation storedConversation = conversations.selectById(result.getConversation().getId());
        assertEquals(storedConversation.getCreateTime(), result.getConversation().getCreateTime());
        assertEquals(storedConversation.getLastMessagePreview(), result.getConversation().getLastMessagePreview());
        assertEquals(storedConversation.getLastMessageTime(), result.getConversation().getLastMessageTime());
    }

    private void assertStoredTimes(AiMessage message) {
        assertNotNull(message.getCreateTime());
        assertEquals(0, message.getCreateTime().getTime() % 1000);
        AiMessage stored = messages.selectById(message.getId());
        assertEquals(message.getCreateTime(), stored.getCreateTime());
        assertEquals(message.getUpdateTime(), stored.getUpdateTime());
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static class SqlRecorder implements Interceptor {
        final List<String> sql = new ArrayList<>();
        public Object intercept(Invocation invocation) throws Throwable {
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql().trim().toUpperCase(Locale.ROOT));
            return invocation.proceed();
        }
    }
}
