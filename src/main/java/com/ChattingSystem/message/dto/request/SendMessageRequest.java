package com.ChattingSystem.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull(message = "Thiếu mã tin nhắn phía client") UUID clientMessageId,
        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(max = 2000, message = "Tin nhắn tối đa 2000 ký tự") String content
) {}
