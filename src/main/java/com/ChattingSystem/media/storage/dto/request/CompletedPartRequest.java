package com.ChattingSystem.media.storage.dto.request;

public record CompletedPartRequest(
        int partNumber,
        String etag
) {}
