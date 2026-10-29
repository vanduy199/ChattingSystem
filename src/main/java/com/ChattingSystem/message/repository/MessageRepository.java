package com.ChattingSystem.message.repository;

import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySenderIdAndClientMessageId(Long senderId, UUID clientMessageId);

    // before = Long.MAX_VALUE → trang mới nhất
    @Query("""
            SELECT new com.ChattingSystem.message.dto.response.MessageResponse(
                m.id, m.roomId, m.clientMessageId, m.type, m.content,
                u.id, u.fullName, u.avatarObject, m.createdAt
            )
            FROM Message m
            JOIN User u ON u.id = m.senderId
            WHERE m.roomId = :roomId
              AND m.deletedAt IS NULL
              AND m.id < :before
            ORDER BY m.id DESC
            """)
    List<MessageResponse> findSlice(@Param("roomId") Long roomId,
                                    @Param("before") Long before,
                                    Pageable pageable);

    @Query("""
            SELECT new com.ChattingSystem.message.dto.response.MessageResponse(
                m.id, m.roomId, m.clientMessageId, m.type, m.content,
                u.id, u.fullName, u.avatarObject, m.createdAt
            )
            FROM Message m
            JOIN User u ON u.id = m.senderId
            WHERE m.id = :id
            """)
    Optional<MessageResponse> findResponseById(@Param("id") Long id);
}
