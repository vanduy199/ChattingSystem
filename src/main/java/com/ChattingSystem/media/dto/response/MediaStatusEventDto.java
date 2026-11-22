package com.ChattingSystem.media.dto.response;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;

public record MediaStatusEventDto(
        Long mediaAssetId,
        Long roomId,
        Long messageId,
        MediaStatus status,
        MediaKind kind,
        Integer width,
        Integer height,
        Integer durationMs,
        String failureReason
) {
    public static MediaStatusEventDto available(Long mediaAssetId, Long roomId, Long messageId,
                                                MediaKind kind, Integer width, Integer height, Integer durationMs) {
        return new MediaStatusEventDto(mediaAssetId, roomId, messageId, MediaStatus.AVAILABLE,
                kind, width, height, durationMs, null);
    }

    public static MediaStatusEventDto blocked(Long mediaAssetId, Long roomId, Long messageId,
                                              MediaKind kind, String failureReason) {
        return new MediaStatusEventDto(mediaAssetId, roomId, messageId, MediaStatus.BLOCKED,
                kind, null, null, null, failureReason);
    }

    public static MediaStatusEventDto failed(Long mediaAssetId, Long roomId, Long messageId,
                                             MediaKind kind, String failureReason) {
        return new MediaStatusEventDto(mediaAssetId, roomId, messageId, MediaStatus.FAILED,
                kind, null, null, null, failureReason);
    }

    public static MediaStatusEventDto deleted(Long mediaAssetId, Long roomId, Long messageId,
                                              MediaKind kind) {
        return new MediaStatusEventDto(mediaAssetId, roomId, messageId, MediaStatus.DELETED,
                kind, null, null, null, null);
    }
}
