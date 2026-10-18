package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.RoomStatus;

import java.time.Instant;

public record RoomResponse(
        Long id,
        String title,
        String description,
        double lat,
        double lng,
        int radiusM,
        RoomStatus status,
        Instant createdAt,
        Instant expiresAt
) {}