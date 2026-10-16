package com.ChattingSystem.chatroom.entity;

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
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "default_live_time", nullable = false)
    private Integer defaultLiveTime;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // KHÔNG map cột "center" (geography). Ghi/đọc bằng native query trong repository.

    private ChatRoom(String title, String description, Long createdBy,
                     int ttlMinutes, int radiusM) {
        this.title = Objects.requireNonNull(title);
        this.description = description;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.defaultLiveTime = ttlMinutes;
        this.radiusM = radiusM;
        this.status = RoomStatus.ACTIVE;
        this.expiresAt = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
    }

    public static ChatRoom create(String title, String description, Long createdBy,
                                  int ttlMinutes, int radiusM) {
        return new ChatRoom(title, description, createdBy, ttlMinutes, radiusM);
    }
}