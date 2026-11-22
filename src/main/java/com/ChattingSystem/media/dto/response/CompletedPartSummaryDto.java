package com.ChattingSystem.media.dto.response;

public record CompletedPartSummaryDto(
        int partNumber,
        String etag,
        long sizeBytes
) {
}
