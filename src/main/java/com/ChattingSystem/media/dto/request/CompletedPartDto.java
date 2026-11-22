package com.ChattingSystem.media.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CompletedPartDto(
        @Positive(message = "partNumber phải lớn hơn hoặc bằng 1")
        int partNumber,

        @NotBlank(message = "etag không được để trống")
        String etag
) {
}
