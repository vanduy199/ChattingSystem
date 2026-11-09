package com.ChattingSystem.chatroom.dto.response;

import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;

import java.time.Instant;

public record RoomMemberResponse(
        Long userId,
        String fullName,
        String avatarUrl,
        MemberRole role,
        MemberStatus status,
        Instant joinedAt
) {
}
