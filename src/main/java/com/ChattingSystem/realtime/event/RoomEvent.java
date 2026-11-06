package com.ChattingSystem.realtime.event;

import java.util.Objects;

/**
 * Sự kiện phát lên {@code /topic/rooms.{roomId}}. Service publish qua
 * {@code ApplicationEventPublisher}; {@link RoomEventBroadcaster} chỉ gửi đi sau khi transaction commit.
 * {@code roomId} không nằm trong payload gửi cho client.
 */
public record RoomEvent(Long roomId, RoomEventType type, Object data) {

    public RoomEvent {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(type);
    }

    /** Payload thật sự gửi cho client: {@code { type, data }}. */
    public record Payload(RoomEventType type, Object data) {
    }

    public Payload payload() {
        return new Payload(type, data);
    }
}
