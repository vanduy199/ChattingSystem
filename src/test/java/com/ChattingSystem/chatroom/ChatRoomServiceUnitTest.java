
package com.ChattingSystem.chatroom;

import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.impl.ChatRoomServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import java.lang.reflect.Field;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceUnitTest {

    @Mock
    private ChatRoomRepository roomRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @InjectMocks
    private ChatRoomServiceImpl service;

    @Test
    void joinRoom_roomNotFound_shouldThrowException() {
        // Arrange: giả lập không tìm thấy phòng
        Long roomId = 999L;
        Long userId = 1L;

        when(roomRepo.findById(roomId))
                .thenReturn(Optional.empty());

        // Act + Assert: gọi service và kiểm tra exception
        assertThatThrownBy(() -> service.joinRoom(userId, roomId))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_NOT_FOUND));

        // Verify: service đã tìm phòng và không xử lý thành viên
        verify(roomRepo).findById(roomId);
        verifyNoInteractions(memberRepo);
    }

    @Test
    void joinRoom_expiredRoom_shouldThrowException() throws Exception {
        Long roomId = 999L;
        Long userId = 1L;

        ChatRoom expiredRoom = ChatRoom.create(
                "Phong het han",
                null,
                10L,
                60,
                2000
        );

        setField(expiredRoom, "expiresAt", Instant.now().minusSeconds(60));

        when(roomRepo.findById(roomId))
                .thenReturn(Optional.of(expiredRoom));

        assertThatThrownBy(() -> service.joinRoom(userId, roomId))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.ROOM_EXPIRED));

        verify(roomRepo).findById(roomId);
        verifyNoInteractions(memberRepo);
    }

    private void setField(Object target, String fieldName, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
