package com.ChattingSystem.media.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteUploadRequest(
        @Valid
        @Size(max = 1000, message = "Số lượng mảnh không được vượt quá 1000")
        List<@Valid CompletedPartDto> parts
) {
    public CompleteUploadRequest {
        parts = (parts != null) ? List.copyOf(parts) : List.of();
    }
}
