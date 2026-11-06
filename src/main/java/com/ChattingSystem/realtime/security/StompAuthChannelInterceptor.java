package com.ChattingSystem.realtime.security;

import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.realtime.StompPrincipals;
import com.ChattingSystem.realtime.StompErrors;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Xác thực STOMP: CONNECT phải mang {@code Authorization: Bearer <JWT>} (cùng JWT như REST),
 * SUBSCRIBE {@code /topic/rooms.{id}} chỉ dành cho thành viên phòng đang hoạt động.
 * SUBSCRIBE bị từ chối thì bỏ frame và báo về {@code /user/queue/errors} thay vì gửi ERROR,
 * vì ERROR đóng luôn cả kết nối.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms.";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final RoomAccessService roomAccess;
    private final SimpMessagingTemplate messagingTemplate;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                       JwtAuthenticationConverter jwtAuthenticationConverter,
                                       RoomAccessService roomAccess,
                                       @Lazy SimpMessagingTemplate messagingTemplate) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.roomAccess = roomAccess;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        return switch (accessor.getCommand()) {
            case CONNECT -> {
                accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                yield message;
            }
            case SUBSCRIBE -> authorizeSubscribe(accessor, message);
            case SEND -> {
                requireUser(accessor.getUser());
                yield message;
            }
            default -> message;
        };
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BadCredentialsException("Thiếu token xác thực");
        }
        try {
            return jwtAuthenticationConverter.convert(jwtDecoder.decode(header.substring(7)));
        } catch (JwtException e) {
            throw new BadCredentialsException("Token không hợp lệ hoặc đã hết hạn", e);
        }
    }

    private Message<?> authorizeSubscribe(StompHeaderAccessor accessor, Message<?> message) {
        Principal user = requireUser(accessor.getUser());
        String destination = accessor.getDestination();
        if (destination == null) {
            return deny(user, destination, ErrorCode.ACCESS_DENIED);
        }
        if (destination.startsWith(USER_QUEUE_PREFIX)) {
            return message;
        }
        if (!destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return deny(user, destination, ErrorCode.ACCESS_DENIED);
        }

        Long roomId;
        try {
            roomId = Long.valueOf(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return deny(user, destination, ErrorCode.ROOM_NOT_FOUND);
        }

        try {
            roomAccess.requireActiveMember(StompPrincipals.userIdOf(user), roomId);
            return message;
        } catch (AppException e) {
            return deny(user, destination, e.getErrorCode());
        }
    }

    private Message<?> deny(Principal user, String destination, ErrorCode code) {
        messagingTemplate.convertAndSendToUser(user.getName(), "/queue/errors", StompErrors.of(code, destination));
        return null;
    }

    private static Principal requireUser(Principal user) {
        if (user == null) {
            throw new BadCredentialsException("Chưa xác thực");
        }
        return user;
    }
}
