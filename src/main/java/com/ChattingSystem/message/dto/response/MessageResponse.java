package com.ChattingSystem.message.dto.response;

import com.ChattingSystem.message.entity.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        Long id,
        Long roomId,
        UUID clientMessageId,
        MessageType type,
        String content,
        SenderResponse sender,
        Instant createdAt
) {
    /** Constructor cho JPQL {@code SELECT new ...}: không lồng được record con trong câu query. */
    public MessageResponse(Long id, Long roomId, UUID clientMessageId, MessageType type, String content,
                           Long senderId, String senderName, String senderAvatar, Instant createdAt) {
        this(id, roomId, clientMessageId, type, content,
                new SenderResponse(senderId, senderName, senderAvatar), createdAt);
    }
}
