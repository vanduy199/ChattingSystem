package com.ChattingSystem.auth.dto.response;

public record LoginResult(String accessToken, String tokenType, long expiresIn) {
    public static LoginResult from(String accessToken, String tokenType, long expiresIn) {
        return new LoginResult(accessToken, tokenType, expiresIn);
    }
}