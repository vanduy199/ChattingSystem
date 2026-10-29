package com.ChattingSystem.realtime.event;

public record MemberEventData(
        Long roomId,
        Long userId,
        String fullName
) {}
