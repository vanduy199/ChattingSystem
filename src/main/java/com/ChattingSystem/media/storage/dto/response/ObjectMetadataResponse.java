package com.ChattingSystem.media.storage.dto.response;

public record ObjectMetadataResponse(
        long contentLength,
        String contentType,
        String etag
) {}
