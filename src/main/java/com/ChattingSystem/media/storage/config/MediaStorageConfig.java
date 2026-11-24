package com.ChattingSystem.media.storage.config;

import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.storage.service.impl.S3ObjectStorage;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageConfig {

    @Bean
    public ObjectStorage objectStorage(MediaStorageProperties properties) {
        return new S3ObjectStorage(properties);
    }
}
