package com.ChattingSystem.media.dto.response;

import com.ChattingSystem.media.entity.MediaStatus;

import java.time.Instant;
import java.util.List;

public record UploadStatusResponse(
        Long mediaAssetId,
        MediaStatus status,
        String uploadMode,
        int totalParts,
        Instant expiresAt,
        long urlExpiresInSeconds,
        List<CompletedPartSummaryDto> completedParts,
        List<UploadTargetDto> remainingUploadTargets
) {
    public UploadStatusResponse {
        completedParts = (completedParts != null) ? List.copyOf(completedParts) : List.of();
        remainingUploadTargets = (remainingUploadTargets != null) ? List.copyOf(remainingUploadTargets) : List.of();
    }
}
