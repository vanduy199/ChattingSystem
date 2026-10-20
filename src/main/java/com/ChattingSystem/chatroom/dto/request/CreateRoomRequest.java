package com.ChattingSystem.chatroom.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("-90")  @DecimalMax("90")  Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Min(5) @Max(1440) Integer ttlMinutes,   // null = mặc định 60 phút
        @Min(100) @Max(3000) Integer radiusM     // null = mặc định 3000 m
) {}