package com.ChattingSystem.message;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;
import com.ChattingSystem.message.entity.Message;
import com.ChattingSystem.message.entity.MessageType;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.message.service.impl.MessageServiceImpl;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaLimitsProperties;
import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private MediaLimitsProperties limitsProperties;

    @InjectMocks
    private MessageServiceImpl messageService;

    private User realGuestUser() {
        return User.createGuest("Khách #9999");
    }

    private User realNormalUser() {
        return User.create("uid-1", "user@example.com", "User Thật", null, null);
    }

    // ==========================================
    // Kịch bản 1: Tin nhắn text thuần túy
    // ==========================================

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

    // ==========================================
    // Kịch bản 2: Tin nhắn kèm danh sách ảnh/video hợp lệ
    // ==========================================

    @Test
    void send_validMediaAttachments_shouldSaveMediaMessageAndAttach() {
        Long userId = 1L;
        Long roomId = 10L;
        UUID clientMsgId = UUID.randomUUID();
        List<Long> attachmentIds = List.of(301L, 302L);
        SendMessageRequest req = new SendMessageRequest(clientMsgId, "Ảnh hiện trường", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(messageRepo.findBySenderIdAndClientMessageId(userId, clientMsgId)).thenReturn(Optional.empty());

        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));
        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(Role.class), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset1 = mock(MediaAsset.class);
        when(asset1.getId()).thenReturn(301L);
        when(asset1.getUploaderId()).thenReturn(userId);
        when(asset1.getRoomId()).thenReturn(roomId);
        when(asset1.getMessageId()).thenReturn(null);
        when(asset1.getStatus()).thenReturn(MediaStatus.AVAILABLE);
        when(asset1.getKind()).thenReturn(MediaKind.IMAGE);
        when(asset1.getWidth()).thenReturn(1920);
        when(asset1.getHeight()).thenReturn(1080);

        MediaAsset asset2 = mock(MediaAsset.class);
        when(asset2.getId()).thenReturn(302L);
        when(asset2.getUploaderId()).thenReturn(userId);
        when(asset2.getRoomId()).thenReturn(roomId);
        when(asset2.getMessageId()).thenReturn(null);
        when(asset2.getStatus()).thenReturn(MediaStatus.PROCESSING);
        when(asset2.getKind()).thenReturn(MediaKind.VIDEO);
        when(asset2.getDurationMs()).thenReturn(15000);

        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset1, asset2));
        when(mediaAssetRepository.attachToMessageIfUnclaimed(eq(301L), eq(200L), eq(0), eq(userId), eq(roomId), any()))
                .thenReturn(1);
        when(mediaAssetRepository.attachToMessageIfUnclaimed(eq(302L), eq(200L), eq(1), eq(userId), eq(roomId), any()))
                .thenReturn(1);

        Message mockSaved = mock(Message.class);
        when(mockSaved.getId()).thenReturn(200L);
        when(messageRepo.saveAndFlush(any(Message.class))).thenReturn(mockSaved);

        MessageResponse baseResponse = new MessageResponse(
                200L, roomId, clientMsgId, MessageType.MEDIA, "Ảnh hiện trường",
                userId, "User Thật", null, Instant.now()
        );
        when(messageRepo.findResponseById(200L)).thenReturn(Optional.of(baseResponse));

        MessageResponse result = messageService.send(userId, roomId, req);

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.type()).isEqualTo(MessageType.MEDIA);
        assertThat(result.attachments()).hasSize(2);
        assertThat(result.attachments().get(0).mediaAssetId()).isEqualTo(301L);
        assertThat(result.attachments().get(0).sortOrder()).isEqualTo(0);
        assertThat(result.attachments().get(0).kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(result.attachments().get(1).mediaAssetId()).isEqualTo(302L);
        assertThat(result.attachments().get(1).sortOrder()).isEqualTo(1);
        assertThat(result.attachments().get(1).kind()).isEqualTo(MediaKind.VIDEO);

        verify(mediaAssetRepository).attachToMessageIfUnclaimed(eq(301L), eq(200L), eq(0), eq(userId), eq(roomId), any());
        verify(mediaAssetRepository).attachToMessageIfUnclaimed(eq(302L), eq(200L), eq(1), eq(userId), eq(roomId), any());
        verify(events).publishEvent(any(com.ChattingSystem.realtime.event.RoomEvent.class));
    }

    // ==========================================
    // Kịch bản 3: Tin nhắn chỉ có ảnh/video (content rỗng hoặc null)
    // ==========================================

    @Test
    void send_mediaOnlyWithoutTextContent_shouldSendSuccessfully() {
        Long userId = 1L;
        Long roomId = 10L;
        UUID clientMsgId = UUID.randomUUID();
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(clientMsgId, null, attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(messageRepo.findBySenderIdAndClientMessageId(userId, clientMsgId)).thenReturn(Optional.empty());

        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));
        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(Role.class), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(userId);
        when(asset.getRoomId()).thenReturn(roomId);
        when(asset.getMessageId()).thenReturn(null);
        when(asset.getStatus()).thenReturn(MediaStatus.AVAILABLE);

        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));
        when(mediaAssetRepository.attachToMessageIfUnclaimed(eq(301L), eq(201L), eq(0), eq(userId), eq(roomId), any()))
                .thenReturn(1);

        Message mockSaved = mock(Message.class);
        when(mockSaved.getId()).thenReturn(201L);
        when(messageRepo.saveAndFlush(any(Message.class))).thenReturn(mockSaved);

        MessageResponse baseResponse = new MessageResponse(
                201L, roomId, clientMsgId, MessageType.MEDIA, "",
                userId, "User Thật", null, Instant.now()
        );
        when(messageRepo.findResponseById(201L)).thenReturn(Optional.of(baseResponse));

        MessageResponse result = messageService.send(userId, roomId, req);
        assertThat(result.id()).isEqualTo(201L);
        assertThat(result.content()).isEmpty();
        assertThat(result.attachments()).hasSize(1);
    }

    // ==========================================
    // Kịch bản 4: Tin nhắn không chữ, không tệp -> ném lỗi
    // ==========================================

    @Test
    void send_emptyContentWithoutAttachments_shouldThrowFieldError() {
        Long userId = 1L;
        Long roomId = 10L;
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "   ", null);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getErrors()).hasSize(1);
                    assertThat(ex.getErrors().getFirst().getField()).isEqualTo("content");
                    assertThat(ex.getErrors().getFirst().getMessage()).contains("Tin nhắn không được để trống");
                });

        verify(messageRepo, never()).saveAndFlush(any());
    }

    // ==========================================
    // Kịch bản 5: Vượt quá số lượng tệp tối đa
    // ==========================================

    @Test
    void send_exceedMaxFilesPerMessage_shouldThrowTooManyAttachments() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L, 302L, 303L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "3 ảnh", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);

        when(userRepo.findById(userId)).thenReturn(Optional.of(realGuestUser()));
        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(Role.class), any())).thenReturn(2); // Guest tối đa 2 tệp
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_ATTACHMENTS));

        verify(mediaAssetRepository, never()).findAllById(any());
        verify(messageRepo, never()).saveAndFlush(any());
    }

    // ==========================================
    // Kịch bản 6: Trùng ID hoặc chứa null trong attachmentIds
    // ==========================================

    @Test
    void send_duplicateAttachmentIds_shouldThrowValidationError() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> duplicateIds = List.of(301L, 301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Trùng ID", duplicateIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).contains("không được chứa ID trùng lặp");
                });

        verify(messageRepo, never()).saveAndFlush(any());
    }

    @Test
    void send_nullInAttachmentIds_shouldThrowValidationError() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> idsWithNull = new ArrayList<>();
        idsWithNull.add(301L);
        idsWithNull.add(null);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Chứa null", idsWithNull);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).contains("không được chứa giá trị trống");
                });

        verify(messageRepo, never()).saveAndFlush(any());
    }

    // ==========================================
    // Kịch bản 7: Chống IDOR
    // ==========================================

    @Test
    void send_attachmentNotFound_shouldThrowMediaNotFound() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(999L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Không tồn tại", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of()); // Không tìm thấy

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    void send_idorAttachmentNotOwner_shouldThrowMediaNotFound() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Tệp người khác", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(999L); // Người khác upload
        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    void send_idorAttachmentDifferentRoom_shouldThrowMediaNotFound() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Tệp phòng khác", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(userId);
        when(asset.getRoomId()).thenReturn(999L); // Phòng khác
        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    void send_idorAttachmentAlreadyAttached_shouldThrowMediaNotFound() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Tệp đã gắn tin nhắn", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(userId);
        when(asset.getRoomId()).thenReturn(roomId);
        when(asset.getMessageId()).thenReturn(888L); // Đã gắn vào tin nhắn 888L
        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    // ==========================================
    // Kịch bản 8: Tranh chấp đồng thời (attachToMessageIfUnclaimed trả về 0)
    // ==========================================

    @Test
    void send_concurrentClaimConflict_shouldThrowMediaNotFound() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Đồng thời", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(userId);
        when(asset.getRoomId()).thenReturn(roomId);
        when(asset.getMessageId()).thenReturn(null);
        when(asset.getStatus()).thenReturn(MediaStatus.AVAILABLE);
        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));

        Message mockSaved = mock(Message.class);
        when(mockSaved.getId()).thenReturn(202L);
        when(messageRepo.saveAndFlush(any())).thenReturn(mockSaved);

        // Bị request khác claim mất đúng lúc UPDATE
        when(mediaAssetRepository.attachToMessageIfUnclaimed(eq(301L), eq(202L), eq(0), eq(userId), eq(roomId), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Tệp đính kèm không còn khả dụng");
                });
    }

    // ==========================================
    // Kịch bản 9: Tệp đang tải dở (UPLOADING / CREATING)
    // ==========================================

    @Test
    void send_attachmentStillUploading_shouldThrowMediaNotReady() {
        Long userId = 1L;
        Long roomId = 10L;
        List<Long> attachmentIds = List.of(301L);
        SendMessageRequest req = new SendMessageRequest(UUID.randomUUID(), "Đang upload", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);
        when(userRepo.findById(userId)).thenReturn(Optional.of(realNormalUser()));

        MediaLimitsProperties.RoleLimitsInt roleLimits = mock(MediaLimitsProperties.RoleLimitsInt.class);
        when(roleLimits.getLimit(any(), any())).thenReturn(5);
        when(limitsProperties.maxFilesPerMessage()).thenReturn(roleLimits);

        MediaAsset asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(301L);
        when(asset.getUploaderId()).thenReturn(userId);
        when(asset.getRoomId()).thenReturn(roomId);
        when(asset.getMessageId()).thenReturn(null);
        when(asset.getStatus()).thenReturn(MediaStatus.UPLOADING); // Đang upload dở
        when(mediaAssetRepository.findAllById(attachmentIds)).thenReturn(List.of(asset));

        assertThatThrownBy(() -> messageService.send(userId, roomId, req))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_READY));
    }

    // ==========================================
    // Kịch bản 10: Xem lịch sử (history)
    // ==========================================

    @Test
    void history_shouldFetchAttachmentsInBatchAndMapCorrectly() {
        Long userId = 1L;
        Long roomId = 10L;

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        MessageResponse msgMedia = new MessageResponse(
                101L, roomId, UUID.randomUUID(), MessageType.MEDIA, "Tin có ảnh",
                userId, "User 1", null, Instant.now()
        );
        MessageResponse msgText = new MessageResponse(
                102L, roomId, UUID.randomUUID(), MessageType.TEXT, "Tin text",
                userId, "User 1", null, Instant.now()
        );

        when(messageRepo.findSlice(eq(roomId), any(), any(PageRequest.class)))
                .thenReturn(List.of(msgMedia, msgText));

        MediaAsset asset1 = mock(MediaAsset.class);
        when(asset1.getId()).thenReturn(401L);
        when(asset1.getMessageId()).thenReturn(101L);
        when(asset1.getSortOrder()).thenReturn(0);
        when(asset1.getKind()).thenReturn(MediaKind.IMAGE);
        when(asset1.getStatus()).thenReturn(MediaStatus.AVAILABLE);

        MediaAsset asset2 = mock(MediaAsset.class);
        when(asset2.getId()).thenReturn(402L);
        when(asset2.getMessageId()).thenReturn(101L);
        when(asset2.getSortOrder()).thenReturn(1);
        when(asset2.getKind()).thenReturn(MediaKind.VIDEO);
        when(asset2.getStatus()).thenReturn(MediaStatus.PROCESSING);

        when(mediaAssetRepository.findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(101L)))
                .thenReturn(List.of(asset1, asset2));

        MessageSliceResponse slice = messageService.history(userId, roomId, null, 30);

        assertThat(slice.items()).hasSize(2);
        MessageResponse resultMedia = slice.items().get(0);
        assertThat(resultMedia.id()).isEqualTo(101L);
        assertThat(resultMedia.attachments()).hasSize(2);
        assertThat(resultMedia.attachments().get(0).mediaAssetId()).isEqualTo(401L);
        assertThat(resultMedia.attachments().get(1).mediaAssetId()).isEqualTo(402L);

        MessageResponse resultText = slice.items().get(1);
        assertThat(resultText.id()).isEqualTo(102L);
        assertThat(resultText.attachments()).isEmpty();

        // Khẳng định đúng 1 query batch cho attachments, không có N+1
        verify(mediaAssetRepository, times(1)).findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(101L));
    }

    @Test
    void history_notMember_shouldThrowNotRoomMember() {
        Long userId = 1L;
        Long roomId = 10L;

        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        assertThatThrownBy(() -> messageService.history(userId, roomId, null, 30))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));

        verify(messageRepo, never()).findSlice(any(), any(), any());
    }

    // ==========================================
    // Kịch bản 11: Idempotency (Gửi lại tin nhắn MEDIA cũ)
    // ==========================================

    @Test
    void send_retryMediaMessageWithSameClientId_shouldReturnExistingWithAttachmentsWithoutSavingOrPublishing() {
        Long userId = 1L;
        Long roomId = 10L;
        UUID clientMsgId = UUID.randomUUID();
        List<Long> attachmentIds = List.of(301L, 302L);
        SendMessageRequest req = new SendMessageRequest(clientMsgId, "Ảnh hiện trường", attachmentIds);

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getRoomType()).thenReturn("public");
        when(roomAccess.requireActiveRoom(roomId)).thenReturn(mockRoom);

        // Tin nhắn cũ đã tồn tại trong DB (sau khi client lần trước đã lưu thành công)
        Message existingMessage = mock(Message.class);
        when(existingMessage.getId()).thenReturn(500L);
        when(existingMessage.getRoomId()).thenReturn(roomId);
        when(messageRepo.findBySenderIdAndClientMessageId(userId, clientMsgId)).thenReturn(Optional.of(existingMessage));

        MessageResponse existingBaseResponse = new MessageResponse(
                500L, roomId, clientMsgId, MessageType.MEDIA, "Ảnh hiện trường",
                userId, "User Thật", null, Instant.now()
        );
        when(messageRepo.findResponseById(500L)).thenReturn(Optional.of(existingBaseResponse));

        // Hai asset đã gắn messageId = 500L ở lần gọi trước
        MediaAsset asset1 = mock(MediaAsset.class);
        when(asset1.getId()).thenReturn(301L);
        when(asset1.getSortOrder()).thenReturn(0);
        when(asset1.getKind()).thenReturn(MediaKind.IMAGE);
        when(asset1.getStatus()).thenReturn(MediaStatus.AVAILABLE);

        MediaAsset asset2 = mock(MediaAsset.class);
        when(asset2.getId()).thenReturn(302L);
        when(asset2.getSortOrder()).thenReturn(1);
        when(asset2.getKind()).thenReturn(MediaKind.IMAGE);
        when(asset2.getStatus()).thenReturn(MediaStatus.AVAILABLE);

        when(mediaAssetRepository.findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(500L)))
                .thenReturn(List.of(asset1, asset2));

        MessageResponse result = messageService.send(userId, roomId, req);

        assertThat(result.id()).isEqualTo(500L);
        assertThat(result.type()).isEqualTo(MessageType.MEDIA);
        assertThat(result.attachments()).hasSize(2);
        assertThat(result.attachments().get(0).mediaAssetId()).isEqualTo(301L);
        assertThat(result.attachments().get(1).mediaAssetId()).isEqualTo(302L);

        // Khẳng định idempotency tuyệt đối: không lưu mới, không gắn lại, không gọi findAllById kiểm tra, không bắn lại event
        verify(messageRepo, never()).saveAndFlush(any());
        verify(mediaAssetRepository, never()).attachToMessageIfUnclaimed(any(), any(), anyInt(), any(), any(), any());
        verify(mediaAssetRepository, never()).findAllById(any());
        verify(events, never()).publishEvent(any());
    }
}
