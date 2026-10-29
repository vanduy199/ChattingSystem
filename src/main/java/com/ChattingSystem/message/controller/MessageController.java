package com.ChattingSystem.message.controller;

import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;
import com.ChattingSystem.message.service.MessageService;
import com.ChattingSystem.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lịch sử tin nhắn + gửi tin qua REST (dự phòng khi không có WebSocket). */
@RestController
@RequestMapping("/rooms/{roomId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MessageSliceResponse>> history(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {

        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        MessageSliceResponse slice = messageService.history(userId, roomId, before, Math.clamp(size, 1, 100));
        return ResponseEntity.ok(ApiResponse.success("Lấy tin nhắn thành công!", slice));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendMessageRequest req) {

        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        MessageResponse message = messageService.send(userId, roomId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi tin nhắn thành công!", message));
    }
}
