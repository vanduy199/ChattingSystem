package com.ChattingSystem.message;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.entity.Message;
import com.ChattingSystem.message.entity.MessageType;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.message.service.impl.MessageServiceImpl;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceUnitTest {

    @Mock
    private MessageRepository messageRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private RoomAccessService roomAccess;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private MessageServiceImpl messageService;

    private User realGuestUser() {
        return User.createGuest("Khách #9999");
    }

    private User realNormalUser() {
        return User.create("uid-1", "user@example.com", "User Thật", null, null);
    }

    @Test
    void send_guestInPrivateRoom_shouldThrowLoginRequiredToChat() {
        Long userId = 1L;
        Long roomId = 10L;
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Xin chào");

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("private");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realGuestUser()));

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_REQUIRED_TO_CHAT));

        verify(messageRepo, never()).saveAndFlush(any());
    }

    @Test
    void send_guestInPublicRoom_shouldSendSuccessfully() {
        Long userId = 1L;
        Long roomId = 10L;
        UUID clientMsgId = UUID.randomUUID();
        SendMessageRequest req = new SendMessageRequest(clientMsgId, "Xin chào phòng public");

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(messageRepo.findBySenderIdAndClientMessageId(userId, clientMsgId)).thenReturn(Optional.empty());

        Message mockSaved = mock(Message.class);
        when(mockSaved.getId()).thenReturn(100L);
        when(messageRepo.saveAndFlush(any())).thenReturn(mockSaved);

        MessageResponse mockResponse = new MessageResponse(
                100L, roomId, clientMsgId, MessageType.TEXT, "Xin chào phòng public",
                userId, "Khách #9999", null, Instant.now()
        );
        when(messageRepo.findResponseById(100L)).thenReturn(Optional.of(mockResponse));

        MessageResponse result = messageService.send(userId, roomId, req);
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.content()).isEqualTo("Xin chào phòng public");
        verify(events).publishEvent(any(com.ChattingSystem.realtime.event.RoomEvent.class));
    }
}
