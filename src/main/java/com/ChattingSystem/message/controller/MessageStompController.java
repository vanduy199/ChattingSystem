package com.ChattingSystem.message.controller;

import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.service.MessageService;
import com.ChattingSystem.realtime.StompPrincipals;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Client gửi tới {@code /app/rooms.{roomId}.send}. Tin đã lưu được phát lên
 * {@code /topic/rooms.{roomId}} (kèm {@code clientMessageId} để client đối chiếu);
 * lỗi đi về {@code /user/queue/errors} qua {@code StompErrorHandler}.
 */
@Controller
public class MessageStompController {

    private final MessageService messageService;

    public MessageStompController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("rooms.{roomId}.send")
    public void send(@DestinationVariable Long roomId,
                     @Valid @Payload SendMessageRequest req,
                     Principal principal) {
        messageService.send(StompPrincipals.userIdOf(principal), roomId, req);
    }
}
