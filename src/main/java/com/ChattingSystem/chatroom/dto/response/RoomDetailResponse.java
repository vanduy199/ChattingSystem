package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomStatus;

import java.time.Instant;

public record RoomDetailResponse(
        Long id,
        String title,
        String description,
        int radiusM,
        RoomStatus status,
        Long createdBy,
        long memberCount,
        MemberRole myRole,
        Instant createdAt,
        Instant expiresAt
) {}
