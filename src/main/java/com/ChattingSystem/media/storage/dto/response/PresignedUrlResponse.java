package com.ChattingSystem.media.storage.dto.response;

import java.time.Instant;
import java.util.Map;

public record PresignedUrlResponse(
        String url,
        String httpMethod,
        Map<String, String> signedHeaders,
        Instant expiresAt
) {}
