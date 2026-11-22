package com.ChattingSystem.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.time.Duration;

@ConfigurationProperties(prefix = "media.worker")
public record MediaWorkerProperties(
        boolean enabled,
        int concurrency,
        Duration pollInterval,
        Duration jobTimeout,
        int maxAttempts,
        String tempDir,
        long maxDownloadBytes,
        long maxPixels,
        int maxVideoDurationSeconds
) {
    public MediaWorkerProperties {
        concurrency = concurrency > 0 ? concurrency : 1;
        pollInterval = pollInterval != null ? pollInterval : Duration.ofSeconds(2);
        jobTimeout = jobTimeout != null ? jobTimeout : Duration.ofMinutes(10);
        maxAttempts = maxAttempts > 0 ? maxAttempts : 4;
        tempDir = (tempDir != null && !tempDir.isBlank())
                ? tempDir
                : System.getProperty("java.io.tmpdir") + File.separator + "media-work";
        maxDownloadBytes = maxDownloadBytes > 0 ? maxDownloadBytes : 268435456L; // 256 MB
        maxPixels = maxPixels > 0 ? maxPixels : 50_000_000L;
        maxVideoDurationSeconds = maxVideoDurationSeconds > 0 ? maxVideoDurationSeconds : 300; // 5 phút
    }
}
