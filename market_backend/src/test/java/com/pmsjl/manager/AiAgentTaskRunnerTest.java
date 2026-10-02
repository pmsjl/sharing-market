package com.pmsjl.manager;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.internal.AgentRunRequest;
import com.pmsjl.model.dto.ai.internal.AgentRunResponse;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.enums.AiMessageStatusEnum;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.model.vo.AiMessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.pmsjl.config.AiAgentExecutorConfig;
import com.pmsjl.config.AiAgentProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAgentTaskRunnerTest {
    private final AiAgentClient client = mock(AiAgentClient.class);
    private final AiMessageMapper messages = mock(AiMessageMapper.class);
    private final List<Runnable> tasks = new ArrayList<>();
    private final AgentRunRequest request = new AgentRunRequest();
    private final AiChatVO pending = pending();
    private final List<AiAgentClientException> failures = new ArrayList<>();

    private AiAgentTaskRunner runner(Executor executor) {
        var runner = new AiAgentTaskRunner();
        ReflectionTestUtils.setField(runner, "executor", executor);
        ReflectionTestUtils.setField(runner, "aiAgentClient", client);
        ReflectionTestUtils.setField(runner, "aiMessageMapper", messages);
        return runner;
    }

    private AiChatVO failed(AiAgentClientException exception) {
        failures.add(exception);
        var result = new AiChatVO();
        var message = new AiMessageVO();
        message.setStatus(AiMessageStatusEnum.FAILED);
        result.setAssistantMessage(message);
        return result;
    }

    @Test
    void returnsBeforeCallingAgentAndCompletesThroughOriginalCallback() {
        var response = new AgentRunResponse();
        when(client.runAgent("request", request)).thenReturn(response);
        var completed = new ArrayList<AgentRunResponse>();
        assertSame(pending, runner(tasks::add).submit(pending, request, value -> {
            completed.add(value);
            return new AiChatVO();
        }, this::failed));
        verifyNoInteractions(client, messages);
        assertTrue(completed.isEmpty());
        tasks.get(0).run();
        assertEquals(List.of(response), completed);
        assertTrue(failures.isEmpty());
    }

    @Test
    void delayedAgentRunsOnWorkerAndSubmissionDoesNotWait() throws Exception {
        var properties = new AiAgentProperties();
        properties.setMaxConcurrentRuns(1);
        var executor = new AiAgentExecutorConfig().aiAgentExecutor(properties);
        executor.initialize();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var workerThread = new java.util.concurrent.atomic.AtomicReference<String>();
        when(client.runAgent("request", request)).thenAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getName());
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new AgentRunResponse();
        });
        try {
            assertSame(pending, runner(executor).submit(pending, request, response -> {
                completed.countDown();
                return new AiChatVO();
            }, this::failed));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, completed.getCount(), "提交已返回，但 Agent 尚未完成");
            assertTrue(workerThread.get().startsWith("ai-agent-"));
            release.countDown();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void rejectionReturnsFailedWithoutCallingAgent() {
        Executor full = task -> { throw new RejectedExecutionException("full"); };
        var result = runner(full).submit(pending, request, response -> fail("不能成功回写"), this::failed);
        assertEquals(AiMessageStatusEnum.FAILED, result.getAssistantMessage().getStatus());
        assertEquals(1, failures.size());
        assertEquals("AI_AGENT_BUSY", failures.get(0).getAgentErrorKey());
        assertTrue(failures.get(0).isRetryable());
        verifyNoInteractions(client, messages);
    }

    @Test
    void remoteFailurePreservesErrorAndCallsFailureCallbackOnce() {
        var error = new AiAgentClientException("AI_MODEL_TIMEOUT", "timeout", true);
        when(client.runAgent(anyString(), any())).thenThrow(error);
        when(messages.selectById(10L)).thenReturn(message("PENDING"));
        runner(tasks::add).submit(pending, request, response -> fail("不能成功回写"), this::failed);
        tasks.get(0).run();
        assertEquals(List.of(error), failures);
    }

    @Test
    void lateSuccessCannotTriggerAnotherFailureWriteBack() {
        when(client.runAgent(anyString(), any())).thenReturn(new AgentRunResponse());
        when(messages.selectById(10L)).thenReturn(message("FAILED"));
        runner(tasks::add).submit(pending, request, response -> {
            throw new BusinessException(ErrorCode.CONFLICT_ERROR, "已结束");
        }, this::failed);
        tasks.get(0).run();
        assertTrue(failures.isEmpty());
    }

    @Test
    void deletedMessageIgnoresLateRemoteFailure() {
        when(client.runAgent(anyString(), any())).thenThrow(new AiAgentClientException("TIMEOUT", "timeout", true));
        when(messages.selectById(10L)).thenReturn(null);
        runner(tasks::add).submit(pending, request, response -> fail("不能成功回写"), this::failed);
        tasks.get(0).run();
        assertTrue(failures.isEmpty());
    }

    @Test
    void unexpectedAssemblyFailureConvergesPendingToFailure() {
        when(client.runAgent(anyString(), any())).thenReturn(new AgentRunResponse());
        when(messages.selectById(10L)).thenReturn(message("PENDING"));
        runner(tasks::add).submit(pending, request, response -> {
            throw new IllegalStateException("invalid structured output");
        }, this::failed);
        tasks.get(0).run();
        assertEquals(1, failures.size());
        assertEquals("AI_AGENT_EXECUTION_FAILED", failures.get(0).getAgentErrorKey());
    }

    @Test
    void writeBackFailureDoesNotRetryForever() {
        when(client.runAgent(anyString(), any())).thenThrow(new AiAgentClientException("TIMEOUT", "timeout", true));
        when(messages.selectById(10L)).thenReturn(message("PENDING"));
        runner(tasks::add).submit(pending, request, response -> fail("不能成功回写"), error -> {
            failures.add(error);
            throw new IllegalStateException("database unavailable");
        });
        assertDoesNotThrow(() -> tasks.get(0).run());
        assertEquals(1, failures.size());
    }

    private static AiChatVO pending() {
        var response = new AiChatVO();
        response.setRequestId("request");
        var message = new AiMessageVO();
        message.setId(10L);
        message.setStatus(AiMessageStatusEnum.PENDING);
        response.setAssistantMessage(message);
        return response;
    }

    private static AiMessage message(String status) {
        var message = new AiMessage();
        message.setStatus(status);
        message.setIsDelete(0);
        return message;
    }
}
