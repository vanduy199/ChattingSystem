package com.ChattingSystem.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "media.storage")
public record MediaStorageProperties(
        String bucket,
        String region,
        String endpoint,
        String publicEndpoint,
        boolean pathStyle,
        String accessKey,
        String secretKey,
        int chunkSizeBytes,
        Duration putUrlTtl,
        Duration getUrlTtl
) {
    public MediaStorageProperties {
        region = (region == null || region.isBlank()) ? "us-east-1" : region.trim();
        chunkSizeBytes = (chunkSizeBytes <= 0) ? 5242880 : chunkSizeBytes;
        putUrlTtl = (putUrlTtl == null) ? Duration.ofMinutes(15) : putUrlTtl;
        getUrlTtl = (getUrlTtl == null) ? Duration.ofMinutes(30) : getUrlTtl;
    }

    public boolean isConfigured() {
        return notBlank(bucket) && notBlank(accessKey) && notBlank(secretKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
