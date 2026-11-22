package com.ChattingSystem.media.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaUrlsDto(
        String thumbnail,
        String display
) {
}
