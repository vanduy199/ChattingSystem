package com.ChattingSystem.message.service.impl;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;
import com.ChattingSystem.message.entity.Message;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.message.service.MessageService;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MessageServiceImpl implements MessageService {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final MessageRepository messageRepo;
    private final RoomMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final RoomAccessService roomAccess;
    private final ApplicationEventPublisher events;

    public MessageServiceImpl(MessageRepository messageRepo,
                              RoomMemberRepository memberRepo,
                              UserRepository userRepo,
                              RoomAccessService roomAccess,
                              ApplicationEventPublisher events) {
        this.messageRepo = messageRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.roomAccess = roomAccess;
        this.events = events;
    }

    @Override
    @Transactional
    public MessageResponse send(Long userId, Long roomId, SendMessageRequest req) {
        roomAccess.requireActiveMember(userId, roomId);

        ChatRoom room = roomAccess.requireActiveRoom(roomId);
        if (room != null && "private".equalsIgnoreCase(room.getRoomType())) {
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (user.isGuest()) {
                throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CHAT);
            }
        }

        String content = req.content() == null ? "" : req.content().strip();
        if (content.isEmpty()) {
            throw AppException.fieldError("content", "Tin nhắn không được để trống");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw AppException.fieldError("content", "Tin nhắn tối đa 2000 ký tự");
        }

        // Client gửi lại khi mạng chập chờn: trả tin cũ, không phát lại sự kiện
        Optional<Message> existing = messageRepo.findBySenderIdAndClientMessageId(userId, req.clientMessageId());
        if (existing.isPresent()) {
            Message message = existing.get();
            if (!message.getRoomId().equals(roomId)) {
                throw AppException.fieldError("clientMessageId", "Mã tin nhắn đã được dùng ở phòng khác");
            }
            return toResponse(message.getId());
        }

        Message saved = messageRepo.saveAndFlush(Message.text(roomId, userId, req.clientMessageId(), content));
        MessageResponse response = toResponse(saved.getId());

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

        Long nextCursor = items.size() == size ? items.getLast().id() : null;
        return new MessageSliceResponse(items, nextCursor);
    }

    private MessageResponse toResponse(Long messageId) {
        return messageRepo.findResponseById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR));
    }
}
