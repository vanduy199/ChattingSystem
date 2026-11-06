package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomStatus;

import java.time.Instant;

public record MyRoomResponse(
        Long roomId,
        Long userId,
        String title,
        String description,
        Long createdBy,
        RoomStatus status,
        Instant expiresAt,
        MemberRole role,
        Instant joinedAt,
        String roomType
) {
}
