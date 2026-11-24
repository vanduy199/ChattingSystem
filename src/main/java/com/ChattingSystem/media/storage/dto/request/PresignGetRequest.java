package com.ChattingSystem.media.storage.dto.request;

import java.time.Duration;

public record PresignGetRequest(
        Duration ttl,
        String responseContentType,
        String contentDisposition,
        String responseCacheControl
) {
    public PresignGetRequest(Duration ttl, String responseContentType, String contentDisposition) {
        this(ttl, responseContentType, contentDisposition, null);
    }
}
