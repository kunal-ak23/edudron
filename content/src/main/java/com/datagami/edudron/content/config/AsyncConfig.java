package com.datagami.edudron.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-logging-");
        executor.initialize();
        return executor;
    }

    /**
     * Bounded thread pool for AI generation jobs (course, lecture, image generation).
     * Limits concurrent AI API calls to prevent resource exhaustion.
     */
    @Bean(name = "aiJobTaskExecutor")
    public Executor aiJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);     // Max 5 concurrent AI jobs
        executor.setQueueCapacity(20);  // Buffer up to 20 waiting jobs
        executor.setThreadNamePrefix("ai-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
