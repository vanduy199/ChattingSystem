package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.dto.response.NearbyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.service.impl.ChatRoomServiceImpl;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test service tạo phòng, không cần Firebase/JWT.
 * Chạy trên DB trong .env. @Transactional ở lớp test => mỗi test tự rollback,
 * không để lại dữ liệu trong DB.
 */
@SpringBootTest
@Transactional
class ChatRoomServiceTest {

    @Autowired private ChatRoomServiceImpl service;
    @Autowired private UserRepository userRepo;
    @Autowired private EntityManager em;

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepo.saveAndFlush(User.create(
                "test-uid-" + id, "test-" + id + "@example.com", "Test User", null, null));
    }

    @Test
    void createRoom_savesRoomCenterAndOwner() {
        User user = newUser();
        CreateRoomRequest req = new CreateRoomRequest(
                "Ngap duong Nguyen Khuyen", "Nuoc ngap 30cm", 21.0285, 105.8542, 120, 2000);

        Instant before = Instant.now();
        RoomResponse res = service.createRoom(user.getId(), req);
        Instant after = Instant.now();

        // 1. Response
        assertThat(res.id()).isNotNull();
        assertThat(res.title()).isEqualTo("Ngap duong Nguyen Khuyen");
        assertThat(res.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(res.radiusM()).isEqualTo(2000);
        assertThat(res.createdAt()).isNotNull();
        assertThat(res.expiresAt()).isBetween(
                before.plus(120, ChronoUnit.MINUTES), after.plus(120, ChronoUnit.MINUTES));

        // 2. Cột center (PostGIS) phải được ghi đúng: lat = Y, lng = X
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT ST_Y(CAST(center AS geometry)), ST_X(CAST(center AS geometry))
                FROM chat_rooms WHERE id = :id
                """)
                .setParameter("id", res.id())
                .getSingleResult();
        assertThat(((Number) row[0]).doubleValue()).isEqualTo(21.0285, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(((Number) row[1]).doubleValue()).isEqualTo(105.8542, org.assertj.core.data.Offset.offset(0.000001));

        // 3. Người tạo phải là OWNER của phòng
        String role = (String) em.createNativeQuery("""
                SELECT role FROM room_members WHERE room_id = :rid AND user_id = :uid
                """)
                .setParameter("rid", res.id())
                .setParameter("uid", user.getId())
                .getSingleResult();
        assertThat(role).isEqualTo("OWNER");
    }

    @Test
    void createRoom_usesDefaultsWhenTtlAndRadiusMissing() {
        User user = newUser();
        CreateRoomRequest req = new CreateRoomRequest("Phong mac dinh", null, 21.0, 105.0, null, null);

        Instant before = Instant.now();
        RoomResponse res = service.createRoom(user.getId(), req);
        Instant after = Instant.now();

        assertThat(res.radiusM()).isEqualTo(3000);
        assertThat(res.expiresAt()).isBetween(
                before.plus(60, ChronoUnit.MINUTES), after.plus(60, ChronoUnit.MINUTES));
    }

    @Test
    void joinRoom_success_shouldSaveMember() {
        User owner = newUser();
        User member = newUser();

        CreateRoomRequest req = new CreateRoomRequest(
                "Phong test join",
                "Phong dung de test",
                21.0285,
                105.8542,
                120,
                2000
        );

        RoomResponse room = service.createRoom(owner.getId(), req);

        JoinRoomResponse response =
                service.joinRoom(member.getId(), room.id(), new LocationRequest(21.0285, 105.8542));

        assertThat(response.roomId()).isEqualTo(room.id());
        assertThat(response.userId()).isEqualTo(member.getId());
        assertThat(response.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(response.joinedAt()).isNotNull();

        Long count = ((Number) em.createNativeQuery("""
            SELECT COUNT(*)
            FROM room_members
            WHERE room_id = :roomId
              AND user_id = :userId
            """)
                .setParameter("roomId", room.id())
                .setParameter("userId", member.getId())
                .getSingleResult()).longValue();

        assertThat(count).isEqualTo(1L);
    }


    @Test
    void joinRoom_roomNotFound_shouldThrowException() {
        User user = newUser();
        Long roomId = -999L;

        assertThatThrownBy(() ->
                service.joinRoom(user.getId(), roomId, new LocationRequest(21.0, 105.0)))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_NOT_FOUND)
                );
    }

    @Test
    void joinRoom_expiredRoom_shouldThrowException() {
        User owner = newUser();
        User member = newUser();

        CreateRoomRequest req = new CreateRoomRequest(
                "Phong da het han",
                null,
                21.0,
                105.0,
                60,
                2000
        );

        RoomResponse room = service.createRoom(owner.getId(), req);

        // Giả lập phòng đã hết hạn
        em.createNativeQuery("""
            UPDATE chat_rooms
            SET expires_at = :expiredAt
            WHERE id = :roomId
            """)
                .setParameter(
                        "expiredAt",
                        Instant.now().minus(10, ChronoUnit.MINUTES)
                )
                .setParameter("roomId", room.id())
                .executeUpdate();

        em.flush();
        em.clear();

        // Kiểm tra đúng mã lỗi
        assertThatThrownBy(() ->
                service.joinRoom(member.getId(), room.id(), new LocationRequest(21.0, 105.0)))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_EXPIRED)
                );
    }

    // =====================================================
    // Làm mờ vị trí + quét bán kính
    // =====================================================

    private double displayDistanceMeters(Long roomId) {
        return ((Number) em.createNativeQuery("""
                SELECT ST_Distance(center, display_center) FROM chat_rooms WHERE id = :id
                """)
                .setParameter("id", roomId)
                .getSingleResult()).doubleValue();
    }

    @Test
    void createRoom_storesFuzzedDisplayCenterAwayFromRealCenter() {
        User user = newUser();
        RoomResponse res = service.createRoom(user.getId(),
                new CreateRoomRequest("Phong lam mo", null, 21.028511, 105.854178, 60, 1000));

        double d = displayDistanceMeters(res.id());
        assertThat(d).isGreaterThan(0).isLessThan(240);
    }

    @Test
    void findNearby_returnsRoomWithApproximateDistanceAndJoinedFlag() {
        User owner = newUser();
        User viewer = newUser();
        RoomResponse room = service.createRoom(owner.getId(),
                new CreateRoomRequest("Phong gan day", "mo ta", 21.0285, 105.8542, 60, 3000));

        // Người xem đứng cách ~1 km về phía bắc
        List<NearbyRoomResponse> forViewer = service.findNearby(viewer.getId(), 21.0375, 105.8542, 3000);
        NearbyRoomResponse found = forViewer.stream().filter(r -> r.id().equals(room.id())).findFirst().orElseThrow();
        assertThat(found.joined()).isFalse();
        assertThat(found.memberCount()).isEqualTo(1);
        assertThat(found.distanceLabel()).startsWith("Khoảng ");

        List<NearbyRoomResponse> forOwner = service.findNearby(owner.getId(), 21.0285, 105.8542, 1000);
        assertThat(forOwner).filteredOn(r -> r.id().equals(room.id()))
                .singleElement().extracting(NearbyRoomResponse::joined).isEqualTo(true);
    }

    @Test
    void findNearby_respectsScanRadiusRoomRadiusAndExpiry() {
        User owner = newUser();
        User viewer = newUser();
        RoomResponse small = service.createRoom(owner.getId(),
                new CreateRoomRequest("Phong ban kinh nho", null, 10.7769, 106.7009, 60, 300));
        RoomResponse expired = service.createRoom(owner.getId(),
                new CreateRoomRequest("Phong het han", null, 10.7769, 106.7009, 60, 3000));
        em.createNativeQuery("UPDATE chat_rooms SET expires_at = now() - interval '1 minute' WHERE id = :id")
                .setParameter("id", expired.id()).executeUpdate();

        // Cách ~2 km: ngoài bán kính 300 m của phòng dù quét 3 km
        List<Long> far = service.findNearby(viewer.getId(), 10.7949, 106.7009, 3000)
                .stream().map(NearbyRoomResponse::id).toList();
        assertThat(far).doesNotContain(small.id(), expired.id());

        // Đứng ngay tại chỗ: thấy phòng nhỏ, không thấy phòng hết hạn
        List<Long> near = service.findNearby(viewer.getId(), 10.7769, 106.7009, 3000)
                .stream().map(NearbyRoomResponse::id).toList();
        assertThat(near).contains(small.id()).doesNotContain(expired.id());
    }

    @Test
    void joinRoom_outOfRange_shouldThrowException() {
        User owner = newUser();
        User member = newUser();
        RoomResponse room = service.createRoom(owner.getId(),
                new CreateRoomRequest("Phong xa", null, 16.0544, 108.2022, 60, 500));

        assertThatThrownBy(() -> service.joinRoom(member.getId(), room.id(), new LocationRequest(16.0744, 108.2022)))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_RANGE));
    }

    @Test
    void getRoom_nonMember_shouldThrowNotRoomMember() {
        User owner = newUser();
        User stranger = newUser();
        RoomResponse room = service.createRoom(owner.getId(),
                new CreateRoomRequest("Phong rieng", null, 21.0, 105.0, 60, 1000));

        assertThat(service.getRoom(owner.getId(), room.id()).myRole()).isEqualTo(MemberRole.OWNER);
        assertThatThrownBy(() -> service.getRoom(stranger.getId(), room.id()))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
    }
}
