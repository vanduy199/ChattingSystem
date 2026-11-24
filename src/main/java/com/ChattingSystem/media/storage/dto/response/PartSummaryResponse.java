package com.ChattingSystem.media.storage.dto.response;

import java.time.Instant;

public record PartSummaryResponse(
        int partNumber,
        String etag,
        long sizeBytes,
        Instant lastModified
) {}
