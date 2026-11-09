package com.ChattingSystem.chatroom.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateRoomRequest(
        @NotNull Long id,
        @Size(max = 500) String title,
        @Size(max = 500) String description,
        @Min(5) @Max(1440) Integer ttlMinutes,
        @Pattern(regexp = "PUBLIC|PRIVATE", message = "roomType must be one of: PUBLIC, PRIVATE") String roomType) {
}
