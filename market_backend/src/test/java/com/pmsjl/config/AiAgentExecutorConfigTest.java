package com.pmsjl.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AiAgentExecutorConfigTest {
    @Test
    void fixedConcurrencyRejectsWithoutQueueingOrUsingCallerThread() throws Exception {
        var properties = new AiAgentProperties();
        properties.setMaxConcurrentRuns(2);
        var executor = new AiAgentExecutorConfig().aiAgentExecutor(properties);
        executor.initialize();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            Runnable delayedAgent = () -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };
            executor.execute(delayedAgent);
            executor.execute(delayedAgent);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(0, executor.getThreadPoolExecutor().getQueue().size());
            assertThrows(RejectedExecutionException.class,
                    () -> executor.execute(() -> fail("拒绝任务不得在请求线程运行")));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
