package com.ChattingSystem.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record InitUploadRequest(
        @NotNull(message = "clientUploadId không được để trống")
        UUID clientUploadId,

        @NotNull(message = "roomId không được để trống")
        Long roomId,

        @NotBlank(message = "contentType không được để trống")
        String contentType,

        @Positive(message = "sizeBytes phải lớn hơn 0")
        long sizeBytes,

        boolean hd
) {
}
