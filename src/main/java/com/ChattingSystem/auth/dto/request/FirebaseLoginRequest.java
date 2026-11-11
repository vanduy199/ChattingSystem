package com.ChattingSystem.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FirebaseLoginRequest(
        @NotBlank(message = "Firebase ID Token không được để trống")
        String idToken,
        String guestToken
) {
    public FirebaseLoginRequest(String idToken) {
        this(idToken, null);
    }
}
