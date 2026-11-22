package com.ChattingSystem.media.dto.response;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;

import java.time.Instant;
import java.util.List;

public record InitUploadResponse(
        Long mediaAssetId,
        MediaStatus status,
        MediaKind kind,
        String uploadMode,
        long chunkSizeBytes,
        int totalParts,
        Instant expiresAt,
        long urlExpiresInSeconds,
        List<UploadTargetDto> uploadTargets
) {
    public InitUploadResponse {
        uploadTargets = (uploadTargets != null) ? List.copyOf(uploadTargets) : List.of();
    }
}
