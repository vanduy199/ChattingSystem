package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.impl.RoomAccessServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomAccessServiceUnitTest {

    @Mock
    private ChatRoomRepository roomRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @InjectMocks
    private RoomAccessServiceImpl roomAccess;

    private ChatRoom room(RoomStatus status, Instant expiresAt) {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getStatus()).thenReturn(status);
        if (status == RoomStatus.ACTIVE) {
            when(room.getExpiresAt()).thenReturn(expiresAt);
        }
        return room;
    }

    @Test
    void requireActiveRoom_notFound() {
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomAccess.requireActiveRoom(1L))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
    }

    @Test
    void requireActiveRoom_expired() {
        ChatRoom expired = room(RoomStatus.ACTIVE, Instant.now().minusSeconds(1));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> roomAccess.requireActiveRoom(1L))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_EXPIRED));
    }

    @Test
    void requireActiveRoom_closed() {
        ChatRoom closed = room(RoomStatus.CLOSED, null);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> roomAccess.requireActiveRoom(1L))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_EXPIRED));
    }

    @Test
    void requireActiveMember_notMember() {
        ChatRoom active = room(RoomStatus.ACTIVE, Instant.now().plusSeconds(600));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(active));
        when(memberRepo.findByRoomIdAndUserId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomAccess.requireActiveMember(7L, 1L))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
    }

    @Test
    void requireActiveMember_ok() {
        ChatRoom active = room(RoomStatus.ACTIVE, Instant.now().plusSeconds(600));
        RoomMember member = mock(RoomMember.class);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(active));
        when(memberRepo.findByRoomIdAndUserId(1L, 7L)).thenReturn(Optional.of(member));

        assertThat(roomAccess.requireActiveMember(7L, 1L)).isSameAs(member);
    }
}
