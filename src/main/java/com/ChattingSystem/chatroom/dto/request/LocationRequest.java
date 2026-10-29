package com.ChattingSystem.chatroom.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

// Vị trí hiện tại, chỉ dùng để kiểm tra bán kính, không lưu
public record LocationRequest(
        @NotNull @DecimalMin("-90")  @DecimalMax("90")  Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
) {}
