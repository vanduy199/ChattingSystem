package com.ChattingSystem.media.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BatchAccessUrlRequest(
        @NotNull(message = "roomId không được để trống")
        Long roomId,

        @NotEmpty(message = "Danh sách mediaAssetIds không được để trống")
        @Size(max = 50, message = "Tối đa 50 tệp mỗi lần yêu cầu")
        List<@NotNull(message = "ID tệp không được để trống") Long> mediaAssetIds
) {
    public BatchAccessUrlRequest {
        mediaAssetIds = (mediaAssetIds != null)
                ? Collections.unmodifiableList(new ArrayList<>(mediaAssetIds))
                : List.of();
    }
}
