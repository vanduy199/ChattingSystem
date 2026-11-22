package com.ChattingSystem.message.service.impl;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.AttachmentResponse;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;
import com.ChattingSystem.message.entity.Message;
import com.ChattingSystem.message.entity.MessageType;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.message.service.MessageService;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaLimitsProperties;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final MessageRepository messageRepo;
    private final RoomMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final RoomAccessService roomAccess;
    private final ApplicationEventPublisher events;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaLimitsProperties limitsProperties;

    public MessageServiceImpl(MessageRepository messageRepo,
                              RoomMemberRepository memberRepo,
                              UserRepository userRepo,
                              RoomAccessService roomAccess,
                              ApplicationEventPublisher events,
                              MediaAssetRepository mediaAssetRepository,
                              MediaLimitsProperties limitsProperties) {
        this.messageRepo = messageRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.roomAccess = roomAccess;
        this.events = events;
        this.mediaAssetRepository = mediaAssetRepository;
        this.limitsProperties = limitsProperties;
    }

    @Override
    @Transactional
    public MessageResponse send(Long userId, Long roomId, SendMessageRequest req) {
        roomAccess.requireActiveMember(userId, roomId);

        ChatRoom room = roomAccess.requireActiveRoom(roomId);
        User user = null;
        if (room != null && "private".equalsIgnoreCase(room.getRoomType())) {
            user = userRepo.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (user.isGuest()) {
                throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CHAT);
            }
        }

        String content = (req.content() == null) ? "" : req.content().strip();
        boolean hasAttachments = req.attachmentIds() != null && !req.attachmentIds().isEmpty();

        if (content.isEmpty() && !hasAttachments) {
            throw AppException.fieldError("content", "Tin nhắn không được để trống");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw AppException.fieldError("content", "Tin nhắn tối đa 2000 ký tự");
        }

        // Client gửi lại khi mạng chập chờn: trả tin cũ kèm attachments hiện có, không phát lại sự kiện.
        // Đặt TRƯỚC khối kiểm tra hasAttachments để khi retry tin MEDIA (asset đã có message_id != null)
        // không bị chặn bởi điều kiện messageId == null.
        Optional<Message> existing = messageRepo.findBySenderIdAndClientMessageId(userId, req.clientMessageId());
        if (existing.isPresent()) {
            Message message = existing.get();
            if (!message.getRoomId().equals(roomId)) {
                throw AppException.fieldError("clientMessageId", "Mã tin nhắn đã được dùng ở phòng khác");
            }
            return toResponse(message.getId());
        }

        Map<Long, MediaAsset> assetMap = null;
        if (hasAttachments) {
            if (req.attachmentIds().contains(null)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Danh sách tệp đính kèm không được chứa giá trị trống");
            }
            Set<Long> uniqueIds = new HashSet<>(req.attachmentIds());
            if (uniqueIds.size() != req.attachmentIds().size()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Danh sách tệp đính kèm không được chứa ID trùng lặp");
            }

            if (user == null) {
                user = userRepo.findById(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            }
            String roomType = (room != null) ? room.getRoomType() : "public";
            int maxFiles = limitsProperties.maxFilesPerMessage().getLimit(user.getRole(), roomType);
            if (req.attachmentIds().size() > maxFiles) {
                throw new AppException(ErrorCode.TOO_MANY_ATTACHMENTS, "Vượt quá số lượng tệp đính kèm cho phép (" + maxFiles + " tệp)");
            }

            List<MediaAsset> assets = mediaAssetRepository.findAllById(req.attachmentIds());
            if (assets.size() != req.attachmentIds().size()) {
                throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
            }
            assetMap = assets.stream().collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
            for (Long assetId : req.attachmentIds()) {
                MediaAsset asset = assetMap.get(assetId);
                if (asset == null || !asset.getUploaderId().equals(userId) || !asset.getRoomId().equals(roomId) || asset.getMessageId() != null) {
                    throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
                }
                if (asset.getStatus() != MediaStatus.PROCESSING && asset.getStatus() != MediaStatus.AVAILABLE) {
                    throw new AppException(ErrorCode.MEDIA_NOT_READY);
                }
            }
        }

        Message toSave = hasAttachments
                ? Message.media(roomId, userId, req.clientMessageId(), content)
                : Message.text(roomId, userId, req.clientMessageId(), content);
        Message saved = messageRepo.saveAndFlush(toSave);

        List<AttachmentResponse> attachmentResponses = new ArrayList<>();
        if (hasAttachments) {
            Instant now = Instant.now();
            for (int i = 0; i < req.attachmentIds().size(); i++) {
                Long assetId = req.attachmentIds().get(i);
                int updated = mediaAssetRepository.attachToMessageIfUnclaimed(assetId, saved.getId(), i, userId, roomId, now);
                if (updated != 1) {
                    throw new AppException(ErrorCode.MEDIA_NOT_FOUND, "Tệp đính kèm không còn khả dụng");
                }
                MediaAsset asset = assetMap.get(assetId);
                attachmentResponses.add(new AttachmentResponse(
                        asset.getId(),
                        i,
                        asset.getKind(),
                        asset.getStatus(),
                        asset.getWidth(),
                        asset.getHeight(),
                        asset.getDurationMs()
                ));
            }
        }

        MessageResponse baseResponse = messageRepo.findResponseById(saved.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR));
        MessageResponse response = hasAttachments
                ? baseResponse.withAttachments(attachmentResponses)
                : baseResponse;

        events.publishEvent(new RoomEvent(roomId, RoomEventType.MESSAGE, response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageSliceResponse history(Long userId, Long roomId, Long before, int size) {
        // Phòng hết hạn vẫn đọc được lịch sử, chỉ cần là thành viên
        if (!memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AppException(ErrorCode.NOT_ROOM_MEMBER);
        }

        List<MessageResponse> items = messageRepo.findSlice(
                roomId,
                before != null ? before : Long.MAX_VALUE,
                PageRequest.ofSize(size));

        List<Long> mediaMsgIds = items.stream()
                .filter(m -> m.type() == MessageType.MEDIA)
                .map(MessageResponse::id)
                .toList();

        if (!mediaMsgIds.isEmpty()) {
            List<MediaAsset> assets = mediaAssetRepository.findByMessageIdInOrderByMessageIdAscSortOrderAsc(mediaMsgIds);
            Map<Long, List<AttachmentResponse>> attachmentsByMsgId = assets.stream()
                    .collect(Collectors.groupingBy(
                            MediaAsset::getMessageId,
                            Collectors.mapping(AttachmentResponse::from, Collectors.toList())
                    ));

            items = items.stream()
                    .map(m -> m.type() == MessageType.MEDIA
                            ? m.withAttachments(attachmentsByMsgId.getOrDefault(m.id(), List.of()))
                            : m)
                    .toList();
        }

        Long nextCursor = items.size() == size ? items.getLast().id() : null;
        return new MessageSliceResponse(items, nextCursor);
    }

    private MessageResponse toResponse(Long messageId) {
        MessageResponse base = messageRepo.findResponseById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR));
        if (base.type() == MessageType.MEDIA) {
            List<MediaAsset> assets = mediaAssetRepository.findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(messageId));
            List<AttachmentResponse> attachments = assets.stream().map(AttachmentResponse::from).toList();
            return base.withAttachments(attachments);
        }
        return base;
    }
}
