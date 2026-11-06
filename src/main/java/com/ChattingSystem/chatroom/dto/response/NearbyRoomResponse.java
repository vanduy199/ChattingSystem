package com.ChattingSystem.chatroom.dto.response;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NearbyRoomResponse(
        Long id,
        String title,
        String description,
        int radiusM,
        long memberCount,
        String distanceLabel,
        boolean joined,
        Instant expiresAt,
        // Test JsonpProperty with jackson
        @JsonProperty("roomType") String roomType
) {}
