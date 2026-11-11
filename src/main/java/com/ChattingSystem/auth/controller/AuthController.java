package com.ChattingSystem.auth.controller;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.request.GuestLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.dto.response.MeResponse;
import com.ChattingSystem.auth.service.AuthService;
import com.ChattingSystem.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResult>> login(
            @Valid @RequestBody FirebaseLoginRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long currentUserId = null;
        if (jwt != null && jwt.hasClaim("userId")) {
            currentUserId = ((Number) jwt.getClaim("userId")).longValue();
        }
        LoginResult loginResult = authService.firebaseLogin(request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công!", loginResult));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<LoginResult>> guestLogin(@Valid @RequestBody(required = false) GuestLoginRequest request) {
        LoginResult loginResult = authService.guestLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập với tư cách khách thành công!", loginResult));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = ((Number) jwt.getClaim("userId")).longValue();
        MeResponse me = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", me));
    }
}
