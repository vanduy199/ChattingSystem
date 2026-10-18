package com.ChattingSystem.chatroom.controller;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.chatroom.service.RoomMemberService;

import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rooms")   // URL thật: POST /api/v1/rooms (context-path = /api/v1)
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService service;
    private final RoomMemberService roomMemberService;
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

    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(@PathVariable Long roomId,@AuthenticationPrincipal Jwt jwt){
        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        JoinRoomResponse room = service.joinRoom(userId, roomId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Tham gia phòng chat thành công!", room));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<MyRoomResponse>>> listRooms(@AuthenticationPrincipal Jwt jwt,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size){
        Long userId = ((Number) jwt.getClaim("userId")).longValue();


        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100)
        );

        Page<MyRoomResponse> rooms =
                roomMemberService.getMyActiveRooms(userId, pageable);

        PageResponse<MyRoomResponse> response = PageResponse.of(rooms);

        return ResponseEntity.ok(
                ApiResponse.success("Lấy danh sách phòng chat thành công!", response)
        );
    }

    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = ((Number) jwt.getClaim("userId")).longValue();

        roomMemberService.leaveRoom(userId, roomId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Rời khỏi phòng chat thành công!",
                        null
                )
        );
    }
}