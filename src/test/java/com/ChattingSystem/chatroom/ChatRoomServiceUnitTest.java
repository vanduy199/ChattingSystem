package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.chatroom.service.impl.ChatRoomServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
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
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("private");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.empty());
        when(roomRepo.isWithinRoomRadius(roomId, here.lat(), here.lng())).thenReturn(true);
        com.ChattingSystem.chatroom.entity.RoomMember saved = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.RoomMember.class);
        when(saved.getRole()).thenReturn(com.ChattingSystem.chatroom.entity.MemberRole.MEMBER);
        when(saved.getJoinedAt()).thenReturn(Instant.now());
        when(memberRepo.save(any())).thenReturn(saved);
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        com.ChattingSystem.chatroom.dto.response.JoinRoomResponse res = service.joinRoom(1L, roomId, here);
        assertThat(res.roomId()).isEqualTo(roomId);
        verify(memberRepo).save(any());
    }

    @Test
    void joinRoom_outOfRange_shouldThrowAndNotSaveMember() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
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
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        com.ChattingSystem.chatroom.entity.RoomMember member = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.RoomMember.class);
        when(member.getStatus()).thenReturn(com.ChattingSystem.chatroom.entity.MemberStatus.PENDING);
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.joinRoom(1L, roomId, here))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_MEMBER_PENDING));
    }

    @Test
    void joinRoom_alreadyMember_active_shouldReturnResponse() {
        Long roomId = 5L;
        com.ChattingSystem.chatroom.entity.ChatRoom mockRoom = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.ChatRoom.class);
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        com.ChattingSystem.chatroom.entity.RoomMember member = org.mockito.Mockito.mock(com.ChattingSystem.chatroom.entity.RoomMember.class);
        when(member.getStatus()).thenReturn(com.ChattingSystem.chatroom.entity.MemberStatus.ACTIVE);
        when(member.getRole()).thenReturn(com.ChattingSystem.chatroom.entity.MemberRole.MEMBER);
        when(member.getJoinedAt()).thenReturn(Instant.now());
        when(memberRepo.findByRoomIdAndUserId(roomId, 1L)).thenReturn(Optional.of(member));

        com.ChattingSystem.chatroom.dto.response.JoinRoomResponse res = service.joinRoom(1L, roomId, here);
        assertThat(res.roomId()).isEqualTo(roomId);
        assertThat(res.userId()).isEqualTo(1L);
    }

    @Test
    void createRoom_whenUserHasThreeActiveRooms_shouldThrowLimitReached() {
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
