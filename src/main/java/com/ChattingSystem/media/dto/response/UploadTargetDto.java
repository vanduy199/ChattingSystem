package com.ChattingSystem.media.dto.response;

import java.util.Map;

public record UploadTargetDto(
        int partNumber,
        String method,
        String url,
        Map<String, String> headers
) {
    public UploadTargetDto {
        headers = (headers != null) ? Map.copyOf(headers) : Map.of();
    }
}
