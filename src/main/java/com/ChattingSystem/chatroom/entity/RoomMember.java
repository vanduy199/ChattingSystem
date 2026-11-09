package com.ChattingSystem.chatroom.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "room_members",
       uniqueConstraints = @UniqueConstraint(name = "uq_room_members",
                                             columnNames = {"room_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    public void setStatus(MemberStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    private RoomMember(Long roomId, Long userId, MemberRole role, MemberStatus status) {
        this.roomId = Objects.requireNonNull(roomId);
        this.userId = Objects.requireNonNull(userId);
        this.role = Objects.requireNonNull(role);
        this.joinedAt = Instant.now();
        this.status = Objects.requireNonNull(status);
    }

    public static RoomMember create(Long roomId, Long userId, MemberRole role, MemberStatus status) {
        return new RoomMember(roomId, userId, role, status);
    }
}