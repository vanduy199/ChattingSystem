package com.ChattingSystem.media.repository.dto;

/**
 * DTO đại diện cho bản ghi DELETED quá hạn cần xóa sạch các object (raw, display, thumb) trên Cloud Storage.
 */
public record DeletedPurgeTarget(
        Long id,
        String rawKey,
        String displayKey,
        String thumbKey,
        String uploadId
) {}
