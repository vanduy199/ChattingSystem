package com.ChattingSystem.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "media.gc")
public record MediaGcProperties(
        boolean enabled,
        String cron,
        int batchSize,
        int maxBatchesPerRun,
        Duration orphanTtl,
        Duration purgeGrace,
        Duration creatingTimeout,
        Duration completingTimeout,
        Duration failedTtl,
        Duration uploadingTtl,
        Duration storageClaimLease
) {
    public MediaGcProperties {
        cron = (cron != null && !cron.isBlank()) ? cron : "0 0 * * * *";
        batchSize = batchSize > 0 ? batchSize : 200;
        maxBatchesPerRun = maxBatchesPerRun > 0 ? maxBatchesPerRun : 20;
        orphanTtl = orphanTtl != null ? orphanTtl : Duration.ofDays(7);
        purgeGrace = purgeGrace != null ? purgeGrace : Duration.ofHours(24);
        creatingTimeout = creatingTimeout != null ? creatingTimeout : Duration.ofMinutes(10);
        completingTimeout = completingTimeout != null ? completingTimeout : Duration.ofMinutes(15);
        failedTtl = failedTtl != null ? failedTtl : Duration.ofDays(1);
        uploadingTtl = uploadingTtl != null ? uploadingTtl : Duration.ofHours(24);
        storageClaimLease = storageClaimLease != null ? storageClaimLease : Duration.ofMinutes(10);
    }
}
