package com.ChattingSystem.message.dto.response;

import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;

public record AttachmentResponse(
        Long mediaAssetId,
        Integer sortOrder,
        MediaKind kind,
        MediaStatus status,
        Integer width,
        Integer height,
        Integer durationMs
) {
    public static AttachmentResponse from(MediaAsset asset) {
        if (asset == null) {
            return null;
        }
        return new AttachmentResponse(
                asset.getId(),
                asset.getSortOrder(),
                asset.getKind(),
                asset.getStatus(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getDurationMs()
        );
    }
}
