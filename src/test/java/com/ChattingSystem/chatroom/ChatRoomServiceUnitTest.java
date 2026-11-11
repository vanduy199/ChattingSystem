package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.chatroom.service.impl.ChatRoomServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceUnitTest {

    @Mock
    private ChatRoomRepository roomRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private RoomAccessService roomAccess;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ChatRoomServiceImpl service;

    private final LocationRequest here = new LocationRequest(21.0285, 105.8542);

    private User realNormalUser() {
        return User.create("uid-1", "user@example.com", "Test User", null, null);
    }

    private User realGuestUser() {
        return User.createGuest("Khách #1234");
    }

    @Test
    void joinRoom_roomNotFound_shouldThrowException() {
        Long roomId = 999L;
        when(roomAccess.requireActiveRoom(roomId))
                .thenThrow(new AppException(ErrorCode.ROOM_NOT_FOUND));

        assertThatThrownBy(() -> service.joinRoom(1L, roomId, here))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));

        verifyNoInteractions(memberRepo, events);
    }

    @Test
    void joinRoom_privateRoom_shouldSavePendingMember() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("private");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realNormalUser()));
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.empty());
        when(roomRepo.isWithinRoomRadius(roomId, here.lat(), here.lng())).thenReturn(true);
        RoomMember saved = mock(RoomMember.class);
        when(saved.getRole()).thenReturn(MemberRole.MEMBER);
        when(saved.getStatus()).thenReturn(MemberStatus.PENDING);
        when(saved.getJoinedAt()).thenReturn(Instant.now());
        when(memberRepo.save(any())).thenReturn(saved);

        com.ChattingSystem.chatroom.dto.response.JoinRoomResponse res = service.joinRoom(1L, roomId, here);
        assertThat(res.roomId()).isEqualTo(roomId);
        verify(memberRepo).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void joinRoom_guestUser_privateRoom_shouldThrowLoginRequired() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("private");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realGuestUser()));

        assertThatThrownBy(() -> service.joinRoom(1L, roomId, here))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_REQUIRED_FOR_PRIVATE_ROOM));

        verify(memberRepo, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void joinRoom_guestUser_publicRoom_shouldJoinSuccessfully() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realGuestUser()));
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.empty());
        when(roomRepo.isWithinRoomRadius(roomId, here.lat(), here.lng())).thenReturn(true);
        RoomMember saved = mock(RoomMember.class);
        when(saved.getRole()).thenReturn(MemberRole.MEMBER);
        when(saved.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(saved.getJoinedAt()).thenReturn(Instant.now());
        when(memberRepo.save(any())).thenReturn(saved);

        com.ChattingSystem.chatroom.dto.response.JoinRoomResponse res = service.joinRoom(1L, roomId, here);
        assertThat(res.roomId()).isEqualTo(roomId);
        verify(memberRepo).save(any());
        verify(events).publishEvent(any(com.ChattingSystem.realtime.event.RoomEvent.class));
    }

    @Test
    void joinRoom_outOfRange_shouldThrowAndNotSaveMember() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realNormalUser()));
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.empty());
        when(roomRepo.isWithinRoomRadius(roomId, here.lat(), here.lng())).thenReturn(false);

        assertThatThrownBy(() -> service.joinRoom(1L, roomId, here))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_RANGE));

        verify(memberRepo, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void joinRoom_alreadyMember_pending_shouldThrowPending() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realNormalUser()));
        RoomMember member = mock(RoomMember.class);
        when(member.getStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.joinRoom(1L, roomId, here))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_MEMBER_PENDING));
    }

    @Test
    void joinRoom_alreadyMember_active_shouldReturnResponse() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(1L)).thenReturn(Optional.of(realNormalUser()));
        RoomMember member = mock(RoomMember.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getRole()).thenReturn(MemberRole.MEMBER);
        when(member.getJoinedAt()).thenReturn(Instant.now());
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.of(member));

        com.ChattingSystem.chatroom.dto.response.JoinRoomResponse res = service.joinRoom(1L, roomId, here);
        assertThat(res.roomId()).isEqualTo(roomId);
        assertThat(res.userId()).isEqualTo(1L);
    }

    @Test
    void createRoom_whenGuestUser_shouldThrowLoginRequired() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(realGuestUser()));
        CreateRoomRequest req = new CreateRoomRequest("Phong chat", null, 21.0, 105.0, null, null);

        assertThatThrownBy(() -> service.createRoom(1L, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_REQUIRED_TO_CREATE_ROOM));

        verify(roomRepo, never()).saveAndFlush(any());
    }

    @Test
    void createRoom_whenUserHasThreeActiveRooms_shouldThrowLimitReached() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(realNormalUser()));
        when(roomRepo.countByCreatedByAndStatusAndExpiresAtAfter(eq(1L), eq(RoomStatus.ACTIVE), any()))
                .thenReturn(3L);

        CreateRoomRequest req = new CreateRoomRequest("Phong thu 4", null, 21.0, 105.0, null, null);

        assertThatThrownBy(() -> service.createRoom(1L, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_LIMIT_REACHED));

        verify(roomRepo, never()).saveAndFlush(any());
        verify(roomRepo, never()).updateCenter(anyLong(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }
}
