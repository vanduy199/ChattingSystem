package com.ChattingSystem.chatroom.repository;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // Lưu ý thứ tự ST_MakePoint(lng, lat), không phải (lat, lng).
    // Dùng CAST thay cho "::" để Spring không hiểu nhầm là tham số.
    @Modifying
    @Query(value = """
            UPDATE chat_rooms
            SET center = CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)
            WHERE id = :id
            """, nativeQuery = true)
    void updateCenter(@Param("id") Long id,
                      @Param("lat") double lat,
                      @Param("lng") double lng);
}