package com.ChattingSystem.chatroom.repository;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

        // Lưu ý thứ tự ST_MakePoint(lng, lat), không phải (lat, lng).
        // Dùng CAST thay cho "::" để Spring không hiểu nhầm là tham số.
        @Modifying
        @Query(value = """
                        UPDATE chat_rooms
                        SET center = CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
                            display_center = CAST(ST_SetSRID(ST_MakePoint(:displayLng, :displayLat), 4326) AS geography)
                        WHERE id = :id
                        """, nativeQuery = true)
        void updateCenter(@Param("id") Long id,
                        @Param("lat") double lat,
                        @Param("lng") double lng,
                        @Param("displayLat") double displayLat,
                        @Param("displayLng") double displayLng);

        long countByCreatedByAndStatusAndExpiresAtAfter(Long createdBy, RoomStatus status, Instant now);


    /**
     * Phòng đang hoạt động mà người dùng nhìn thấy: nằm trong bán kính quét của họ
     * và họ đứng trong bán kính của phòng. Khoảng cách tính từ tâm đã làm mờ.
     */
    @Query(value = """
            SELECT r.id                AS "id",
                   r.title             AS "title",
                   r.description       AS "description",
                   r.radius_m          AS "radiusM",
                   r.expires_at        AS "expiresAt",
                   r.room_type         AS "roomType",
                   ST_Distance(r.display_center, me.point) AS "distanceM",
                   (SELECT COUNT(*) FROM room_members m WHERE m.room_id = r.id) AS "memberCount",
                   EXISTS (SELECT 1 FROM room_members m
                           WHERE m.room_id = r.id AND m.user_id = :userId AND m.status = 'ACTIVE') AS "joined"
            FROM chat_rooms r,
                 (SELECT CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography) AS point) me
            WHERE r.status = 'ACTIVE'
              AND (r.expires_at > now() OR r.expires_at IS NULL)
              AND ST_DWithin(r.display_center, me.point, LEAST(:radius, r.radius_m))
            ORDER BY "distanceM"
            LIMIT 50
            """, nativeQuery = true)

        List<NearbyRoomRow> findNearby(@Param("lat") double lat,
                        @Param("lng") double lng,
                        @Param("radius") int radius,
                        @Param("userId") Long userId);

        @Query(value = """
                        SELECT COALESCE(ST_DWithin(r.display_center,
                                                   CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
                                                   r.radius_m), false)
                        FROM chat_rooms r
                        WHERE r.id = :id
                        """, nativeQuery = true)
        boolean isWithinRoomRadius(@Param("id") Long id,
                        @Param("lat") double lat,
                        @Param("lng") double lng);

        interface NearbyRoomRow {
                Long getId();

                String getTitle();

                String getDescription();

                Integer getRadiusM();

                Instant getExpiresAt();

                Double getDistanceM();

                Long getMemberCount();

                Boolean getJoined();

                String getRoomType();
        }

        @Modifying(clearAutomatically = true)
        @Query("UPDATE ChatRoom cr SET cr.createdBy = :targetUserId WHERE cr.createdBy = :guestUserId")
        int reassignCreatedBy(@Param("guestUserId") Long guestUserId, @Param("targetUserId") Long targetUserId);
}
