package com.ChattingSystem.media.dto.response;

import java.util.List;

public record BatchAccessUrlResponse(
        List<MediaAccessItemResponse> items
) {
    public BatchAccessUrlResponse {
        items = (items != null) ? List.copyOf(items) : List.of();
    }
}
