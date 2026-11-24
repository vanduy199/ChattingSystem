package com.ChattingSystem.media.dto.response;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaAccessItemResponse(
        Long mediaAssetId,
        MediaStatus status,
        MediaKind kind,
        String contentType,
        Integer width,
        Integer height,
        Integer durationMs,
        Long urlExpiresInSeconds,
        MediaUrlsDto urls,
        String error
) {

    public static MediaAccessItemResponse available(
            Long mediaAssetId,
            MediaKind kind,
            String contentType,
            Integer width,
            Integer height,
            Integer durationMs,
            Long urlExpiresInSeconds,
            MediaUrlsDto urls
    ) {
        return new MediaAccessItemResponse(
                mediaAssetId,
                MediaStatus.AVAILABLE,
                kind,
                contentType,
                width,
                height,
                durationMs,
                urlExpiresInSeconds,
                urls,
                null
        );
    }

    public static MediaAccessItemResponse inProgress(
            Long mediaAssetId,
            MediaStatus status,
            MediaKind kind,
            String contentType,
            Integer width,
            Integer height,
            Integer durationMs
    ) {
        return new MediaAccessItemResponse(
                mediaAssetId,
                status,
                kind,
                contentType,
                width,
                height,
                durationMs,
                null,
                null,
                null
        );
    }

    public static MediaAccessItemResponse processing(
            Long mediaAssetId,
            MediaKind kind,
            String contentType,
            Integer width,
            Integer height,
            Integer durationMs
    ) {
        return inProgress(
                mediaAssetId,
                MediaStatus.PROCESSING,
                kind,
                contentType,
                width,
                height,
                durationMs
        );
    }

    public static MediaAccessItemResponse errorOnly(
            Long mediaAssetId,
            MediaStatus status,
            String error
    ) {
        boolean isNotFound = (status == null || "MEDIA_NOT_FOUND".equalsIgnoreCase(error));
        return new MediaAccessItemResponse(
                mediaAssetId,
                isNotFound ? null : status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                error
        );
    }
}
