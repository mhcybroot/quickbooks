package com.example.quickbooksimporter.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ImportAsyncConfig {

    @Bean(name = "importTaskExecutor")
    public Executor importTaskExecutor(
            @Value("${app.import.background.pool-size:4}") int poolSize,
            @Value("${app.import.background.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, poolSize));
        executor.setMaxPoolSize(Math.max(1, poolSize));
        executor.setQueueCapacity(Math.max(10, queueCapacity));
        executor.setThreadNamePrefix("import-bg-");
        executor.initialize();
        return executor;
    }
}
