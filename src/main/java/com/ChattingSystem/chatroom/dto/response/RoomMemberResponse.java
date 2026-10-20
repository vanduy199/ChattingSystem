package com.ChattingSystem.chatroom.dto.response;

import java.time.Instant;

public record RoomMemberResponse(
        Long userId,
        String fullName,
        String avatarUrl,
        Instant joinedAt
) {
}

