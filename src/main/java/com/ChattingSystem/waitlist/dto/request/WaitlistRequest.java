package com.ChattingSystem.waitlist.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @Size(max = 50, message = "Nguồn đăng ký tối đa 50 ký tự")
        String source
) {
}
