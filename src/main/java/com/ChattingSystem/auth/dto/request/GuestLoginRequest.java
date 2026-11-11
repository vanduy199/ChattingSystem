package com.ChattingSystem.auth.dto.request;

import jakarta.validation.constraints.Size;

public record GuestLoginRequest(
        @Size(max = 50, message = "Tên hiển thị tối đa 50 ký tự")
        String nickname
) {
}
