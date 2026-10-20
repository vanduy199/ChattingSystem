package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.MemberRole;

import java.time.Instant;

public record JoinRoomResponse(
        Long roomId,
        Long userId,
        MemberRole role,
        Instant joinedAt
) {
}
