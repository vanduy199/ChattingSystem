package com.ChattingSystem.media.config;

import com.ChattingSystem.shared.properties.MediaGcProperties;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MediaWorkerProperties.class, MediaGcProperties.class})
public class MediaConfig {

    @Bean(name = "mediaTaskExecutor")
    public ThreadPoolTaskExecutor mediaTaskExecutor(MediaWorkerProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("media-worker-");
        // TUYỆT ĐỐI không dùng CallerRunsPolicy để tránh treo luồng scheduler
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
