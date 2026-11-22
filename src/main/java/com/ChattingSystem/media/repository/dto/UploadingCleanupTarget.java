package com.ChattingSystem.media.repository.dto;

/**
 * DTO đại diện cho bản ghi UPLOADING quá hạn cần dọn dẹp trên Cloud Storage.
 */
public record UploadingCleanupTarget(
        Long id,
        String rawKey,
        String uploadId
) {}
