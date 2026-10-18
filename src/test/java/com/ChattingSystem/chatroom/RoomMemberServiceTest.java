
package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.chatroom.service.RoomMemberService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;

import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RoomMemberServiceTest {

    @Autowired
    private RoomMemberService roomMemberService;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;


    @Autowired
    private UserRepository userRepository;

    private Long ownerId;
    private Long memberId;
    private Long otherUserId;

    @BeforeEach
    void setUpUsers() {
        ownerId = createTestUser("owner").getId();
        memberId = createTestUser("member").getId();
        otherUserId = createTestUser("other").getId();
    }

    private User createTestUser(String prefix) {
        String uniqueId = UUID.randomUUID().toString();

        User user = User.create(
                "test-firebase-" + uniqueId,
                prefix + "-" + uniqueId + "@example.test",
                "Test " + prefix,
                null,
                null
        );

        return userRepository.saveAndFlush(user);
    }



    // =====================================================
    // Helper methods
    // =====================================================

    private Long createRoom(Long ownerId) {
        CreateRoomRequest request = new CreateRoomRequest(
                "Integration test room",       // title
                "Room for integration testing", // description
                21.0285,                        // latitude
                105.8542,                       // longitude
                60,                             // defaultLiveTime (phút)
                1000                            // radiusM (mét)
        );

        return chatRoomService.createRoom(ownerId, request).id();
    }

    private Long createRoomAndJoin(Long ownerId, Long memberId) {
        Long roomId = createRoom(ownerId);
        chatRoomService.joinRoom(memberId, roomId);
        return roomId;
    }

    private void changeRoomStatus(Long roomId, RoomStatus status) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow();

        ReflectionTestUtils.setField(room, "status", status);
        chatRoomRepository.saveAndFlush(room);
    }

    private void expireRoom(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow();

        ReflectionTestUtils.setField(
                room,
                "expiresAt",
                Instant.now().minusSeconds(60)
        );

        chatRoomRepository.saveAndFlush(room);
    }

    // =====================================================
    // GET /rooms/me
    // =====================================================

    @Test
    void getMyActiveRooms_shouldReturnOnlyActiveAndUnexpiredRooms() {
        Long activeRoomId = createRoomAndJoin(ownerId, memberId);

        Long expiredRoomId = createRoomAndJoin(
                otherUserId, memberId
        );
        expireRoom(expiredRoomId);

        Long closedRoomId = createRoomAndJoin(
                otherUserId, memberId
        );
        changeRoomStatus(closedRoomId, RoomStatus.CLOSED);

        Page<MyRoomResponse> result =
                roomMemberService.getMyActiveRooms(
                        memberId,
                        PageRequest.of(0, 20)
                );

        List<Long> returnedRoomIds = result.getContent()
                .stream()
                .map(MyRoomResponse::roomId)
                .toList();

        assertTrue(returnedRoomIds.contains(activeRoomId));
        assertFalse(returnedRoomIds.contains(expiredRoomId));
        assertFalse(returnedRoomIds.contains(closedRoomId));
    }

    @Test
    void getMyActiveRooms_shouldReturnEmptyPage_whenUserHasNoRooms() {
        Page<MyRoomResponse> result =
                roomMemberService.getMyActiveRooms(
                        otherUserId,
                        PageRequest.of(0, 20)
                );

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
    }

    @Test
    void getMyActiveRooms_shouldRespectPagination() {
        createRoomAndJoin(ownerId, memberId);
        createRoomAndJoin(otherUserId, memberId);
        createRoomAndJoin(ownerId, memberId);

        Page<MyRoomResponse> firstPage =
                roomMemberService.getMyActiveRooms(
                        memberId,
                        PageRequest.of(0, 2)
                );

        Page<MyRoomResponse> secondPage =
                roomMemberService.getMyActiveRooms(
                        memberId,
                        PageRequest.of(1, 2)
                );

        assertEquals(2, firstPage.getContent().size());
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());

        assertEquals(1, secondPage.getContent().size());
        assertEquals(3, secondPage.getTotalElements());
        assertEquals(2, secondPage.getTotalPages());
    }

    // =====================================================
    // DELETE /rooms/{roomId}/members/me
    // =====================================================

    @Test
    void leaveRoom_shouldDeleteMember_whenMemberAndRoomAreValid() {
        Long roomId = createRoomAndJoin(ownerId, memberId);

        assertTrue(
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId, memberId
                )
        );

        roomMemberService.leaveRoom(memberId, roomId);
        roomMemberRepository.flush();

        assertFalse(
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId, memberId
                )
        );
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomDoesNotExist() {
        Long nonExistingRoomId = 999999999L;

        AppException exception = assertThrows(
                AppException.class,
                () -> roomMemberService.leaveRoom(
                        memberId, nonExistingRoomId
                )
        );

        assertEquals(
                ErrorCode.ROOM_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomIsClosed() {
        Long roomId = createRoomAndJoin(ownerId, memberId);
        changeRoomStatus(roomId, RoomStatus.CLOSED);

        AppException exception = assertThrows(
                AppException.class,
                () -> roomMemberService.leaveRoom(memberId, roomId)
        );

        assertEquals(
                ErrorCode.ROOM_EXPIRED,
                exception.getErrorCode()
        );

        assertTrue(
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId, memberId
                )
        );
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomHasExpired() {
        Long roomId = createRoomAndJoin(ownerId, memberId);
        expireRoom(roomId);

        AppException exception = assertThrows(
                AppException.class,
                () -> roomMemberService.leaveRoom(memberId, roomId)
        );

        assertEquals(
                ErrorCode.ROOM_EXPIRED,
                exception.getErrorCode()
        );

        assertTrue(
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId, memberId
                )
        );
    }

    @Test
    void leaveRoom_shouldThrow_whenUserHasNotJoinedRoom() {
        Long roomId = createRoom(ownerId);

        AppException exception = assertThrows(
                AppException.class,
                () -> roomMemberService.leaveRoom(
                        otherUserId, roomId
                )
        );

        assertEquals(
                ErrorCode.ROOM_MEMBER_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void leaveRoom_shouldThrow_whenUserIsOwner() {
        Long roomId = createRoom(ownerId);

        AppException exception = assertThrows(
                AppException.class,
                () -> roomMemberService.leaveRoom(ownerId, roomId)
        );

        assertEquals(
                ErrorCode.OWNER_CANNOT_LEAVE_ROOM,
                exception.getErrorCode()
        );

        // Owner vẫn phải còn trong danh sách thành viên.
        assertTrue(
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId, ownerId
                )
        );
    }
}
