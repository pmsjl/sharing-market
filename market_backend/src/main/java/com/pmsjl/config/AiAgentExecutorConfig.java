package com.pmsjl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** Agent 等待使用独立线程，不占用请求线程，也不排队消耗 PENDING 超时窗口。 */
@Configuration
public class AiAgentExecutorConfig {

    @Bean("aiAgentExecutor")
    public ThreadPoolTaskExecutor aiAgentExecutor(AiAgentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getMaxConcurrentRuns());
        executor.setMaxPoolSize(properties.getMaxConcurrentRuns());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ai-agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
