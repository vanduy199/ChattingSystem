package com.ChattingSystem.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible object storage (Cloudflare R2). Uploads are disabled while {@code endpoint} is blank.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        String publicBaseUrl
) {

    public StorageProperties {
        region = (region == null || region.isBlank()) ? "auto" : region.trim();
        if (publicBaseUrl != null) {
            publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        }
    }

    public boolean isConfigured() {
        return notBlank(endpoint) && notBlank(bucket) && notBlank(accessKey)
                && notBlank(secretKey) && notBlank(publicBaseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
