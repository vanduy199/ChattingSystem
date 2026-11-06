
package com.ChattingSystem.chatroom.repository;

import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomMemberResponse;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository
        extends JpaRepository<RoomMember, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    @Query(
            value = """
            SELECT new com.ChattingSystem.chatroom.dto.response.MyRoomResponse(
                rm.roomId,
                rm.userId,
                cr.title,
                cr.description,
                cr.createdBy,
                cr.status,
                cr.expiresAt,
                rm.role,
                rm.joinedAt
            )
            FROM RoomMember rm
            JOIN ChatRoom cr ON cr.id = rm.roomId
            WHERE rm.userId = :userId
              AND cr.status = :status
              AND cr.expiresAt > :now
            """,
            countQuery = """
            SELECT COUNT(rm.id)
            FROM RoomMember rm
            JOIN ChatRoom cr ON cr.id = rm.roomId
            WHERE rm.userId = :userId
              AND cr.status = :status
              AND cr.expiresAt > :now
            """
    )
    Page<MyRoomResponse> findActiveRoomsByUserId(
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("status") RoomStatus status,
            Pageable pageable
    );


    Optional<RoomMember> findByRoomIdAndUserId(
            Long roomId,
            Long userId
    );

    long countByRoomId(Long roomId);

    @Query("""
            SELECT new com.ChattingSystem.chatroom.dto.response.RoomMemberResponse(
                u.id, u.fullName, u.avatarObject, rm.role, rm.joinedAt
            )
            FROM RoomMember rm
            JOIN User u ON u.id = rm.userId
            WHERE rm.roomId = :roomId
            ORDER BY rm.joinedAt
            """)
    List<RoomMemberResponse> findMembers(@Param("roomId") Long roomId);
}
