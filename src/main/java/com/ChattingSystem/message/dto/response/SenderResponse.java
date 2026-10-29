package com.ChattingSystem.message.dto.response;

public record SenderResponse(
        Long id,
        String fullName,
        String avatarUrl
) {}
