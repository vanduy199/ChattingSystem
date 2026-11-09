package com.ChattingSystem.chatroom.controller;

import com.ChattingSystem.chatroom.dto.request.AcceptJoinRequest;
import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.dto.request.UpdateRoomRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.NearbyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomDetailResponse;
import com.ChattingSystem.chatroom.dto.response.RoomMemberResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.chatroom.service.RoomMemberService;

import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.shared.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms") // URL thật: POST /api/v1/rooms (context-path = /api/v1)
public class ChatRoomController {

        private final ChatRoomService service;
        private final RoomMemberService roomMemberService;

        public ChatRoomController(ChatRoomService service, RoomMemberService roomMemberService) {
                this.service = service;
                this.roomMemberService = roomMemberService;
        }

        // claim "userId" do JwtService.generateAccessToken đặt vào token
        private static Long userIdOf(Jwt jwt) {
                return ((Number) jwt.getClaim("userId")).longValue();
        }

        @PostMapping
        public ResponseEntity<ApiResponse<RoomResponse>> create(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody CreateRoomRequest req) {

                RoomResponse room = service.createRoom(userIdOf(jwt), req);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Tạo phòng thành công!", room));
        }

        @PutMapping
        public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody UpdateRoomRequest req) {

                RoomResponse entity = service.updateRoom(userIdOf(jwt), req);
                return ResponseEntity.status(HttpStatus.OK)
                                .body(ApiResponse.success("Cập nhật phòng thành công!", entity));
        }

        @GetMapping("/nearby")
        public ResponseEntity<ApiResponse<List<NearbyRoomResponse>>> nearby(
                        @AuthenticationPrincipal Jwt jwt,
                        @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                        @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
                        @RequestParam(defaultValue = "1000") @Min(100) @Max(3000) int radius) {

                List<NearbyRoomResponse> rooms = service.findNearby(userIdOf(jwt), lat, lng, radius);
                return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phòng gần bạn thành công!", rooms));
        }

        @GetMapping("/{roomId}")
        public ResponseEntity<ApiResponse<RoomDetailResponse>> detail(
                        @PathVariable Long roomId,
                        @AuthenticationPrincipal Jwt jwt) {

                RoomDetailResponse room = service.getRoom(userIdOf(jwt), roomId);
                return ResponseEntity.ok(ApiResponse.success("Lấy thông tin phòng chat thành công!", room));
        }

        @PostMapping("/{roomId}/members")
        public ResponseEntity<ApiResponse<JoinRoomResponse>> joinRoom(
                        @PathVariable Long roomId,
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody LocationRequest location) {

                JoinRoomResponse room = service.joinRoom(userIdOf(jwt), roomId, location);
                return ResponseEntity.status(HttpStatus.OK)
                                .body(ApiResponse.success("Tham gia phòng chat thành công!", room));
        }

        @GetMapping("/{roomId}/members")
        public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> members(
                        @PathVariable Long roomId,
                        @AuthenticationPrincipal Jwt jwt) {

                List<RoomMemberResponse> members = service.getMembers(userIdOf(jwt), roomId);
                return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thành viên thành công!", members));
        }

        @GetMapping("/me")
        public ResponseEntity<ApiResponse<PageResponse<MyRoomResponse>>> listRooms(@AuthenticationPrincipal Jwt jwt,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                Pageable pageable = PageRequest.of(
                                Math.max(page, 0),
                                Math.clamp(size, 1, 100));

                Page<MyRoomResponse> rooms = roomMemberService.getMyActiveRooms(userIdOf(jwt), pageable);

                PageResponse<MyRoomResponse> response = PageResponse.of(rooms);

                return ResponseEntity.ok(
                                ApiResponse.success("Lấy danh sách phòng chat thành công!", response));
        }

        @DeleteMapping("/{roomId}/members/me")
        public ResponseEntity<ApiResponse<Void>> leaveRoom(
                        @PathVariable Long roomId,
                        @AuthenticationPrincipal Jwt jwt) {

                roomMemberService.leaveRoom(userIdOf(jwt), roomId);

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                "Rời khỏi phòng chat thành công!",
                                                null));
        }

        @PostMapping("/members/response-join")
        public ResponseEntity<ApiResponse<Void>> acceptJoinRequest(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody AcceptJoinRequest ac) {

                roomMemberService.acceptJoinRequest(userIdOf(jwt), ac.getRoomId(), ac.getUserId(), ac.getStatus());

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                "Chấp nhận yêu cầu tham gia phòng chat thành công!",
                                                null));
        }
}
