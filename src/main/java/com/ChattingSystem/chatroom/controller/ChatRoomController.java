package com.ChattingSystem.chatroom.controller;

import com.ChattingSystem.chatroom.dto.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.RoomResponse;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")   // URL thật: POST /api/v1/rooms (context-path = /api/v1)
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService service;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoomRequest req) {

        // claim "userId" do JwtService.generateAccessToken đặt vào token
        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        RoomResponse room = service.createRoom(userId, req);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phòng thành công!", room));
    }
}