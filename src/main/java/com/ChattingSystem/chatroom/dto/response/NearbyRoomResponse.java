package com.ChattingSystem.chatroom.dto.response;

import java.time.Instant;

public record NearbyRoomResponse(
        Long id,
        String title,
        String description,
        int radiusM,
        long memberCount,
        String distanceLabel,
        boolean joined,
        Instant expiresAt
) {}
