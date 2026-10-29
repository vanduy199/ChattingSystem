package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.RoomStatus;

import java.time.Instant;

// Không có tọa độ: tâm phòng là vị trí thật của người tạo
public record RoomResponse(
        Long id,
        String title,
        String description,
        int radiusM,
        RoomStatus status,
        Instant createdAt,
        Instant expiresAt
) {}
