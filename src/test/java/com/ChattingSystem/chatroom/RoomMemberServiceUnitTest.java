package com.ChattingSystem.chatroom;


import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;

import com.ChattingSystem.chatroom.service.impl.RoomMemberServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomMemberServiceUnitTest {
    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private RoomMemberServiceImpl service;

    private final Long roomId = 1L;
    private final Long userId = 10L;

    private ChatRoom activeRoom;
    private RoomMember member;

    @BeforeEach
    void setUp() {
        activeRoom = mock(ChatRoom.class);
        member = mock(RoomMember.class);
    }

    @Test
    void getMyActiveRooms_shouldReturnRoomsFromRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Instant now = Instant.now();

        MyRoomResponse room = new MyRoomResponse(
                1L,
                userId,
                "Phòng hỗ trợ",
                "Trao đổi thông tin khu vực",
                10L,
                RoomStatus.ACTIVE,
                now.plusSeconds(3600),
                MemberRole.MEMBER,
                now
        );

        Page<MyRoomResponse> expected = new PageImpl<>(
                List.of(room),
                pageable,
                1
        );

        when(roomMemberRepository.findActiveRoomsByUserId(
                eq(userId),
                any(Instant.class),
                eq(RoomStatus.ACTIVE),
                eq(pageable)
        )).thenReturn(expected);

        Page<MyRoomResponse> actual =
                service.getMyActiveRooms(userId, pageable);

        assertEquals(1, actual.getTotalElements());
        assertEquals(1, actual.getTotalPages());
        assertEquals(room, actual.getContent().get(0));

        verify(roomMemberRepository).findActiveRoomsByUserId(
                eq(userId),
                any(Instant.class),
                eq(RoomStatus.ACTIVE),
                eq(pageable)
        );
    }

    @Test
    void getMyActiveRooms_shouldReturnEmptyPage_whenUserHasNoRooms() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<MyRoomResponse> emptyPage = new PageImpl<>(
                List.of(),
                pageable,
                0
        );

        when(roomMemberRepository.findActiveRoomsByUserId(
                eq(userId),
                any(Instant.class),
                eq(RoomStatus.ACTIVE),
                eq(pageable)
        )).thenReturn(emptyPage);

        Page<MyRoomResponse> actual =
                service.getMyActiveRooms(userId, pageable);

        assertTrue(actual.isEmpty());
        assertEquals(0, actual.getTotalElements());
        assertEquals(0, actual.getTotalPages());

        verify(roomMemberRepository).findActiveRoomsByUserId(
                eq(userId),
                any(Instant.class),
                eq(RoomStatus.ACTIVE),
                eq(pageable)
        );
    }

    @Test
    void leaveRoom_shouldDeleteMember_whenMemberAndRoomAreValid() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.of(activeRoom));
        when(activeRoom.getStatus())
                .thenReturn(RoomStatus.ACTIVE);
        when(activeRoom.getExpiresAt())
                .thenReturn(Instant.now().plusSeconds(3600));

        when(roomMemberRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(member));
        when(member.getRole())
                .thenReturn(MemberRole.MEMBER);

        assertDoesNotThrow(() -> service.leaveRoom(userId, roomId));

        verify(roomMemberRepository).delete(member);
        verify(events).publishEvent(argThat((Object e) ->
                e instanceof RoomEvent re && re.type() == RoomEventType.MEMBER_LEFT && re.roomId().equals(roomId)));
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomDoesNotExist() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.leaveRoom(userId, roomId)
        );

        assertEquals(ErrorCode.ROOM_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(roomMemberRepository);
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomIsClosed() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.of(activeRoom));

        when(activeRoom.getStatus())
                .thenReturn(RoomStatus.CLOSED);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.leaveRoom(userId, roomId)
        );

        assertEquals(
                ErrorCode.ROOM_EXPIRED,
                exception.getErrorCode()
        );

        verify(roomMemberRepository, never())
                .delete(any(RoomMember.class));
    }

    @Test
    void leaveRoom_shouldThrow_whenRoomHasExpired() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.of(activeRoom));

        when(activeRoom.getStatus())
                .thenReturn(RoomStatus.ACTIVE);

        when(activeRoom.getExpiresAt())
                .thenReturn(Instant.now().minusSeconds(60));

        AppException exception = assertThrows(
                AppException.class,
                () -> service.leaveRoom(userId, roomId)
        );

        assertEquals(
                ErrorCode.ROOM_EXPIRED,
                exception.getErrorCode()
        );

        verify(roomMemberRepository, never())
                .delete(any(RoomMember.class));
    }

    @Test
    void leaveRoom_shouldThrow_whenUserHasNotJoinedRoom() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.of(activeRoom));

        when(activeRoom.getStatus())
                .thenReturn(RoomStatus.ACTIVE);

        when(activeRoom.getExpiresAt())
                .thenReturn(Instant.now().plusSeconds(3600));

        when(roomMemberRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.leaveRoom(userId, roomId)
        );

        assertEquals(
                ErrorCode.ROOM_MEMBER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(roomMemberRepository, never())
                .delete(any(RoomMember.class));
    }

    @Test
    void leaveRoom_shouldThrow_whenUserIsOwner() {
        when(chatRoomRepository.findById(roomId))
                .thenReturn(Optional.of(activeRoom));

        when(activeRoom.getStatus())
                .thenReturn(RoomStatus.ACTIVE);

        when(activeRoom.getExpiresAt())
                .thenReturn(Instant.now().plusSeconds(3600));

        when(roomMemberRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(member));

        when(member.getRole())
                .thenReturn(MemberRole.OWNER);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.leaveRoom(userId, roomId)
        );

        assertEquals(
                ErrorCode.OWNER_CANNOT_LEAVE_ROOM,
                exception.getErrorCode()
        );

        verify(roomMemberRepository, never())
                .delete(any(RoomMember.class));
    }
}
