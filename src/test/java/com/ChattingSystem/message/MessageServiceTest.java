package com.ChattingSystem.message;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;
import com.ChattingSystem.message.entity.MessageType;
import com.ChattingSystem.message.service.MessageService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy trên DB trong env (dùng Postgres + PostGIS local, không phải DB hosted). Mỗi test tự rollback. */
@SpringBootTest
@Transactional
class MessageServiceTest {

    @Autowired private MessageService messageService;
    @Autowired private ChatRoomService chatRoomService;
    @Autowired private UserRepository userRepo;
    @Autowired private EntityManager em;

    private User owner;
    private User member;
    private User stranger;
    private Long roomId;

    private User newUser(String name) {
        String id = UUID.randomUUID().toString();
        return userRepo.saveAndFlush(User.create("test-uid-" + id, "test-" + id + "@example.com", name, null, null));
    }

    @BeforeEach
    void setUp() {
        owner = newUser("Chu phong");
        member = newUser("Thanh vien");
        stranger = newUser("Nguoi la");
        roomId = chatRoomService.createRoom(owner.getId(),
                new CreateRoomRequest("Phong nhan tin", null, 21.0285, 105.8542, 60, 1000)).id();
        chatRoomService.joinRoom(member.getId(), roomId, new LocationRequest(21.0285, 105.8542));
    }

    @Test
    void send_savesTrimmedTextWithSender() {
        MessageResponse res = messageService.send(member.getId(), roomId,
                new SendMessageRequest(UUID.randomUUID(), "  Xin chào  "));

        assertThat(res.id()).isNotNull();
        assertThat(res.content()).isEqualTo("Xin chào");
        assertThat(res.type()).isEqualTo(MessageType.TEXT);
        assertThat(res.sender().id()).isEqualTo(member.getId());
        assertThat(res.sender().fullName()).isEqualTo("Thanh vien");
        assertThat(res.createdAt()).isNotNull();
    }

    @Test
    void send_sameClientMessageId_returnsExistingMessage() {
        UUID clientId = UUID.randomUUID();
        MessageResponse first = messageService.send(member.getId(), roomId, new SendMessageRequest(clientId, "Một"));
        MessageResponse retry = messageService.send(member.getId(), roomId, new SendMessageRequest(clientId, "Một"));

        assertThat(retry.id()).isEqualTo(first.id());
        Long count = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM messages WHERE room_id = :r")
                .setParameter("r", roomId).getSingleResult()).longValue();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void send_byNonMember_isRejected() {
        assertThatThrownBy(() -> messageService.send(stranger.getId(), roomId,
                new SendMessageRequest(UUID.randomUUID(), "Chen ngang")))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
    }

    @Test
    void send_toExpiredRoom_isRejected() {
        em.createNativeQuery("UPDATE chat_rooms SET expires_at = now() - interval '1 minute' WHERE id = :id")
                .setParameter("id", roomId).executeUpdate();
        em.clear();

        assertThatThrownBy(() -> messageService.send(member.getId(), roomId,
                new SendMessageRequest(UUID.randomUUID(), "Tre roi")))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_EXPIRED));
    }

    @Test
    void send_blankAfterTrim_isValidationError() {
        assertThatThrownBy(() -> messageService.send(member.getId(), roomId,
                new SendMessageRequest(UUID.randomUUID(), "   ")))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void history_pagesNewestFirstWithCursor() {
        for (int i = 1; i <= 5; i++) {
            messageService.send(i % 2 == 0 ? owner.getId() : member.getId(), roomId,
                    new SendMessageRequest(UUID.randomUUID(), "Tin " + i));
        }

        MessageSliceResponse page1 = messageService.history(member.getId(), roomId, null, 2);
        assertThat(page1.items()).extracting(MessageResponse::content).containsExactly("Tin 5", "Tin 4");
        assertThat(page1.nextCursor()).isEqualTo(page1.items().getLast().id());

        MessageSliceResponse page2 = messageService.history(member.getId(), roomId, page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(MessageResponse::content).containsExactly("Tin 3", "Tin 2");

        MessageSliceResponse page3 = messageService.history(member.getId(), roomId, page2.nextCursor(), 2);
        assertThat(page3.items()).extracting(MessageResponse::content).containsExactly("Tin 1");
        assertThat(page3.nextCursor()).isNull();
    }

    @Test
    void history_byNonMember_isRejected() {
        assertThatThrownBy(() -> messageService.history(stranger.getId(), roomId, null, 30))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
    }
}
