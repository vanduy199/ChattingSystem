package com.ChattingSystem.chatroom.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomRequest(
        @NotNull Long id,
        @Size(max = 500) String title,
        @Size(max = 500) String description,
        @Min(5) @Max(1440) Integer ttlMinutes
    ) {
}
