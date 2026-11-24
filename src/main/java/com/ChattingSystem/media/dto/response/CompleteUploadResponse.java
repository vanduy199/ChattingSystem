package com.ChattingSystem.media.dto.response;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;

public record CompleteUploadResponse(
        Long mediaAssetId,
        MediaStatus status,
        MediaKind kind
) {
}
