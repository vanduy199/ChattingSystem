package com.ChattingSystem.message.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType type;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Message(Long roomId, Long senderId, UUID clientMessageId, MessageType type, String content) {
        this.roomId = Objects.requireNonNull(roomId);
        this.senderId = Objects.requireNonNull(senderId);
        this.clientMessageId = Objects.requireNonNull(clientMessageId);
        this.type = Objects.requireNonNull(type);
        this.content = Objects.requireNonNull(content);
    }

    public static Message text(Long roomId, Long senderId, UUID clientMessageId, String content) {
        return new Message(roomId, senderId, clientMessageId, MessageType.TEXT, content);
    }
}
