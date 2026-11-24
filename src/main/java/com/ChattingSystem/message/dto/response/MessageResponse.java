package com.ChattingSystem.message.dto.response;

import com.ChattingSystem.message.entity.MessageType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        Long id,
        Long roomId,
        UUID clientMessageId,
        MessageType type,
        String content,
        SenderResponse sender,
        Instant createdAt,
        List<AttachmentResponse> attachments
) {
    public MessageResponse {
        attachments = (attachments == null) ? List.of() : Collections.unmodifiableList(attachments);
    }

    /** Constructor 7 tham số tương thích ngược khi không truyền attachments */
    public MessageResponse(Long id, Long roomId, UUID clientMessageId, MessageType type, String content,
                           SenderResponse sender, Instant createdAt) {
        this(id, roomId, clientMessageId, type, content, sender, createdAt, List.of());
    }

    /** Constructor cho JPQL {@code SELECT new ...}: không lồng được record con trong câu query. */
    public MessageResponse(Long id, Long roomId, UUID clientMessageId, MessageType type, String content,
                           Long senderId, String senderName, String senderAvatar, Instant createdAt) {
        this(id, roomId, clientMessageId, type, content,
                new SenderResponse(senderId, senderName, senderAvatar), createdAt, List.of());
    }

    /** Helper method để gán danh sách tệp đính kèm */
    public MessageResponse withAttachments(List<AttachmentResponse> attachments) {
        return new MessageResponse(id, roomId, clientMessageId, type, content, sender, createdAt, attachments);
    }
}
